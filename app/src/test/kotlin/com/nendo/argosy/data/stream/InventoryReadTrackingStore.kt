package com.nendo.argosy.data.stream

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.onEach

internal class InventoryReadTrackingStore(private val delegate: DataStore<Preferences>) : DataStore<Preferences> {
    val reads = Channel<Unit>(Channel.UNLIMITED)
    override val data = delegate.data.onEach { reads.send(Unit) }
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        delegate.updateData(transform)
}
