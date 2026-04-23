package com.truelock.enigma.ui

import android.content.ContentResolver
import android.net.Uri
import java.io.File

internal fun ContentResolver.copyUriToFileWithLimit(
    uri: Uri,
    target: File,
    maxBytes: Long,
) {
    openInputStream(uri)?.use { input ->
        target.outputStream().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var totalBytes = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                totalBytes += read
                require(totalBytes <= maxBytes) { "Imported file exceeds limit" }
                output.write(buffer, 0, read)
            }
        }
    } ?: error("Input stream missing")
}
