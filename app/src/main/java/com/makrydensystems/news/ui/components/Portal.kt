package com.makrydensystems.news.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
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
import com.makrydensystems.news.ui.theme.StarBrookColors
import com.makrydensystems.news.ui.theme.StarBrookShapes
import com.makrydensystems.news.ui.theme.StarBrookType

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
