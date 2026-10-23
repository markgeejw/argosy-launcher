package com.nendo.argosy.ui.screens.settings.delegates

import com.nendo.argosy.data.model.stream.StreamSettings
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.ui.components.TEXT_ENTRY_CANCEL_BUTTON
import com.nendo.argosy.ui.components.TEXT_ENTRY_CONFIRM_BUTTON
import com.nendo.argosy.ui.components.TextEntryFocus
import com.nendo.argosy.ui.components.TextEntryRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StreamingState(
    val settings: StreamSettings = StreamSettings(),
    val showHostModal: Boolean = false,
    val hostDraft: String = "",
    val hostFocus: TextEntryFocus = TextEntryFocus()
)

class StreamingSettingsDelegate @Inject constructor(
    private val repository: UserPreferencesRepository
) {
    private val _state = MutableStateFlow(StreamingState())
    val state: StateFlow<StreamingState> = _state.asStateFlow()

    fun observe(scope: CoroutineScope) {
        repository.userPreferences.onEach { prefs ->
            _state.update { it.copy(settings = prefs.streamSettings) }
        }.launchIn(scope)
    }

    fun openHostModal() {
        _state.update {
            it.copy(showHostModal = true, hostDraft = it.settings.host, hostFocus = TextEntryFocus())
        }
    }

    fun dismissHostModal() {
        _state.update { it.copy(showHostModal = false, hostDraft = "", hostFocus = TextEntryFocus()) }
    }

    fun updateHostDraft(text: String) {
        _state.update { it.copy(hostDraft = text) }
    }

    fun moveHostRow(row: TextEntryRow) {
        _state.update { it.copy(hostFocus = it.hostFocus.copy(row = row)) }
    }

    fun moveHostButton(delta: Int) {
        _state.update { state ->
            if (state.hostFocus.row != TextEntryRow.BUTTONS) return@update state
            val next = (state.hostFocus.buttonIndex + delta)
                .coerceIn(TEXT_ENTRY_CANCEL_BUTTON, TEXT_ENTRY_CONFIRM_BUTTON)
            state.copy(hostFocus = state.hostFocus.copy(buttonIndex = next))
        }
    }

    fun confirmHost(scope: CoroutineScope) {
        val focus = _state.value.hostFocus
        when {
            focus.row == TextEntryRow.FIELD -> moveHostRow(TextEntryRow.BUTTONS)
            focus.buttonIndex == TEXT_ENTRY_CANCEL_BUTTON -> dismissHostModal()
            else -> submitHost(scope)
        }
    }

    fun submitHost(scope: CoroutineScope) {
        val host = _state.value.hostDraft.trim()
        if (host.isEmpty()) return
        scope.launch { repository.setStreamHost(host) }
        dismissHostModal()
    }

    fun cycleClient(scope: CoroutineScope, delta: Int) {
        val clients = StreamSettings.CLIENTS
        val current = clients.indexOf(_state.value.settings.moonlightPackage).coerceAtLeast(0)
        val next = clients[(current + delta).mod(clients.size)]
        _state.update { it.copy(settings = it.settings.copy(moonlightPackage = next)) }
        scope.launch { repository.setStreamMoonlightPackage(next) }
    }

    fun togglePlatform(scope: CoroutineScope, slug: String) {
        val enable = slug !in _state.value.settings.platforms
        _state.update { state ->
            val platforms = state.settings.platforms
            state.copy(settings = state.settings.copy(platforms = if (enable) platforms + slug else platforms - slug))
        }
        scope.launch { repository.setStreamPlatformEnabled(slug, enable) }
    }
}
