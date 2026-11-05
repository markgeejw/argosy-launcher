package com.nendo.argosy.data.emulator

/**
 * Switch title ids as they appear in dump names, e.g. `Game [0100F2C0115B6800][v262144].nsp`.
 *
 * A base game's id ends in 000, its update is the base id ending in 800, and each dlc is the
 * next 0x1000 block plus its index.
 */
object SwitchTitleId {
    val CONTAINER_EXTENSIONS = setOf("nsp", "nsz", "xci", "xcz")

    private val BRACKETED_ID = Regex("\\[(01[0-9A-Fa-f]{14})]")
    private const val CONTENT_MASK = 0xFFFL
    private const val UPDATE_SUFFIX = 0x800L
    private const val DLC_BLOCK = 0x1000L

    fun of(fileName: String): Long? =
        BRACKETED_ID.find(fileName)?.groupValues?.get(1)?.toLong(16)

    fun isBase(id: Long): Boolean = id and CONTENT_MASK == 0L

    fun baseOf(id: Long): Long = when (id and CONTENT_MASK) {
        0L, UPDATE_SUFFIX -> id and CONTENT_MASK.inv()
        else -> (id - DLC_BLOCK) and CONTENT_MASK.inv()
    }

    fun namePrefix(fileName: String): String =
        fileName.substringBeforeLast('.').substringBefore('[').trim()
}
