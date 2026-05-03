package com.truelock.enigma.media

import android.content.Context
import android.media.MediaMetadataRetriever
import com.truelock.enigma.profiles.KeyProfile
import com.truelock.enigma.security.DecryptUsageStore
import com.truelock.enigma.storage.SecureProfileStore
import java.io.File

class MediaCapsuleService(
    context: Context,
    private val secureProfileStore: SecureProfileStore,
    private val codec: MediaCapsuleFileCodec = MediaCapsuleFileCodec(),
) {
    companion object {
        const val MAX_MEDIA_BYTES = 32L * 1024L * 1024L
    }

    private val capsuleStore = MediaCapsuleFileStore(File(context.cacheDir, "media_capsules").apply { mkdirs() })
    private val decryptedStore = File(context.cacheDir, "media_plain").apply { mkdirs() }
    private val recordingStore = File(context.cacheDir, "media_recordings").apply { mkdirs() }
    private val usageStore = DecryptUsageStore(context)

    fun createRecordingFile(type: MediaCapsuleType, extension: String): File =
        File(recordingStore, "veil_${type.name.lowercase()}_${System.currentTimeMillis()}.$extension")

    fun describeMediaFile(file: File): String {
        if (!file.exists()) {
            return "missing(${file.name})"
        }

        val prefix = "name=${file.name} size=${file.length()}"
        return runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            retriever.release()
            "$prefix mime=${mime ?: "unknown"} durationMs=${duration ?: "unknown"} bitrate=${bitrate ?: "unknown"}"
        }.getOrElse { error ->
            "$prefix metadataError=${error.javaClass.simpleName}:${error.message.orEmpty()}"
        }
    }

    fun encryptFile(
        sourceFile: File,
        type: MediaCapsuleType,
        mimeType: String,
        durationMs: Long,
        profile: KeyProfile,
        width: Int? = null,
        height: Int? = null,
    ): File {
        requireMediaSize(sourceFile)
        val rawKey = secureProfileStore.loadProfileKey(profile)
        val metadata = MediaCapsuleMetadata(
            mimeType = mimeType,
            durationMs = durationMs,
            width = width,
            height = height,
            originalFileName = sourceFile.name,
        )
        val encrypted = codec.encrypt(
            type = type,
            metadata = metadata,
            mediaBytes = sourceFile.readBytes(),
            profileKey = rawKey,
            profileHint = profile.profileHint,
        )
        secureProfileStore.touchProfile(profile.id)
        return capsuleStore.saveCapsule(encrypted)
    }

    fun decryptFile(capsuleFile: File, profiles: List<KeyProfile> = secureProfileStore.listProfiles()): DecryptedMediaCapsule {
        requireMediaSize(capsuleFile)
        val bytes = capsuleStore.readCapsule(capsuleFile)
        val hint = codec.extractProfileHint(bytes)
        val candidates = profiles.filter { it.profileHint.contentEquals(hint) }.ifEmpty { profiles }
        require(candidates.isNotEmpty()) { "No profiles available for media capsule" }

        val profile = candidates.firstOrNull() ?: error("No matching profile")
        val fingerprint = usageStore.mediaFingerprint(bytes)
        require(!profile.oneTimeRead || !usageStore.isConsumed(profile.id, fingerprint)) {
            "Capsule already opened"
        }

        val keys = candidates.map(secureProfileStore::loadProfileKey)
        val decoded = codec.decrypt(bytes, keys)
        val decodedProfile = candidates.firstOrNull { it.profileHint.contentEquals(decoded.profileHint) }
            ?: candidates.first()
        secureProfileStore.touchProfile(decodedProfile.id)
        if (decodedProfile.oneTimeRead) {
            usageStore.markConsumed(decodedProfile.id, fingerprint)
        }

        val extension = decoded.metadata.originalFileName
            ?.substringAfterLast('.', "")
            ?.takeIf { it.isNotBlank() }
            ?.takeUnless { it.equals(decoded.type.fileExtension, ignoreCase = true) }
            ?.takeUnless { candidate ->
                MediaCapsuleType.entries.any { type ->
                    type.legacyFileExtensions.any { it.equals(candidate, ignoreCase = true) }
                }
            }
            ?: defaultPlaintextExtension(decoded.type)

        val plaintextFile = File(
            decryptedStore,
            "veil_plain_${decoded.type.name.lowercase()}_${System.currentTimeMillis()}.$extension",
        )
        plaintextFile.writeBytes(decoded.mediaBytes)

        return DecryptedMediaCapsule(
            capsuleFile = capsuleFile,
            plaintextFile = plaintextFile,
            profile = decodedProfile,
            metadata = decoded.metadata,
            type = decoded.type,
        )
    }

    fun resolveProfileForCapsule(
        capsuleFile: File,
        profiles: List<KeyProfile> = secureProfileStore.listProfiles(),
    ): KeyProfile? {
        requireMediaSize(capsuleFile)
        val bytes = capsuleStore.readCapsule(capsuleFile)
        val hint = codec.extractProfileHint(bytes)
        return profiles.firstOrNull { it.profileHint.contentEquals(hint) } ?: profiles.firstOrNull()
    }

    fun requiresBiometricForCapsule(
        capsuleFile: File,
        profiles: List<KeyProfile> = secureProfileStore.listProfiles(),
    ): Boolean {
        requireMediaSize(capsuleFile)
        if (profiles.isEmpty()) return false
        val bytes = capsuleStore.readCapsule(capsuleFile)
        val hint = codec.extractProfileHint(bytes)
        val exactMatches = profiles.filter { it.profileHint.contentEquals(hint) }
        return if (exactMatches.isNotEmpty()) {
            exactMatches.any { it.requireBiometricForDecrypt }
        } else {
            profiles.any { it.requireBiometricForDecrypt }
        }
    }

    fun safeRequiresBiometricForCapsule(
        capsuleFile: File,
        profiles: List<KeyProfile> = secureProfileStore.listProfiles(),
    ): Boolean = runCatching {
        requiresBiometricForCapsule(capsuleFile, profiles)
    }.getOrDefault(false)

    private fun defaultPlaintextExtension(type: MediaCapsuleType): String =
        when (type) {
            MediaCapsuleType.AUDIO -> "m4a"
            MediaCapsuleType.VIDEO -> "mp4"
            MediaCapsuleType.PHOTO -> "jpg"
        }

    private fun requireMediaSize(file: File) {
        require(file.exists()) { "Media file missing" }
        require(file.length() in 1..MAX_MEDIA_BYTES) {
            "Media file exceeds supported size"
        }
    }
}

data class DecryptedMediaCapsule(
    val capsuleFile: File,
    val plaintextFile: File,
    val profile: KeyProfile,
    val metadata: MediaCapsuleMetadata,
    val type: MediaCapsuleType,
)
