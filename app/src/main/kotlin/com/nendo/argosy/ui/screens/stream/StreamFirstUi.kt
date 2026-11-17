package com.nendo.argosy.ui.screens.stream

import androidx.compose.runtime.compositionLocalOf
import com.nendo.argosy.data.model.stream.StreamSettings
import com.nendo.argosy.data.stream.StreamKind
import com.nendo.argosy.data.stream.StreamRoute
import com.nendo.argosy.data.preferences.NotOnDeviceAction
import com.nendo.argosy.data.streaming.StreamFirstRule

/**
 * The account's choice for games not on this device and the platforms the connected server can
 * stream, lowercased.
 */
data class StreamFirstUi(
    val action: NotOnDeviceAction = NotOnDeviceAction.DOWNLOAD,
    val platforms: Set<String> = emptySet(),
    val settings: StreamSettings = StreamSettings()
) {
    fun leads(platformSlug: String?, isRommGame: Boolean, isOnDevice: Boolean,
              rommId: Long? = null, title: String = "", isSteamGame: Boolean = false,
              isAndroidApp: Boolean = false): Boolean {
        val hasContainer = settings.host.isNotBlank() && !isSteamGame && !isAndroidApp &&
            platformSlug?.lowercase()?.let { it in platforms } == true
        val moonlight = StreamRoute.resolve(settings, null, rommId, platformSlug.orEmpty(),
            isSteamGame, isAndroidApp, title)?.kind == StreamKind.MOONLIGHT
        return StreamFirstRule.leadsWithStream(action, isOnDevice, isRommGame && hasContainer, moonlight)
    }
}

val LocalStreamFirst = compositionLocalOf { StreamFirstUi() }
