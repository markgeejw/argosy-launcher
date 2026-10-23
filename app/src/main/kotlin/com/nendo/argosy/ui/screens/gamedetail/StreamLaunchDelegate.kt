package com.nendo.argosy.ui.screens.gamedetail

import com.nendo.argosy.data.model.stream.StreamSettings
import com.nendo.argosy.data.stream.MoonlightStream
import com.nendo.argosy.data.stream.toIntent

internal fun streamLaunchEvent(
    game: GameDetailUi,
    settings: StreamSettings
): LaunchEvent.StreamIntent? {
    val rommId = game.rommId ?: return null
    if (!MoonlightStream.canStream(settings, rommId, game.platformSlug, game.isSteamGame, game.isAndroidApp)) {
        return null
    }
    return LaunchEvent.StreamIntent(
        intent = MoonlightStream.request(settings.host, game.title, rommId).toIntent(settings.moonlightPackage),
        moonlightPackage = settings.moonlightPackage
    )
}
