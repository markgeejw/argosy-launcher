package com.nendo.argosy.data.sync.platform

internal data class N3dsTitleIds(
    val category: String,
    val lowId: String,
    val extdataId: String?,
    val legacyExtdataId: String?
) {
    companion object {
        const val BASE_CATEGORY = "00040000"
        private val nativeExtdataIds: Map<String, String?> = mapOf(
            "0004000000055e00" to "0000055d",
            "000400000011c400" to "000011c5",
            "00040000001b5100" to "00001b50",
            "000400000008d300" to null
        )

        fun fromSaveId(saveId: String): N3dsTitleIds {
            val flat = saveId.replace("/", "").trim().lowercase()
            val category = if (flat.length >= 16) flat.take(8) else BASE_CATEGORY
            val lowId = flat.takeLast(8)
            val derived = lowId.toLongOrNull(16)?.let { java.lang.Long.toHexString(it shr 8).padStart(8, '0') }
            val full = "$category$lowId"
            val native = if (flat.length in setOf(8, 16) && full in nativeExtdataIds) {
                nativeExtdataIds.getValue(full)
            } else derived
            return N3dsTitleIds(category, lowId, native, derived)
        }
    }
}
