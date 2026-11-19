package com.makrydensystems.news.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.makrydensystems.news.data.SettingsStore
import com.makrydensystems.news.data.di.AppContainer
import com.makrydensystems.news.ui.components.SectionHeader
import com.makrydensystems.news.ui.theme.StarBrookColors
import com.makrydensystems.news.ui.theme.StarBrookShapes
import com.makrydensystems.news.ui.theme.StarBrookType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private class SettingsViewModel(private val store: SettingsStore) : ViewModel() {
    val edition = store.editionFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "morning")
    val sections = store.sectionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    val zip = store.zipFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val notifyBreaking = store.notifyBreakingFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val notifyMorning = store.notifyMorningFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val notifyEvening = store.notifyEveningFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val notifyWeather = store.notifyWeatherFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setEdition(e: String) = viewModelScope.launch { store.setEdition(e) }
    fun setSection(s: String, on: Boolean) =
        viewModelScope.launch { store.setSectionEnabled(s, on) }
    fun setZip(z: String?) = viewModelScope.launch { store.setZip(z) }
    fun setNotifyBreaking(on: Boolean) =
        viewModelScope.launch { store.setNotifyBreaking(on) }
    fun setNotifyMorning(on: Boolean) =
        viewModelScope.launch { store.setNotifyMorning(on) }
    fun setNotifyEvening(on: Boolean) =
        viewModelScope.launch { store.setNotifyEvening(on) }
    fun setNotifyWeather(on: Boolean) =
        viewModelScope.launch { store.setNotifyWeather(on) }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = StarBrookType.body, color = StarBrookColors.textPrimary)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = StarBrookColors.onGold,
                checkedTrackColor = StarBrookColors.gold,
                uncheckedThumbColor = StarBrookColors.textSecondary,
                uncheckedTrackColor = StarBrookColors.surfaceVariant,
            ),
        )
    }
}

/**
 * Settings / Account (v1). Every toggle writes immediately. Sign-in
 * is a later step (auth is deferred) — the account row says so in
 * plain language instead of offering a dead button.
 */
@Composable
fun SettingsScreen(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val vm: SettingsViewModel = viewModel(
        factory = viewModelFactory { initializer { SettingsViewModel(container.settingsStore) } },
    )
    val edition by vm.edition.collectAsState()
    val sections by vm.sections.collectAsState()
    val zip by vm.zip.collectAsState()
    val notifyBreaking by vm.notifyBreaking.collectAsState()
    val notifyMorning by vm.notifyMorning.collectAsState()
    val notifyEvening by vm.notifyEvening.collectAsState()
    val notifyWeather by vm.notifyWeather.collectAsState()
    var zipInput by remember(zip) { mutableStateOf(zip.orEmpty()) }
    var zipError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(modifier = Modifier.height(4.dp))
        SectionHeader(title = "Account")
        Text(
            text = "Makryden account — sign-in arrives in a later update. Reading the news never needs an account.",
            style = StarBrookType.bodySmall,
            color = StarBrookColors.textSecondary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        SectionHeader(title = "Editions")
        ToggleRow("Morning edition", edition == "morning", onChange = { if (it) vm.setEdition("morning") })
        ToggleRow("Evening edition", edition == "evening", onChange = { if (it) vm.setEdition("evening") })
        Spacer(modifier = Modifier.height(12.dp))
        SectionHeader(title = "Sections")
        for ((key, label) in listOf(
            "national" to "National",
            "local" to "Local",
            "tech" to "Tech",
            "business" to "Business",
            "culture" to "Culture",
        )) {
            ToggleRow(label, key in sections, onChange = { vm.setSection(key, it) })
        }
        Spacer(modifier = Modifier.height(12.dp))
        SectionHeader(title = "Your ZIP code")
        Text(
            text = "One ZIP for local news and weather. No ZIP, no local content — never a default city.",
            style = StarBrookType.bodySmall,
            color = StarBrookColors.textSecondary,
        )
        OutlinedTextField(
            value = zipInput,
            onValueChange = { zipInput = it.filter { c -> c.isDigit() }.take(5) },
            placeholder = { Text("Enter your ZIP code", color = StarBrookColors.textSecondary) },
            singleLine = true,
            isError = zipError != null,
            supportingText = zipError?.let { { Text(it, color = StarBrookColors.liveRed) } },
            shape = StarBrookShapes.chip,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = StarBrookColors.gold,
                unfocusedBorderColor = StarBrookColors.border,
                focusedTextColor = StarBrookColors.textPrimary,
                unfocusedTextColor = StarBrookColors.textPrimary,
                cursorColor = StarBrookColors.gold,
                errorBorderColor = StarBrookColors.liveRed,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Save ZIP",
            style = StarBrookType.label,
            color = if (zipInput.length == 5) StarBrookColors.gold else StarBrookColors.textSecondary,
            modifier = Modifier
                .padding(vertical = 4.dp)
                .clickable {
                    if (zipInput.length == 5) {
                        zipError = null
                        vm.setZip(zipInput)
                    } else {
                        zipError = "That doesn't look like a ZIP code — 5 digits."
                    }
                },
        )
        Spacer(modifier = Modifier.height(12.dp))
        SectionHeader(title = "Notifications")
        ToggleRow("Breaking news", notifyBreaking, onChange = { vm.setNotifyBreaking(it) })
        ToggleRow("Morning edition", notifyMorning, onChange = { vm.setNotifyMorning(it) })
        ToggleRow("Evening edition", notifyEvening, onChange = { vm.setNotifyEvening(it) })
        ToggleRow("Weather alerts", notifyWeather, onChange = { vm.setNotifyWeather(it) })
        Spacer(modifier = Modifier.height(24.dp))
    }
}
