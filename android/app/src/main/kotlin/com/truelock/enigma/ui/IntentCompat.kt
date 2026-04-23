package com.truelock.enigma.ui

import android.content.Intent
import android.net.Uri
import android.os.Build

internal fun Intent.uriExtraCompat(name: String): Uri? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(name, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(name) as? Uri
    }
}
