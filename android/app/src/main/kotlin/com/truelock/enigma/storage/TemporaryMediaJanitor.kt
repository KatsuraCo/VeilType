package com.truelock.enigma.storage

import android.content.Context
import com.truelock.enigma.media.PendingCapsuleStore
import java.io.File

object TemporaryMediaJanitor {
    fun purgeTransientMedia(context: Context) {
        PendingCapsuleStore(context).clear()
        deleteRecursively(File(context.cacheDir, "media_capsules"))
        deleteRecursively(File(context.cacheDir, "media_plain"))
        deleteRecursively(File(context.cacheDir, "media_recordings"))
        deleteRecursively(File(context.cacheDir, "shared_capsules"))
    }

    private fun deleteRecursively(target: File) {
        runCatching {
            if (!target.exists()) return
            if (target.isDirectory) {
                target.listFiles()?.forEach(::deleteRecursively)
            }
            target.delete()
        }
    }
}
