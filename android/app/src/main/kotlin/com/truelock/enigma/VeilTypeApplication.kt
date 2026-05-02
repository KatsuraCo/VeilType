package com.truelock.enigma

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.truelock.enigma.storage.TemporaryMediaJanitor

class VeilTypeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TemporaryMediaJanitor.purgeTransientMedia(this)
        val appLocales = AppCompatDelegate.getApplicationLocales()
        if (appLocales.isEmpty) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
        }
    }
}
