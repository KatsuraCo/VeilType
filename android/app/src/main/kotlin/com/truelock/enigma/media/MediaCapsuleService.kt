package com.truelock.enigma.media

import android.content.Context
import com.truelock.enigma.profiles.KeyProfile
import com.truelock.enigma.storage.SecureProfileStore
import java.io.File

class MediaCapsuleService(
    context: Context,
    private val secureProfileStore: SecureProfileStore,
    private val codec: MediaCapsuleFileCodec = MediaCapsuleFileCodec(),
) {
    private val capsuleStore = MediaCapsuleFileStore(File(context.filesDir, "media_capsules"))
    private val decryptedStore = File(context.cacheDir, "media_plain").apply { mkdirs() }
    private val recordingStore = File(context.cacheDir, "media_recordings").apply { mkdirs() }

    fun createRecordingFile(type: MediaCapsuleType, extension: String): File =
        File(recordingStore, "${type.magic.lowercase()}_${System.currentTimeMillis()}.$extension")

    fun encryptFile(
        sourceFile: File,
        type: MediaCapsuleType,
        mimeType: String,
        durationMs: Long,
        profile: KeyProfile,
        width: Int? = null,
        height: Int? = null,
    ): File {
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
        val bytes = capsuleStore.readCapsule(capsuleFile)
        val hint = codec.extractProfileHint(bytes)
        val candidates = profiles.filter { it.profileHint.contentEquals(hint) }.ifEmpty { profiles }
        require(candidates.isNotEmpty()) { "No profiles available for media capsule" }

        val keys = candidates.map(secureProfileStore::loadProfileKey)
        val decoded = codec.decrypt(bytes, keys)
        val profile = candidates.firstOrNull { it.profileHint.contentEquals(decoded.profileHint) }
            ?: candidates.first()
        secureProfileStore.touchProfile(profile.id)

        val extension = decoded.metadata.originalFileName
            ?.substringAfterLast('.', "")
            ?.takeIf { it.isNotBlank() }
            ?: defaultPlaintextExtension(decoded.type)

        val plaintextFile = File(
            decryptedStore,
            "${decoded.type.magic.lowercase()}_${System.currentTimeMillis()}.$extension",
        )
        plaintextFile.writeBytes(decoded.mediaBytes)

        return DecryptedMediaCapsule(
            capsuleFile = capsuleFile,
            plaintextFile = plaintextFile,
            profile = profile,
            metadata = decoded.metadata,
            type = decoded.type,
        )
    }

    private fun defaultPlaintextExtension(type: MediaCapsuleType): String =
        when (type) {
            MediaCapsuleType.AUDIO -> "m4a"
            MediaCapsuleType.VIDEO -> "mp4"
        }
}

data class DecryptedMediaCapsule(
    val capsuleFile: File,
    val plaintextFile: File,
    val profile: KeyProfile,
    val metadata: MediaCapsuleMetadata,
    val type: MediaCapsuleType,
)
