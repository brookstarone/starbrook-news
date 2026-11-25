package com.makrydensystems.news.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.makrydensystems.news.data.AuthResult
import com.makrydensystems.news.data.MasterRoomStatus
import com.makrydensystems.news.data.NewsroomPrefs
import com.makrydensystems.news.data.SettingsStore
import com.makrydensystems.news.data.StarBrookApi
import com.makrydensystems.news.data.di.AppContainer
import com.makrydensystems.news.ui.theme.StarBrookColors
import com.makrydensystems.news.ui.theme.StarBrookShapes
import com.makrydensystems.news.ui.theme.StarBrookType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Login
// ---------------------------------------------------------------------------

/**
 * Portal sign-in, mirroring https://makrydensystems.com/account/:
 * email + password (Supabase Auth password flow), "keep me signed in"
 * (the session persists like the portal's mk-portal-session), and
 * friendly errors — never a raw Supabase message. The caller owns
 * the auth call and passes back [error] in plain language, e.g.
 * "That email and password didn't match — check them and try again."
 * or "We couldn't reach the sign-in service. Check your connection
 * and try again." The form never shows a password, never logs one,
 * and never submits while [isLoading].
 */
@Composable
fun LoginForm(
    onSignIn: (email: String, password: String, keepSignedIn: Boolean) -> Unit,
    isLoading: Boolean = false,
    error: String? = null,
    onForgotPassword: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var keepSignedIn by remember { mutableStateOf(true) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    val fieldColors = TextFieldDefaults.colors(
        focusedContainerColor = StarBrookColors.surface,
        unfocusedContainerColor = StarBrookColors.surface,
        disabledContainerColor = StarBrookColors.surface,
        errorContainerColor = StarBrookColors.surface,
        focusedIndicatorColor = StarBrookColors.gold,
        unfocusedIndicatorColor = StarBrookColors.border,
        errorIndicatorColor = StarBrookColors.liveRed,
        focusedTextColor = StarBrookColors.textPrimary,
        unfocusedTextColor = StarBrookColors.textPrimary,
        errorTextColor = StarBrookColors.textPrimary,
        focusedLabelColor = StarBrookColors.textSecondary,
        unfocusedLabelColor = StarBrookColors.textSecondary,
        errorLabelColor = StarBrookColors.liveRed,
        cursorColor = StarBrookColors.gold,
        focusedPlaceholderColor = StarBrookColors.textSecondary,
        unfocusedPlaceholderColor = StarBrookColors.textSecondary,
    )

    fun submit() {
        emailError = when {
            email.isBlank() -> "Enter your email address."
            !email.contains("@") || !email.substringAfter("@").contains(".") ->
                "That doesn't look like an email address."
            else -> null
        }
        passwordError = if (password.isBlank()) "Enter your password." else null
        if (emailError == null && passwordError == null) {
            onSignIn(email.trim(), password, keepSignedIn)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LiveStarMark(size = 48.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(
            text = "Sign in to Makryden",
            style = StarBrookType.headline,
            color = StarBrookColors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Your Makryden account unlocks Newsroom prefs, your synced Shelf, and the Master Room.",
            style = StarBrookType.bodySmall,
            color = StarBrookColors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        if (error != null) {
            Surface(
                shape = StarBrookShapes.card,
                color = StarBrookColors.liveRed.copy(alpha = 0.14f),
            ) {
                Text(
                    text = error,
                    style = StarBrookType.bodySmall,
                    color = StarBrookColors.textPrimary,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; emailError = null },
            label = { Text("Email address") },
            placeholder = { Text("you@example.com") },
            singleLine = true,
            enabled = !isLoading,
            isError = emailError != null,
            supportingText = emailError?.let { { Text(it, style = StarBrookType.caption) } },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            colors = fieldColors,
            shape = StarBrookShapes.card,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; passwordError = null },
            label = { Text("Password") },
            singleLine = true,
            enabled = !isLoading,
            isError = passwordError != null,
            supportingText = passwordError?.let { { Text(it, style = StarBrookType.caption) } },
            visualTransformation = if (passwordVisible) VisualTransformation.None
            else PasswordVisualTransformation(),
            trailingIcon = {
                Text(
                    text = if (passwordVisible) "Hide" else "Show",
                    style = StarBrookType.label,
                    color = StarBrookColors.gold,
                    modifier = Modifier
                        .clickable { passwordVisible = !passwordVisible }
                        .padding(8.dp),
                )
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            colors = fieldColors,
            shape = StarBrookShapes.card,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Checkbox(
                checked = keepSignedIn,
                onCheckedChange = { keepSignedIn = it },
                enabled = !isLoading,
                colors = CheckboxDefaults.colors(
                    checkedColor = StarBrookColors.gold,
                    uncheckedColor = StarBrookColors.border,
                    checkmarkColor = StarBrookColors.onGold,
                ),
            )
            Text(
                text = "Keep me signed in",
                style = StarBrookType.bodySmall,
                color = StarBrookColors.textPrimary,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clickable { keepSignedIn = !keepSignedIn },
            )
            if (onForgotPassword != null) {
                Text(
                    text = "Forgot password?",
                    style = StarBrookType.label,
                    color = StarBrookColors.gold,
                    modifier = Modifier
                        .clickable(onClick = onForgotPassword)
                        .padding(8.dp),
                )
            }
        }

        Button(
            onClick = ::submit,
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = StarBrookColors.gold,
                contentColor = StarBrookColors.onGold,
                disabledContainerColor = StarBrookColors.surfaceVariant,
                disabledContentColor = StarBrookColors.textSecondary,
            ),
            shape = StarBrookShapes.chip,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (isLoading) "Signing in…" else "Sign in",
                style = StarBrookType.label,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
        Text(
            text = "Free to read — no paywall, no ads. Signing in is optional and only adds your prefs, Shelf sync, and the Master Room.",
            style = StarBrookType.caption,
            color = StarBrookColors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---------------------------------------------------------------------------
// Portal Hub screen (Concept B) + its ViewModel.
//
// Route: "portal?reason={reason}" — reason "save" is the save-prompt
// contract: other agents navigate here on a signed-out save tap and the
// Hub shows "Sign in to keep this on your Shelf". Any other reason (or
// none) shows the plain Hub.
//
// Exits (walked in code — every path back to News):
//   1. "← Back to News" gold-outline button at the top of the Hub.
//   2. "Back to News" gold button at the bottom of the Hub.
//   3. Bottom nav (the Hub is a top-level route — News/Weather/Saved/
//      Settings are always reachable; the star re-opens the Hub).
//   4. System back — the Hub sits on the nav back stack above News,
//      so back pops straight to News.
//   5. "Open my Shelf" → Saved tab (top-level, bottom nav present).
//   6. "Continue without signing in" → News.
//   7. Sign-out stays on the Hub, now showing the sign-in form — the
//      Back buttons above are still there. No dead ends, no orphans.
// ---------------------------------------------------------------------------

private class PortalViewModel(private val container: AppContainer) : ViewModel() {
    private val auth = container.portalAuth
    private val repo = container.portalRepository
    private val store = container.settingsStore

    private val _email = MutableStateFlow<String?>(null)
    val email: StateFlow<String?> = _email.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val _prefs = MutableStateFlow(NewsroomPrefs())
    val prefs: StateFlow<NewsroomPrefs> = _prefs.asStateFlow()

    private val _prefsSavedMessage = MutableStateFlow<String?>(null)
    val prefsSavedMessage: StateFlow<String?> = _prefsSavedMessage.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private val _masterCards = MutableStateFlow<List<MasterRoomStatus>>(emptyList())
    val masterCards: StateFlow<List<MasterRoomStatus>> = _masterCards.asStateFlow()

    private val _chat = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chat: StateFlow<List<ChatMessage>> = _chat.asStateFlow()

    private val _chatSending = MutableStateFlow(false)
    val chatSending: StateFlow<Boolean> = _chatSending.asStateFlow()

    val isConfigured: Boolean get() = auth.isConfigured

    val savedCount: StateFlow<Int> = container.newsRepository.savedFlow()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val zip: StateFlow<String?> = store.effectiveZipFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val sections: StateFlow<Set<String>> = store.sectionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        viewModelScope.launch {
            _prefs.value = repo.localNewsroomPrefs()
            val restored = auth.restoreSession()
            if (restored.isNotBlank()) {
                onSignedIn(restored)
            }
        }
    }

    private suspend fun onSignedIn(email: String) {
        _email.value = email
        _error.value = null
        _notice.value = null
        // Pull the server prefs and merge them with the local set, then
        // converge the Shelf both ways. The MERGE (not the raw server
        // row) wins: a stale server row that predates R-02 must not
        // switch the six new sections off — see
        // PortalRepository.syncNewsroomPrefsOnSignIn. Other fields
        // (subscribed/morning/evening/breaking) stay server-wins.
        // Null (no server row yet) leaves the local prefs in place.
        runCatching { repo.syncNewsroomPrefsOnSignIn() }.getOrNull()?.let { merged ->
            _prefs.value = merged
        }
        val synced = runCatching { repo.syncShelf() }.getOrDefault(false)
        _syncMessage.value = if (synced) {
            "Shelf synced across your devices."
        } else {
            "Couldn't reach the Shelf sync — your saves are safe on this device and will sync later."
        }
        if (StarBrookApi.isOperator(email)) {
            _masterCards.value = runCatching { repo.masterRoomStatus() }.getOrDefault(emptyList())
        }
    }

    fun signIn(email: String, password: String, keepSignedIn: Boolean) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _notice.value = null
            try {
                when (val result = auth.signIn(email, password, keepSignedIn)) {
                    is AuthResult.SignedIn -> onSignedIn(result.email)
                    is AuthResult.NeedsConfirmation -> {
                        _notice.value = "Please confirm your email first — the link is in your inbox (check spam too)."
                    }
                    AuthResult.RecoverySent -> Unit // Never returned by signIn.
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Something went wrong on our side. Try again in a minute."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun signUp(email: String, password: String, keepSignedIn: Boolean) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _notice.value = null
            try {
                when (val result = auth.signUp(email, password, keepSignedIn)) {
                    is AuthResult.SignedIn -> onSignedIn(result.email)
                    is AuthResult.NeedsConfirmation -> {
                        _notice.value = "Account created — check your inbox for the confirmation link, then sign in here. (Check spam too.)"
                    }
                    AuthResult.RecoverySent -> Unit // Never returned by signUp.
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Something went wrong on our side. Try again in a minute."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun forgotPassword(email: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                auth.sendRecoveryEmail(email)
                _notice.value = "Reset link sent — check your inbox for the password reset email (check spam too)."
            } catch (e: Exception) {
                _error.value = e.message ?: "Something went wrong on our side. Try again in a minute."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            auth.signOut()
            _email.value = null
            _masterCards.value = emptyList()
            _chat.value = emptyList()
            _syncMessage.value = null
            _notice.value = "Signed out. Your saves stay on this device."
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _syncMessage.value = "Syncing your Shelf…"
            val synced = runCatching { repo.syncShelf() }.getOrDefault(false)
            _syncMessage.value = if (synced) {
                "Shelf synced across your devices."
            } else {
                "Couldn't reach the Shelf sync — your saves are safe on this device and will sync later."
            }
        }
    }

    // --- Newsroom prefs (local first; Save commits to the server) ---

    fun setSubscribed(on: Boolean) {
        _prefs.value = _prefs.value.copy(subscribed = on)
        viewModelScope.launch { store.setNewsSubscribed(on) }
    }

    fun setPrefMorning(on: Boolean) {
        _prefs.value = _prefs.value.copy(morning = on)
        viewModelScope.launch { store.setPrefMorning(on) }
    }

    fun setPrefEvening(on: Boolean) {
        _prefs.value = _prefs.value.copy(evening = on)
        viewModelScope.launch { store.setPrefEvening(on) }
    }

    fun setPrefBreaking(on: Boolean) {
        _prefs.value = _prefs.value.copy(breaking = on)
        viewModelScope.launch { store.setPrefBreaking(on) }
    }

    fun setSection(section: String, on: Boolean) {
        viewModelScope.launch {
            store.setSectionEnabled(section, on)
            _prefs.value = repo.localNewsroomPrefs()
        }
    }

    fun savePrefs() {
        viewModelScope.launch {
            _prefsSavedMessage.value = "Saving…"
            // Preserve any section ids the server row carries that the
            // app does not know (future web-only ids): merge the server
            // row's sections with the local set.
            val server = runCatching { repo.loadNewsroomPrefs() }.getOrNull()
            val local = _prefs.value
            val mergedSections = when {
                server?.sections == null && local.sections == null -> null
                else -> ((server?.sections ?: emptyList()) +
                    (local.sections ?: SettingsStore.ALL_SECTIONS.toList()))
                    .distinct()
                    .filter { it in SettingsStore.ALL_SECTIONS ||
                        it in (server?.sections ?: emptyList()) }
                    .ifEmpty { null }
            }
            val toSave = local.copy(sections = mergedSections)
            val ok = runCatching { repo.saveNewsroomPrefs(toSave) }.isSuccess
            _prefs.value = toSave
            _prefsSavedMessage.value = if (ok) {
                "Saved — your news settings are updated."
            } else {
                "Saved on this device — couldn't reach the server, so it will sync next time you open the Portal."
            }
        }
    }

    fun setZip(zip: String?) {
        viewModelScope.launch { store.setZip(zip) }
    }

    // --- Master Room chat (operators only; data path only) ---

    fun sendChat(text: String) {
        viewModelScope.launch {
            _chat.value = _chat.value + ChatMessage(
                text = text,
                isFromUser = true,
                timestamp = System.currentTimeMillis(),
            )
            _chatSending.value = true
            val reply = runCatching { repo.sendCadeMessage(text) }.getOrNull()
            _chatSending.value = false
            _chat.value = _chat.value + ChatMessage(
                text = reply ?: "Couldn't reach CADE right now. Check your connection and try again.",
                isFromUser = false,
                timestamp = System.currentTimeMillis(),
                senderName = "CADE",
            )
        }
    }
}

/**
 * The Portal Hub — Concept B. Signed-out visitors get the sign-in /
 * create-account form (with the save context when opened with
 * reason="save"); signed-in users get account home, Shelf entry, news
 * settings, the Master Room (operators only), and "How Saved works".
 * Every path out is listed above the ViewModel.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PortalHubScreen(
    container: AppContainer,
    reason: String? = null,
    onBack: () -> Unit,
    onOpenSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: PortalViewModel = viewModel(
        factory = viewModelFactory { initializer { PortalViewModel(container) } },
    )
    val email by vm.email.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val error by vm.error.collectAsState()
    val notice by vm.notice.collectAsState()
    val prefs by vm.prefs.collectAsState()
    val prefsSavedMessage by vm.prefsSavedMessage.collectAsState()
    val syncMessage by vm.syncMessage.collectAsState()
    val masterCards by vm.masterCards.collectAsState()
    val chat by vm.chat.collectAsState()
    val chatSending by vm.chatSending.collectAsState()
    val savedCount by vm.savedCount.collectAsState()
    val zip by vm.zip.collectAsState()
    val sections by vm.sections.collectAsState()
    val signedIn = email != null
    val isOperator = StarBrookApi.isOperator(email)
    var zipInput by remember(zip) { mutableStateOf(zip.orEmpty()) }
    var zipError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(modifier = Modifier.height(4.dp))
        // Exit 1 — back to News, top of the Hub.
        PortalOutlineButton(text = "← Back to News", onClick = onBack)
        Text(
            text = "Portal",
            style = StarBrookType.headline,
            color = StarBrookColors.textPrimary,
        )

        // Save-prompt contract: reason="save" shows the save context.
        if (reason == "save" && !signedIn) {
            Surface(
                shape = StarBrookShapes.card,
                color = StarBrookColors.gold.copy(alpha = 0.16f),
            ) {
                Text(
                    text = "Sign in to keep this on your Shelf",
                    style = StarBrookType.body,
                    color = StarBrookColors.textPrimary,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
        if (reason == "save" && signedIn) {
            Surface(
                shape = StarBrookShapes.card,
                color = StarBrookColors.gold.copy(alpha = 0.16f),
            ) {
                Text(
                    text = "You're signed in — stories you save now sync to your Shelf on every device.",
                    style = StarBrookType.bodySmall,
                    color = StarBrookColors.textPrimary,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }

        if (!vm.isConfigured) {
            Surface(
                shape = StarBrookShapes.card,
                color = StarBrookColors.liveRed.copy(alpha = 0.14f),
            ) {
                Text(
                    text = "Sign-in isn't set up in this build yet — reading the news still works, and saves stay on this device.",
                    style = StarBrookType.bodySmall,
                    color = StarBrookColors.textPrimary,
                    modifier = Modifier.padding(14.dp),
                )
            }
            HowSavedWorksCard(isSignedIn = false)
            PortalGoldButton(text = "Back to News", onClick = onBack)
            Spacer(modifier = Modifier.height(24.dp))
            return@Column
        }

        if (!signedIn) {
            PortalAuthForm(
                onSignIn = vm::signIn,
                onSignUp = vm::signUp,
                onForgotPassword = vm::forgotPassword,
                isLoading = isLoading,
                error = error,
                notice = notice,
            )
            HowSavedWorksCard(isSignedIn = false)
            // Exit 6 — keep reading without an account.
            PortalOutlineButton(text = "Continue without signing in", onClick = onBack)
        } else {
            // --- Account home ---
            AccountRow(
                email = email!!,
                onSignOut = vm::signOut,
            )
            if (notice != null) {
                Text(
                    text = notice!!,
                    style = StarBrookType.bodySmall,
                    color = StarBrookColors.textSecondary,
                )
            }

            // --- Shelf entry ---
            Surface(
                shape = StarBrookShapes.card,
                color = StarBrookColors.surface,
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Your Shelf",
                        style = StarBrookType.title,
                        color = StarBrookColors.textPrimary,
                    )
                    Text(
                        text = if (savedCount == 0) {
                            "Nothing saved yet — tap the bookmark on any story."
                        } else if (savedCount == 1) {
                            "1 story saved."
                        } else {
                            "$savedCount stories saved."
                        },
                        style = StarBrookType.bodySmall,
                        color = StarBrookColors.textSecondary,
                    )
                    if (syncMessage != null) {
                        Text(
                            text = syncMessage!!,
                            style = StarBrookType.caption,
                            color = StarBrookColors.textSecondary,
                        )
                    }
                    // Exit 5 — the Saved tab (top-level, bottom nav present).
                    PortalGoldButton(text = "Open my Shelf", onClick = onOpenSaved)
                    PortalOutlineButton(text = "Sync now", onClick = vm::syncNow)
                }
            }

            // --- News settings (Newsroom prefs) ---
            Surface(
                shape = StarBrookShapes.card,
                color = StarBrookColors.surface,
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Your news",
                        style = StarBrookType.title,
                        color = StarBrookColors.textPrimary,
                    )
                    Text(
                        text = "These emails go to $email.",
                        style = StarBrookType.bodySmall,
                        color = StarBrookColors.textSecondary,
                    )
                    PortalToggleRow(
                        label = "StarBrook News emails",
                        checked = prefs.subscribed,
                        onChange = vm::setSubscribed,
                    )
                    PortalToggleRow(
                        label = "Morning edition",
                        checked = prefs.morning,
                        onChange = vm::setPrefMorning,
                        enabled = prefs.subscribed,
                    )
                    PortalToggleRow(
                        label = "Evening edition",
                        checked = prefs.evening,
                        onChange = vm::setPrefEvening,
                        enabled = prefs.subscribed,
                    )
                    PortalToggleRow(
                        label = "Breaking news",
                        checked = prefs.breaking,
                        onChange = vm::setPrefBreaking,
                        enabled = prefs.subscribed,
                    )
                    Text(
                        text = "Sections",
                        style = StarBrookType.label,
                        color = StarBrookColors.textSecondary,
                    )
                    // FlowRow, not Row: the eleven chips are wider than
                    // the screen, and a Row squeezes the last one
                    // ("Fashion") until its letters stack vertically.
                    // FlowRow wraps the overflow onto a second line and
                    // every chip keeps the same look.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        for ((key, label) in listOf(
                            "national" to "National",
                            "local" to "Local",
                            "tech" to "Tech",
                            "business" to "Business",
                            "culture" to "Culture",
                            // R-02 (2026-11-24): the six sections the Hub
                            // could never toggle — same set as Settings.
                            "sports" to "Sports",
                            "faith" to "Faith",
                            "science" to "Science",
                            "entertainment" to "Entertainment",
                            "gaming" to "Gaming",
                            "fashion" to "Fashion",
                        )) {
                            val on = key in sections
                            Surface(
                                shape = StarBrookShapes.chip,
                                color = if (on) StarBrookColors.gold else StarBrookColors.surfaceVariant,
                                modifier = Modifier.clickable { vm.setSection(key, !on) },
                            ) {
                                Text(
                                    text = label,
                                    style = StarBrookType.label,
                                    color = if (on) StarBrookColors.onGold else StarBrookColors.textPrimary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                    Text(
                        text = "Your ZIP code",
                        style = StarBrookType.label,
                        color = StarBrookColors.textSecondary,
                    )
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
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = StarBrookColors.surface,
                            unfocusedContainerColor = StarBrookColors.surface,
                            disabledContainerColor = StarBrookColors.surface,
                            focusedIndicatorColor = StarBrookColors.gold,
                            unfocusedIndicatorColor = StarBrookColors.border,
                            errorIndicatorColor = StarBrookColors.liveRed,
                            focusedTextColor = StarBrookColors.textPrimary,
                            unfocusedTextColor = StarBrookColors.textPrimary,
                            cursorColor = StarBrookColors.gold,
                            focusedPlaceholderColor = StarBrookColors.textSecondary,
                            unfocusedPlaceholderColor = StarBrookColors.textSecondary,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PortalOutlineButton(
                        text = "Save ZIP",
                        onClick = {
                            if (zipInput.length == 5) {
                                zipError = null
                                vm.setZip(zipInput)
                            } else {
                                zipError = "That doesn't look like a ZIP code — 5 digits."
                            }
                        },
                    )
                    PortalGoldButton(text = "Save news settings", onClick = vm::savePrefs)
                    if (prefsSavedMessage != null) {
                        Text(
                            text = prefsSavedMessage!!,
                            style = StarBrookType.bodySmall,
                            color = StarBrookColors.textSecondary,
                        )
                    }
                }
            }

            // --- Master Room (operators only, minimal screen) ---
            if (isOperator) {
                Surface(
                    shape = StarBrookShapes.card,
                    color = StarBrookColors.surface,
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "Master Room",
                            style = StarBrookType.title,
                            color = StarBrookColors.textPrimary,
                        )
                        Text(
                            text = "Operator access — newsroom status and CADE.",
                            style = StarBrookType.bodySmall,
                            color = StarBrookColors.textSecondary,
                        )
                        if (masterCards.isEmpty()) {
                            Text(
                                text = "No status to show right now — the newsroom is quiet, or the Master Room couldn't be reached.",
                                style = StarBrookType.bodySmall,
                                color = StarBrookColors.textSecondary,
                            )
                        } else {
                            for (card in masterCards) {
                                MasterRoomCard(
                                    title = card.title,
                                    subtitle = card.subtitle,
                                    statusLabel = card.statusLabel,
                                    statusIsAlert = card.statusIsAlert,
                                    onClick = { /* Detail view is a later step — the card is the status. */ },
                                )
                            }
                        }
                        if (chat.isNotEmpty()) {
                            for (message in chat) {
                                ChatBubble(message = message)
                            }
                        }
                        ChatInput(
                            onSend = vm::sendChat,
                            enabled = !chatSending,
                            placeholder = if (chatSending) "CADE is replying…" else "Message CADE…",
                        )
                    }
                }
            }

            HowSavedWorksCard(isSignedIn = true)
            PortalOutlineButton(text = "Sign out", onClick = vm::signOut)
        }

        // Exit 2 — back to News, bottom of the Hub.
        PortalGoldButton(text = "Back to News", onClick = onBack)
        // Tall bottom spacer: the raised 64dp gold Portal star floats
        // above the bottom bar and covered the last buttons (the gold
        // "Save news settings" button was hidden under it). This lets
        // the whole Hub scroll up clear of the star and the bar.
        Spacer(modifier = Modifier.height(96.dp))
    }
}

// ---------------------------------------------------------------------------
// Account
// ---------------------------------------------------------------------------

/**
 * The signed-in account row: initial avatar, email, "Makryden
 * account", and Sign out. The avatar initial comes from the email —
 * no photo is ever required. Sign-out signs out of the app session
 * only; it never touches the StarBrook browser app's sign-out.
 */
@Composable
fun AccountRow(
    email: String,
    displayName: String? = null,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = StarBrookShapes.card,
        color = StarBrookColors.surface,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(StarBrookColors.gold),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = (displayName?.firstOrNull() ?: email.firstOrNull() ?: '?')
                        .uppercaseChar().toString(),
                    style = StarBrookType.title,
                    color = StarBrookColors.onGold,
                    maxLines = 1,
                )
            }
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = displayName ?: email,
                    style = StarBrookType.body,
                    color = StarBrookColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (displayName != null) email else "Makryden account",
                    style = StarBrookType.caption,
                    color = StarBrookColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "Sign out",
                style = StarBrookType.label,
                color = StarBrookColors.gold,
                modifier = Modifier
                    .clickable(onClick = onSignOut)
                    .padding(8.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// CADE chat (Master Room)
// ---------------------------------------------------------------------------

/** One chat message. [isFromUser] false means CADE (or the newsroom). */
data class ChatMessage(
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long = 0L,
    val senderName: String? = null,
)

/**
 * One chat bubble. CADE's messages sit left on a surface bubble with
 * a gold "CADE" label; yours sit right on gold. Long messages wrap
 * and grow the bubble — text never clips, never runs past an edge.
 * The Master Room chat intro is one line; bubbles carry the rest.
 */
@Composable
fun ChatBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isFromUser) Alignment.End else Alignment.Start,
    ) {
        if (!message.isFromUser) {
            Text(
                text = message.senderName ?: "CADE",
                style = StarBrookType.label,
                color = StarBrookColors.gold,
                modifier = Modifier.padding(start = 12.dp, bottom = 4.dp),
            )
        }
        Surface(
            shape = StarBrookShapes.card,
            color = if (message.isFromUser) StarBrookColors.gold else StarBrookColors.surface,
            modifier = Modifier.fillMaxWidth(0.82f),
        ) {
            Text(
                text = message.text,
                style = StarBrookType.body,
                color = if (message.isFromUser) StarBrookColors.onGold else StarBrookColors.textPrimary,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
        if (message.timestamp > 0L) {
            Text(
                text = formatRelativeTime(message.timestamp),
                style = StarBrookType.caption,
                color = StarBrookColors.textSecondary,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp),
            )
        }
    }
}

/** A gold send arrow, drawn — no icon-font dependency. */
@Composable
private fun SendArrow(modifier: Modifier = Modifier) {
    // Read the token in the composable body — the Canvas draw lambda
    // below is a DrawScope, not a @Composable context.
    val onGold = StarBrookColors.onGold
    Canvas(modifier = modifier.size(20.dp)) {
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        val arrow = Path().apply {
            moveTo(size.width * 0.18f, size.height * 0.5f)
            lineTo(size.width * 0.78f, size.height * 0.5f)
            moveTo(size.width * 0.52f, size.height * 0.24f)
            lineTo(size.width * 0.78f, size.height * 0.5f)
            lineTo(size.width * 0.52f, size.height * 0.76f)
        }
        drawPath(arrow, onGold, style = stroke)
    }
}

/**
 * The chat input: a rounded field plus a gold send button. Send is
 * disabled on blank input (whitespace doesn't send). The field
 * grows to three lines, then scrolls internally — the send button
 * never gets pushed off screen.
 */
@Composable
fun ChatInput(
    onSend: (String) -> Unit,
    enabled: Boolean = true,
    placeholder: String = "Message CADE…",
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    val canSend = enabled && text.isNotBlank()

    fun send() {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        onSend(trimmed)
        text = ""
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            enabled = enabled,
            placeholder = { Text(placeholder, style = StarBrookType.bodySmall) },
            maxLines = 3,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = StarBrookColors.surface,
                unfocusedContainerColor = StarBrookColors.surface,
                disabledContainerColor = StarBrookColors.surface,
                focusedIndicatorColor = StarBrookColors.gold,
                unfocusedIndicatorColor = StarBrookColors.border,
                focusedTextColor = StarBrookColors.textPrimary,
                unfocusedTextColor = StarBrookColors.textPrimary,
                cursorColor = StarBrookColors.gold,
                focusedPlaceholderColor = StarBrookColors.textSecondary,
                unfocusedPlaceholderColor = StarBrookColors.textSecondary,
            ),
            shape = StarBrookShapes.card,
            modifier = Modifier.weight(1f, fill = false),
        )
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (canSend) StarBrookColors.gold else StarBrookColors.surfaceVariant)
                .clickable(enabled = canSend) { send() },
            contentAlignment = Alignment.Center,
        ) {
            SendArrow()
        }
    }
}

// ---------------------------------------------------------------------------
// Master Room
// ---------------------------------------------------------------------------

/**
 * An operator overview card for the Master Room: a pipeline or
 * newsroom status at a glance (e.g. "Morning edition — Shipped
 * 7:00 AM", "Breaking monitor — 1 alert active"). Gold left bar,
 * title + subtitle, and a status chip — gold when all is well,
 * live-red when something needs an operator's eyes. Tapping opens
 * the detail; the card itself never shows a dead button.
 */
@Composable
fun MasterRoomCard(
    title: String,
    subtitle: String,
    statusLabel: String,
    statusIsAlert: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = StarBrookShapes.card,
        color = StarBrookColors.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(if (statusIsAlert) StarBrookColors.liveRed else StarBrookColors.gold),
            )
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title,
                    style = StarBrookType.title,
                    color = StarBrookColors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = StarBrookType.bodySmall,
                    color = StarBrookColors.textSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Surface(
                    shape = StarBrookShapes.chip,
                    color = if (statusIsAlert) StarBrookColors.liveRed.copy(alpha = 0.16f)
                    else StarBrookColors.gold.copy(alpha = 0.16f),
                ) {
                    Text(
                        text = statusLabel,
                        style = StarBrookType.label,
                        color = if (statusIsAlert) StarBrookColors.liveRed else StarBrookColors.gold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Portal Hub — Concept B (the raised gold star in the bottom bar opens
// this dashboard). Buttons rule: every action is a real button — gold
// primary, 48dp+ touch target, chip radius — never a plain-text link
// pretending to be a button. Navigation rule (Matthew's word — nobody
// ever gets stuck): every Hub screen, sheet and dialog has an obvious
// way back to News — the Hub always shows "← Back to News" at the top
// AND at the bottom, the bottom nav stays reachable (the Hub is a
// top-level route), and system back pops to News.
// ---------------------------------------------------------------------------

/** The gold primary button: 48dp tall, full width by default, gold fill. */
@Composable
fun PortalGoldButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = StarBrookColors.gold,
            contentColor = StarBrookColors.onGold,
            disabledContainerColor = StarBrookColors.surfaceVariant,
            disabledContentColor = StarBrookColors.textSecondary,
        ),
        shape = StarBrookShapes.chip,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        Text(text = text, style = StarBrookType.label)
    }
}

/** The secondary button: same size and shape, gold outline on surface. */
@Composable
fun PortalOutlineButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = StarBrookColors.surface,
            contentColor = StarBrookColors.gold,
            disabledContainerColor = StarBrookColors.surfaceVariant,
            disabledContentColor = StarBrookColors.textSecondary,
        ),
        shape = StarBrookShapes.chip,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        Text(text = text, style = StarBrookType.label)
    }
}

/** A five-point star path, centred in the given size. */
private fun starPath(width: Float, height: Float): Path {
    val cx = width / 2f
    val cy = height / 2f
    val outer = minOf(width, height) / 2f
    val inner = outer * 0.42f
    val path = Path()
    for (i in 0 until 10) {
        val radius = if (i % 2 == 0) outer else inner
        // Start at the top point, going clockwise.
        val angle = Math.toRadians(-90.0 + i * 36.0)
        val x = cx + radius * kotlin.math.cos(angle).toFloat()
        val y = cy + radius * kotlin.math.sin(angle).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/**
 * The raised gold Portal star for the bottom bar: a 64dp gold circle
 * that sits proud of the bar (the caller offsets it upward), a drawn
 * gold-on-dark star, and the word "Portal" under it — labelled, not
 * just an icon, so nobody has to guess what it does.
 */
@Composable
fun PortalStarButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gold = StarBrookColors.gold
    val onGold = StarBrookColors.onGold
    Surface(
        modifier = modifier
            .size(64.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = gold,
        shadowElevation = 8.dp,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Canvas(modifier = Modifier.size(24.dp)) {
                drawPath(starPath(size.width, size.height), onGold)
            }
            Text(
                text = "Portal",
                style = StarBrookType.label.copy(fontSize = 10.sp),
                color = onGold,
                maxLines = 1,
            )
        }
    }
}

/**
 * First-run coach mark: a one-time bubble pointing at the star —
 * "Your portal lives here — sign in to save stories, set your news,
 * and more." One tap on "Got it" dismisses it forever (persisted
 * flag in SettingsStore). It never reappears.
 */
@Composable
fun PortalCoachMark(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = StarBrookShapes.card,
        color = StarBrookColors.surface,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Your portal lives here — sign in to save stories, set your news, and more.",
                style = StarBrookType.bodySmall,
                color = StarBrookColors.textPrimary,
            )
            PortalGoldButton(text = "Got it", onClick = onDismiss)
        }
    }
}

/** One Hub toggle row: label on the left, gold switch on the right. */
@Composable
private fun PortalToggleRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
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
            enabled = enabled,
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
 * "How Saved works" — the Shelf policy in plain language. Guest saves
 * are local Room only, on this device; signed-in saves sync across
 * devices. The bookmark on a story always reads the same Room source
 * either way; signing in only changes where the save travels.
 */
@Composable
fun HowSavedWorksCard(
    isSignedIn: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = StarBrookShapes.card,
        color = StarBrookColors.surface,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "How Saved works",
                style = StarBrookType.title,
                color = StarBrookColors.textPrimary,
            )
            Text(
                text = "Tap the bookmark on any story to keep it on your Shelf.",
                style = StarBrookType.bodySmall,
                color = StarBrookColors.textSecondary,
            )
            Text(
                text = if (isSignedIn) {
                    "You're signed in — your Shelf syncs across your devices. " +
                        "Stories you saved before signing in stay on this device and sync the next time the Shelf syncs."
                } else {
                    "Right now you're saving as a guest: saves stay on this device only. " +
                        "Sign in and your Shelf syncs across your devices."
                },
                style = StarBrookType.bodySmall,
                color = StarBrookColors.textPrimary,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Sign-in / create-account form (Hub). CREATE ACCOUNT is as prominent as
// sign-in — two equal buttons at the top, not a small link underneath.
// One plain line says what an account gets you. Email confirmation
// follows the web portal: Supabase emails a confirmation link, and the
// form says "check your inbox" when the account is created but not yet
// confirmed. Passwords are never shown, never logged, never submitted
// while loading.
// ---------------------------------------------------------------------------

enum class PortalAuthMode { SIGN_IN, CREATE_ACCOUNT }

@Composable
fun PortalAuthForm(
    onSignIn: (email: String, password: String, keepSignedIn: Boolean) -> Unit,
    onSignUp: (email: String, password: String, keepSignedIn: Boolean) -> Unit,
    onForgotPassword: (email: String) -> Unit,
    isLoading: Boolean = false,
    error: String? = null,
    notice: String? = null,
    modifier: Modifier = Modifier,
) {
    var mode by remember { mutableStateOf(PortalAuthMode.SIGN_IN) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var keepSignedIn by remember { mutableStateOf(true) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmError by remember { mutableStateOf<String?>(null) }

    val fieldColors = TextFieldDefaults.colors(
        focusedContainerColor = StarBrookColors.surface,
        unfocusedContainerColor = StarBrookColors.surface,
        disabledContainerColor = StarBrookColors.surface,
        errorContainerColor = StarBrookColors.surface,
        focusedIndicatorColor = StarBrookColors.gold,
        unfocusedIndicatorColor = StarBrookColors.border,
        errorIndicatorColor = StarBrookColors.liveRed,
        focusedTextColor = StarBrookColors.textPrimary,
        unfocusedTextColor = StarBrookColors.textPrimary,
        errorTextColor = StarBrookColors.textPrimary,
        focusedLabelColor = StarBrookColors.textSecondary,
        unfocusedLabelColor = StarBrookColors.textSecondary,
        errorLabelColor = StarBrookColors.liveRed,
        cursorColor = StarBrookColors.gold,
        focusedPlaceholderColor = StarBrookColors.textSecondary,
        unfocusedPlaceholderColor = StarBrookColors.textSecondary,
    )

    fun validate(): Boolean {
        emailError = when {
            email.isBlank() -> "Enter your email address."
            !email.contains("@") || !email.substringAfter("@").contains(".") ->
                "That doesn't look like an email address."
            else -> null
        }
        passwordError = when {
            password.isBlank() -> "Enter your password."
            mode == PortalAuthMode.CREATE_ACCOUNT && password.length < 12 ->
                "Use at least 12 characters — a phrase you can remember works best."
            else -> null
        }
        confirmError = if (mode == PortalAuthMode.CREATE_ACCOUNT &&
            confirmPassword != password
        ) {
            "Those passwords don't match."
        } else {
            null
        }
        return emailError == null && passwordError == null && confirmError == null
    }

    fun submit() {
        if (!validate()) return
        if (mode == PortalAuthMode.CREATE_ACCOUNT) {
            onSignUp(email.trim(), password, keepSignedIn)
        } else {
            onSignIn(email.trim(), password, keepSignedIn)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LiveStarMark(size = 48.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(
            text = "Your Makryden account",
            style = StarBrookType.headline,
            color = StarBrookColors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        // One plain line: what an account gets you.
        Text(
            text = "An account gets you stories saved across devices, your editions, your ZIP, and breaking emails.",
            style = StarBrookType.bodySmall,
            color = StarBrookColors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        // Mode switch — two equal, prominent buttons, never a small link.
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            PortalGoldButton(
                text = "Sign in",
                onClick = { mode = PortalAuthMode.SIGN_IN },
                enabled = !isLoading && mode != PortalAuthMode.SIGN_IN,
                modifier = Modifier.weight(1f),
            )
            PortalGoldButton(
                text = "Create account",
                onClick = { mode = PortalAuthMode.CREATE_ACCOUNT },
                enabled = !isLoading && mode != PortalAuthMode.CREATE_ACCOUNT,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = if (mode == PortalAuthMode.CREATE_ACCOUNT) {
                "New here? Create your free Makryden account with an email and password."
            } else {
                "Already have a Makryden account? Sign in with the same email and password as the website."
            },
            style = StarBrookType.caption,
            color = StarBrookColors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        if (notice != null) {
            Surface(
                shape = StarBrookShapes.card,
                color = StarBrookColors.gold.copy(alpha = 0.16f),
            ) {
                Text(
                    text = notice,
                    style = StarBrookType.bodySmall,
                    color = StarBrookColors.textPrimary,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
        if (error != null) {
            Surface(
                shape = StarBrookShapes.card,
                color = StarBrookColors.liveRed.copy(alpha = 0.14f),
            ) {
                Text(
                    text = error,
                    style = StarBrookType.bodySmall,
                    color = StarBrookColors.textPrimary,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; emailError = null },
            label = { Text("Email address") },
            placeholder = { Text("you@example.com") },
            singleLine = true,
            enabled = !isLoading,
            isError = emailError != null,
            supportingText = emailError?.let { { Text(it, style = StarBrookType.caption) } },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            colors = fieldColors,
            shape = StarBrookShapes.card,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; passwordError = null },
            label = { Text("Password") },
            singleLine = true,
            enabled = !isLoading,
            isError = passwordError != null,
            supportingText = passwordError?.let { { Text(it, style = StarBrookType.caption) } },
            visualTransformation = if (passwordVisible) VisualTransformation.None
            else PasswordVisualTransformation(),
            trailingIcon = {
                Text(
                    text = if (passwordVisible) "Hide" else "Show",
                    style = StarBrookType.label,
                    color = StarBrookColors.gold,
                    modifier = Modifier
                        .clickable { passwordVisible = !passwordVisible }
                        .padding(8.dp),
                )
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = if (mode == PortalAuthMode.CREATE_ACCOUNT) ImeAction.Next else ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            colors = fieldColors,
            shape = StarBrookShapes.card,
            modifier = Modifier.fillMaxWidth(),
        )

        if (mode == PortalAuthMode.CREATE_ACCOUNT) {
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; confirmError = null },
                label = { Text("Confirm password") },
                singleLine = true,
                enabled = !isLoading,
                isError = confirmError != null,
                supportingText = confirmError?.let { { Text(it, style = StarBrookType.caption) } },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                colors = fieldColors,
                shape = StarBrookShapes.card,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "At least 12 characters. We'll email you a confirmation link — your account starts once you tap it.",
                style = StarBrookType.caption,
                color = StarBrookColors.textSecondary,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Checkbox(
                checked = keepSignedIn,
                onCheckedChange = { keepSignedIn = it },
                enabled = !isLoading,
                colors = CheckboxDefaults.colors(
                    checkedColor = StarBrookColors.gold,
                    uncheckedColor = StarBrookColors.border,
                    checkmarkColor = StarBrookColors.onGold,
                ),
            )
            Text(
                text = "Keep me signed in",
                style = StarBrookType.bodySmall,
                color = StarBrookColors.textPrimary,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clickable { keepSignedIn = !keepSignedIn },
            )
            if (mode == PortalAuthMode.SIGN_IN) {
                // Fixed-width box: the outline button fills it, so the
                // "Forgot password?" label never wraps or clips.
                Box(modifier = Modifier.width(168.dp)) {
                    PortalOutlineButton(
                        text = "Forgot password?",
                        onClick = {
                            if (email.isBlank() || !email.contains("@")) {
                                emailError = "Enter your email address first — the reset link goes there."
                            } else {
                                onForgotPassword(email.trim())
                            }
                        },
                        enabled = !isLoading,
                    )
                }
            }
        }

        PortalGoldButton(
            text = if (isLoading) {
                if (mode == PortalAuthMode.CREATE_ACCOUNT) "Creating account…" else "Signing in…"
            } else {
                if (mode == PortalAuthMode.CREATE_ACCOUNT) "Create account" else "Sign in"
            },
            onClick = ::submit,
            enabled = !isLoading,
        )
        Text(
            text = "Free to read — no paywall, no ads. Signing in is optional and only adds your prefs, Shelf sync, and the Master Room.",
            style = StarBrookType.caption,
            color = StarBrookColors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
