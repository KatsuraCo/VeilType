package com.truelock.enigma.media

import com.truelock.enigma.crypto.hex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.SecureRandom

class MediaCapsuleFileStoreTest {
    private val codec = MediaCapsuleFileCodec(SecureRandom())
    private val key = hex("4f3dbdf40fdf38b4131990efc6d319ebb80f5c32faffe8df4e97a02cc607b453")
    private val hint = hex("1e69f31d92861b44")

    @Test
    fun saveAndReadCapsule_persistsBinaryContainer() {
        val tempDir = createTempDir(prefix = "media_capsule_store_test")
        try {
            val store = MediaCapsuleFileStore(tempDir)
            val capsule = codec.encrypt(
                type = MediaCapsuleType.VIDEO,
                metadata = MediaCapsuleMetadata(
                    mimeType = "video/mp4",
                    durationMs = 1_500,
                    width = 480,
                    height = 480,
                ),
                mediaBytes = ByteArray(16) { it.toByte() },
                profileKey = key,
                profileHint = hint,
                nonceOverride = hex("00112233445566778899aabb"),
            )

            val file = store.saveCapsule(capsule)
            val restoredBytes = store.readCapsule(file)
            val decoded = codec.decrypt(restoredBytes, listOf(key))

            assertTrue(file.exists())
            assertEquals(MediaCapsuleType.VIDEO, decoded.type)
            assertArrayEquals(capsule.containerBytes, restoredBytes)
            assertTrue(file.name.endsWith(".${MediaCapsuleType.VIDEO.fileExtension}"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
