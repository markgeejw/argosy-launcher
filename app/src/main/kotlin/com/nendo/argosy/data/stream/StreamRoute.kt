package com.nendo.argosy.data.stream

import com.nendo.argosy.data.model.stream.StreamSettings
import com.nendo.argosy.data.model.stream.InventoryEntry
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
            title: String,
            inventory: Set<InventoryEntry>? = null
        ): StreamRoute? {
            if (isAndroidApp || settings.host.isBlank()) return null
            if (isSteamGame) {
                if (title.isNotBlank() && (inventory == null || InventoryEntry(title, MoonlightStream.appId(title).toString()) in inventory)) {
                    return StreamRoute(StreamKind.MOONLIGHT, MoonlightStream.appId(title).toString())
                }
                return null
            }
            if (rommId != null && MoonlightStream.platformEnabled(
                    settings.platforms, PlatformDefinitions.getCanonicalSlug(platformSlug)
                )
            ) {
                val name = MoonlightStream.appName(title, rommId)
                val appId = MoonlightStream.appId(name).toString()
                if (inventory == null || InventoryEntry(name, appId) in inventory) {
                    return StreamRoute(StreamKind.MOONLIGHT, appId)
                }
            }
            return container?.let { StreamRoute(StreamKind.CONTAINER) }
        }
    }
}
