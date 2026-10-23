package com.nendo.argosy.data.model.stream

data class StreamSettings(
    val host: String = DEFAULT_HOST,
    val moonlightPackage: String = DEFAULT_MOONLIGHT_PACKAGE,
    val platforms: Set<String> = DEFAULT_PLATFORMS
) {
    companion object {
        const val DEFAULT_HOST = "arceus"
        const val DEFAULT_MOONLIGHT_PACKAGE = "com.limelight"
        val DEFAULT_PLATFORMS = setOf("switch")

        val PLATFORMS = listOf("switch", "3ds", "nds", "snes", "psx", "ps2")

        val CLIENTS = listOf("com.limelight", "com.limelight.noir")
    }
}
