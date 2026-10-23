package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.nendo.argosy.R
import com.nendo.argosy.data.model.stream.StreamSettings
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.components.TextEntryModal
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.SectionHeader
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens

internal sealed class StreamingItem(val key: String, val section: String) {
    val isFocusable: Boolean get() = this !is Header

    class Header(key: String, section: String, val titleRes: Int) : StreamingItem(key, section)
    data object Host : StreamingItem("streamHost", "connection")
    data object Client : StreamingItem("streamClient", "connection")
    data class Platform(val slug: String) : StreamingItem("streamPlatform_$slug", "platforms")

    companion object {
        val ALL: List<StreamingItem>
            get() = buildList {
                add(Header("connectionHeader", "connection", R.string.kaegr_settings_streaming_section_connection))
                add(Host)
                add(Client)
                add(Header("platformsHeader", "platforms", R.string.kaegr_settings_streaming_section_platforms))
                StreamSettings.PLATFORMS.forEach { add(Platform(it)) }
            }
    }
}

private val streamingLayout = SettingsLayout<StreamingItem, Unit>(
    allItems = StreamingItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { _, _ -> true },
    sectionOf = { it.section },
    sectionTitleRes = {
        when (it) {
            "connection" -> R.string.kaegr_settings_streaming_section_connection
            "platforms" -> R.string.kaegr_settings_streaming_section_platforms
            else -> null
        }
    }
)

internal fun streamingItemAtFocusIndex(index: Int): StreamingItem? = streamingLayout.itemAtFocusIndex(index, Unit)

internal fun streamingMaxFocusIndex(): Int = streamingLayout.maxFocusIndex(Unit)

internal fun streamingClientPackageAt(index: Int): String? = StreamSettings.CLIENTS.getOrNull(index)

@androidx.annotation.StringRes
internal fun streamingClientNameRes(packageName: String): Int = when (packageName) {
    "com.limelight.noir" -> R.string.kaegr_stream_client_artemis
    else -> R.string.kaegr_stream_client_moonlight
}

@Composable
fun StreamingSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val state = uiState.streaming
    val context = LocalContext.current
    val visibleItems = remember { streamingLayout.visibleItems(Unit) }
    val sections = remember(context) { streamingLayout.buildSections(Unit, context) }

    fun isFocused(item: StreamingItem): Boolean = uiState.focusedIndex == streamingLayout.focusIndexOf(item, Unit)

    fun focus(item: StreamingItem) = viewModel.setFocusIndex(streamingLayout.focusIndexOf(item, Unit))

    SectionPaneLayout(
        items = visibleItems,
        sections = sections,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { streamingLayout.focusToListIndex(it, Unit) },
        itemKey = { it.key },
        isNavItem = { false },
        isHeader = { it is StreamingItem.Header },
        onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) { item ->
        when (item) {
            is StreamingItem.Header -> {
                if (item.key != "connectionHeader") Spacer(modifier = Modifier.height(Dimens.spacingSm))
                SectionHeader(stringResource(item.titleRes))
            }
            StreamingItem.Host -> ActionPreference(
                title = stringResource(R.string.kaegr_settings_streaming_host_title),
                subtitle = stringResource(R.string.kaegr_settings_streaming_host_subtitle),
                trailingText = state.settings.host,
                isFocused = isFocused(item),
                onClick = { focus(item); viewModel.openStreamHostModal() }
            )
            StreamingItem.Client -> {
                val clientOptions = remember(context) {
                    StreamSettings.CLIENTS.map { context.getString(streamingClientNameRes(it)) }
                }
                CyclePreference(
                    title = stringResource(R.string.kaegr_settings_streaming_client_title),
                    value = stringResource(streamingClientNameRes(state.settings.moonlightPackage)),
                    isFocused = isFocused(item),
                    onClick = { focus(item); viewModel.requestEnumPicker(item.key) },
                    onPrev = { viewModel.cycleStreamClient(-1) },
                    options = clientOptions,
                    onSelect = { index -> streamingClientPackageAt(index)?.let(viewModel::setStreamClient) },
                    pickerRequestToken = if (uiState.enumPickerKey == item.key) uiState.enumPickerToken else 0
                )
            }
            is StreamingItem.Platform -> SwitchPreference(
                title = PlatformDefinitions.getBySlug(item.slug)?.name ?: item.slug,
                isEnabled = item.slug in state.settings.platforms,
                isFocused = isFocused(item),
                onToggle = { focus(item); viewModel.toggleStreamPlatform(item.slug) }
            )
        }
    }

    if (state.showHostModal) {
        TextEntryModal(
            title = stringResource(R.string.kaegr_settings_streaming_host_title),
            label = stringResource(R.string.kaegr_settings_streaming_host_label),
            confirmLabel = stringResource(R.string.kaegr_settings_streaming_host_save),
            cancelLabel = stringResource(R.string.kaegr_settings_streaming_host_cancel),
            text = state.hostDraft,
            onTextChange = { viewModel.updateStreamHostDraft(it) },
            onDismiss = { viewModel.dismissStreamHostModal() },
            onSubmit = { viewModel.submitStreamHost() },
            focus = state.hostFocus,
            canSubmit = state.hostDraft.isNotBlank(),
            keyboardType = KeyboardType.Uri
        )
    }
}
