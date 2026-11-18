package com.nendo.argosy.data.model.stream

data class InventoryEntry(val name: String, val appId: String)

data class InventorySnapshot(val entries: Set<InventoryEntry>, val fetchedAt: Long)
