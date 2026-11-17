package com.nendo.argosy.data.stream

import com.nendo.argosy.data.model.stream.StreamSettings
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.data.remote.romm.RomMStreamingContainer

enum class StreamKind { MOONLIGHT, CONTAINER }

data class StreamRoute(val kind: StreamKind, val appId: String? = null) {
    companion object {
        fun resolve(
            settings: StreamSettings,
            container: RomMStreamingContainer?,
            rommId: Long?,
            platformSlug: String,
            isSteamGame: Boolean,
            isAndroidApp: Boolean,
            title: String
        ): StreamRoute? {
            if (isAndroidApp || settings.host.isBlank()) return null
            if (isSteamGame) {
                if (title.isNotBlank()) {
                    return StreamRoute(StreamKind.MOONLIGHT, MoonlightStream.appId(title).toString())
                }
                return null
            }
            if (rommId != null && MoonlightStream.platformEnabled(
                    settings.platforms, PlatformDefinitions.getCanonicalSlug(platformSlug)
                )
            ) {
                return StreamRoute(
                    StreamKind.MOONLIGHT,
                    MoonlightStream.appId(MoonlightStream.appName(title, rommId)).toString()
                )
            }
            return container?.let { StreamRoute(StreamKind.CONTAINER) }
        }
    }
}
