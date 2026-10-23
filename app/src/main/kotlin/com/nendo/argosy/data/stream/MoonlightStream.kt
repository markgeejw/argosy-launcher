package com.nendo.argosy.data.stream

import android.content.Intent
import com.nendo.argosy.data.model.stream.StreamRequest
import com.nendo.argosy.data.model.stream.StreamSettings
import com.nendo.argosy.data.platform.PlatformDefinitions
import java.util.zip.CRC32
import kotlin.math.abs

object MoonlightStream {
    const val TRAMPOLINE_CLASS = "com.limelight.ShortcutTrampoline"

    fun appName(title: String, rommId: Long): String = "$title [$rommId]"

    fun appId(appName: String): Int {
        val crc = CRC32().apply { update(appName.toByteArray(Charsets.UTF_8)) }
        return abs(crc.value.toInt())
    }

    fun request(host: String, title: String, rommId: Long): StreamRequest =
        StreamRequest(host = host, appId = appId(appName(title, rommId)).toString(), appName = title)

    fun canStream(
        settings: StreamSettings,
        rommId: Long?,
        platformSlug: String,
        isSteamGame: Boolean,
        isAndroidApp: Boolean
    ): Boolean = rommId != null && !isSteamGame && !isAndroidApp && settings.host.isNotBlank() &&
        PlatformDefinitions.getCanonicalSlug(platformSlug) in settings.platforms
}

fun StreamRequest.toIntent(moonlightPackage: String): Intent = Intent().apply {
    setClassName(moonlightPackage, MoonlightStream.TRAMPOLINE_CLASS)
    putExtra("Name", host)
    putExtra("AppId", appId)
    putExtra("AppName", appName)
    putExtra("HDR", hdr)
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
