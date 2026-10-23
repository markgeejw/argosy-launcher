package com.nendo.argosy.ui.screens.gamedetail

import android.content.ActivityNotFoundException

internal inline fun tryStartStream(start: () -> Unit): Boolean = try {
    start()
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}
