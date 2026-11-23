package com.makrydensystems.news.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.makrydensystems.news.data.SettingsStore
import com.makrydensystems.news.data.di.AppContainer
import com.makrydensystems.news.data.di.PushModule
import com.makrydensystems.news.ui.components.SectionHeader
import com.makrydensystems.news.ui.theme.StarBrookColors
import com.makrydensystems.news.ui.theme.StarBrookShapes
import com.makrydensystems.news.ui.theme.StarBrookType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
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
    // Suspend (not fire-and-forget): the ZIP save handler awaits this
    // before re-registering, or the re-register reads the stale ZIP —
    // the same race the notify toggles had (see applyNotifyToggle).
    suspend fun setZip(z: String?) { store.setZip(z) }
    // Suspend (not fire-and-forget): the caller awaits the DataStore
    // write before anything reads the toggle back. Fire-and-forget
    // here caused a live bug (2026-11-23): turning Weather back on
    // re-registered with the stale kinds and the server row lost
    // "weather" — see applyNotifyToggle in SettingsScreen.
    suspend fun setNotifyBreaking(on: Boolean) { store.setNotifyBreaking(on) }
    suspend fun setNotifyMorning(on: Boolean) { store.setNotifyMorning(on) }
    suspend fun setNotifyEvening(on: Boolean) { store.setNotifyEvening(on) }
    suspend fun setNotifyWeather(on: Boolean) { store.setNotifyWeather(on) }

    init {
        // Mirror every notification toggle into PushModule's in-memory
        // gates, so the repository polling loops follow the toggles the
        // moment they change. DataStore stays the source of truth — this
        // only feeds the loops until AppContainer passes SettingsStore
        // straight into the repositories (one-line hookup; see PushModule).
        viewModelScope.launch {
            combine(
                store.notifyBreakingFlow,
                store.notifyMorningFlow,
                store.notifyEveningFlow,
                store.notifyWeatherFlow,
            ) { breaking, morning, evening, weather ->
                PushModule.updateAll(breaking, morning, evening, weather)
            }.collect { }
        }
    }
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
 * Notification toggle (Settings > Notifications only — the edition and
 * section toggles above keep using ToggleRow). A REAL control: the whole
 * row is a switch (Role.Switch) with a 48dp minimum touch target, so
 * tapping anywhere on the row flips it, and the description says exactly
 * what it does. The toggle does two things: it gates the in-app live
 * refresh while the app is open (PushModule), AND it re-registers this
 * device's FCM token with the server (FcmRegistrar), so the kind stops
 * arriving as a push when the toggle is off.
 */
@Composable
private fun NotificationToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onChange,
            )
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        ) {
            Text(text = label, style = StarBrookType.body, color = StarBrookColors.textPrimary)
            Text(
                text = description,
                style = StarBrookType.bodySmall,
                color = StarBrookColors.textSecondary,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
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
 * lives in the Portal Hub (the gold Portal star in the bottom bar) —
 * the account row points there instead of offering a dead button.
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

    // FCM push (Build #24): a toggle change re-registers this device
    // with the server (new kinds), and a ZIP change re-registers with
    // the new ZIP — the server row is upserted, so no re-subscribe is
    // needed. Android 13+ asks for notification permission the first
    // time a toggle is turned on; registration proceeds either way
    // (the OS blocks display without the permission, the token still
    // registers).
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* granted or denied — nothing to do here */ }
    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    fun refreshPushRegistration() {
        scope.launch { container.fcmRegistrar.ensureRegistered() }
    }
    // One notification toggle changed. The DataStore write MUST
    // complete before ensureRegistered() reads the toggle flows back,
    // or the server is upserted with stale kinds (proven live
    // 2026-11-23: turning Weather back on left the server row without
    // "weather"). So the whole sequence runs in one coroutine:
    // write -> permission prompt when turning on -> re-register.
    fun applyNotifyToggle(set: suspend (Boolean) -> Unit, on: Boolean) {
        scope.launch {
            set(on)
            if (on) requestNotificationPermissionIfNeeded()
            container.fcmRegistrar.ensureRegistered()
        }
    }

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
            text = "Makryden account — sign in from the gold Portal star below. Reading the news never needs an account.",
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
                        // Same sequencing as the notify toggles: the
                        // DataStore write must complete before the
                        // re-register reads the ZIP back.
                        scope.launch {
                            vm.setZip(zipInput)
                            refreshPushRegistration()
                        }
                    } else {
                        zipError = "That doesn't look like a ZIP code — 5 digits."
                    }
                },
        )
        Spacer(modifier = Modifier.height(12.dp))
        SectionHeader(title = "Notifications")
        Text(
            text = "Push notifications from the app itself — breaking news, editions, and weather alerts arrive even when the app is closed. Turning a toggle off stops that kind. (Android 13 and up asks for notification permission the first time you turn one on.)",
            style = StarBrookType.bodySmall,
            color = StarBrookColors.textSecondary,
        )
        NotificationToggleRow(
            label = "Breaking news",
            description = "Breaking news as it happens, even when the app is closed. Tapping it opens the story.",
            checked = notifyBreaking,
            onChange = { applyNotifyToggle(vm::setNotifyBreaking, it) },
        )
        NotificationToggleRow(
            label = "Morning edition",
            description = "The morning edition, when it is ready after its 7:00 AM build. Tapping it opens News.",
            checked = notifyMorning,
            onChange = { applyNotifyToggle(vm::setNotifyMorning, it) },
        )
        NotificationToggleRow(
            label = "Evening edition",
            description = "The evening edition, when it is ready after its 4:00 PM build. Tapping it opens News.",
            checked = notifyEvening,
            onChange = { applyNotifyToggle(vm::setNotifyEvening, it) },
        )
        NotificationToggleRow(
            label = "Weather alerts",
            description = "Severe weather warnings and rain starting soon, for your ZIP code. Tapping it opens Weather.",
            checked = notifyWeather,
            onChange = { applyNotifyToggle(vm::setNotifyWeather, it) },
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}
