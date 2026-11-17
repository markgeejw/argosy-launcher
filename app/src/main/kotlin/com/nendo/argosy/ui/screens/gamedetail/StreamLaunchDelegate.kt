package com.nendo.argosy.ui.screens.gamedetail

import com.nendo.argosy.data.model.stream.StreamSettings
import com.nendo.argosy.data.stream.MoonlightStream
import com.nendo.argosy.data.stream.StreamKind
import com.nendo.argosy.data.stream.StreamRoute
import com.nendo.argosy.data.stream.toIntent

internal fun streamLaunchEvent(
    game: GameDetailUi,
    settings: StreamSettings
): LaunchEvent.StreamIntent? {
    val route = StreamRoute.resolve(
        settings,
        container = null,
        rommId = game.rommId,
        platformSlug = game.platformSlug,
        isSteamGame = game.isSteamGame,
        isAndroidApp = game.isAndroidApp,
        title = game.title
    ) ?: return null
    if (route.kind != StreamKind.MOONLIGHT) return null
    val request = if (game.isSteamGame) {
        MoonlightStream.request(settings.host, game.title)
    } else {
        MoonlightStream.request(settings.host, game.title, game.rommId ?: return null)
    }
    return LaunchEvent.StreamIntent(
        intent = request.toIntent(settings.moonlightPackage),
        moonlightPackage = settings.moonlightPackage
    )
}
