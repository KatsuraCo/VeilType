package com.truelock.enigma.media

import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class MediaCapsuleFileStore(
    private val baseDir: File,
) {
    init {
        baseDir.mkdirs()
    }

    fun saveCapsule(
        capsule: MediaCapsuleEncryptedFile,
        fileName: String = defaultFileName(capsule.type, capsule),
    ): File {
        val file = File(baseDir, fileName)
        file.parentFile?.mkdirs()
        file.writeBytes(capsule.containerBytes)
        return file
    }

    fun readCapsule(file: File): ByteArray = file.readBytes()

    fun deleteCapsule(file: File): Boolean = file.delete()

    private fun defaultFileName(
        type: MediaCapsuleType,
        capsule: MediaCapsuleEncryptedFile,
    ): String {
        val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            .withZone(ZoneOffset.UTC)
            .format(Instant.now())
        val hint = capsule.profileHint.joinToString("") { "%02x".format(it) }.take(8)
        return "${type.magic.lowercase()}_${timestamp}_${hint}.${type.fileExtension}"
    }
}
