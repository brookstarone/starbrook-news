package com.makrydensystems.news.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.makrydensystems.news.data.update.AppUpdateState
import com.makrydensystems.news.ui.theme.StarBrookColors
import com.makrydensystems.news.ui.theme.StarBrookShapes
import com.makrydensystems.news.ui.theme.StarBrookType

/**
 * In-app update prompt (2026-11-23): the visible Update button, inside
 * the app. No browser, no Drive link — tapping Update downloads the APK
 * here and opens the system installer. The copy states the honest limit
 * every time: Android asks the user to tap Install once; the app never
 * promises a silent update.
 *
 * Buttons: [onLater] hides the prompt AND records this version as
 * dismissed (a newer version still prompts); [onHide] hides it for
 * this session only (a failed download, a not-now on the permission
 * screen — the next check re-prompts).
 */
@Composable
fun AppUpdateDialog(
    state: AppUpdateState,
    onUpdate: () -> Unit,
    onInstall: () -> Unit,
    onOpenSettings: () -> Unit,
    onLater: () -> Unit,
    onHide: () -> Unit,
) {
    when (state) {
        is AppUpdateState.None -> {
            // Nothing to show.
        }
        is AppUpdateState.Available -> {
            val manifest = state.manifest
            AlertDialog(
                onDismissRequest = { if (!manifest.mandatory) onLater() },
                title = {
                    Text(text = "Update available", style = StarBrookType.title)
                },
                text = {
                    Text(
                        text = updateAvailableBody(manifest.versionName, manifest.releaseNotes),
                        style = StarBrookType.body,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = onUpdate,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = StarBrookColors.gold,
                        ),
                    ) {
                        Text(text = "Update", style = StarBrookType.label)
                    }
                },
                dismissButton = {
                    if (!manifest.mandatory) {
                        TextButton(onClick = onLater) {
                            Text(text = "Later", style = StarBrookType.label)
                        }
                    }
                },
                properties = DialogProperties(
                    dismissOnBackPress = !manifest.mandatory,
                    dismissOnClickOutside = !manifest.mandatory,
                ),
            )
        }
        is AppUpdateState.Downloading -> {
            // No buttons while downloading — it is seconds, and the
            // OkHttp timeouts bound a stuck download (then Failed).
            Dialog(onDismissRequest = {}) {
                Surface(shape = StarBrookShapes.card, color = StarBrookColors.surface) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "Downloading update…",
                            style = StarBrookType.title,
                        )
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "${(state.progress * 100).toInt()}% downloaded",
                            style = StarBrookType.body,
                            color = StarBrookColors.textSecondary,
                        )
                    }
                }
            }
        }
        is AppUpdateState.Downloaded -> {
            val manifest = state.manifest
            AlertDialog(
                onDismissRequest = { if (!manifest.mandatory) onLater() },
                title = {
                    Text(text = "Update downloaded", style = StarBrookType.title)
                },
                text = {
                    Text(
                        text = "Tap Install — Android asks you to confirm once, " +
                            "and the app opens on the new version.",
                        style = StarBrookType.body,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = onInstall,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = StarBrookColors.gold,
                        ),
                    ) {
                        Text(text = "Install", style = StarBrookType.label)
                    }
                },
                dismissButton = {
                    if (!manifest.mandatory) {
                        TextButton(onClick = onLater) {
                            Text(text = "Later", style = StarBrookType.label)
                        }
                    }
                },
                properties = DialogProperties(
                    dismissOnBackPress = !manifest.mandatory,
                    dismissOnClickOutside = !manifest.mandatory,
                ),
            )
        }
        is AppUpdateState.NeedsPermission -> {
            AlertDialog(
                onDismissRequest = onHide,
                title = {
                    Text(text = "Allow installs to update", style = StarBrookType.title)
                },
                text = {
                    Text(
                        text = "Android needs you to allow StarBrook News to install " +
                            "apps first. Tap Settings below, turn on “Allow from " +
                            "this source”, then come back and tap Update.",
                        style = StarBrookType.body,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = onOpenSettings,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = StarBrookColors.gold,
                        ),
                    ) {
                        Text(text = "Open settings", style = StarBrookType.label)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onUpdate) {
                        Text(text = "Update", style = StarBrookType.label)
                    }
                },
            )
        }
        is AppUpdateState.Failed -> {
            AlertDialog(
                onDismissRequest = onHide,
                title = {
                    Text(text = "Couldn’t download the update", style = StarBrookType.title)
                },
                text = {
                    Text(
                        text = "Check your connection and try again.",
                        style = StarBrookType.body,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = onUpdate,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = StarBrookColors.gold,
                        ),
                    ) {
                        Text(text = "Retry", style = StarBrookType.label)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onHide) {
                        Text(text = "Later", style = StarBrookType.label)
                    }
                },
            )
        }
    }
}

private fun updateAvailableBody(versionName: String, releaseNotes: String): String {
    val head = versionName.takeIf { it.isNotBlank() }
        ?.let { "StarBrook News $it is ready." }
        ?: "A new StarBrook News update is ready."
    val notes = releaseNotes.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
    return "$head$notes The update downloads here in the app, then " +
        "Android asks you to tap Install once."
}
