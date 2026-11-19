package com.makrydensystems.news.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.makrydensystems.news.ui.theme.StarBrookColors
import com.makrydensystems.news.ui.theme.StarBrookType

/**
 * Native Media3 player. No embedded web player: the video URL is a
 * direct MP4 from the news-videos bucket.
 *
 * DEFECT 2 (tap test 2026-11-19): tapping a briefing showed the video's
 * first frame (the static Live Star logo intro) and stayed there — no
 * playback advance, no player controls, no progress, no spinner. The
 * previous version relied entirely on PlayerView defaults inside a
 * Compose AndroidView: prepare() + playWhenReady were set, but nothing
 * ever asserted playback after the screen appeared, the controls were
 * never explicitly shown, the buffering spinner defaults to NEVER
 * (PlayerView.show_buffering), and the 16:9 box pillarboxed the 9:16
 * portrait briefings to a ~115dp column — which read as "no player".
 * Media3 also ships a dedicated workaround for Compose AndroidView +
 * SurfaceView sync issues (androidx/media#1237) that was not enabled.
 *
 * This version makes every step explicit: player built before the view
 * attaches, play() asserted in LaunchedEffect, showController() called
 * on attach, buffering spinner on, Compose-surface workaround on, the
 * player box sized for the real 9:16 format, a loading indicator while
 * buffering, and an error message + retry instead of a silent black
 * frame. Same screen, same dark/gold look — Back, player, title.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoScreen(
    url: String,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var retryToken by remember { mutableStateOf(0) }
    var playerState by remember { mutableStateOf(Player.STATE_IDLE) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var isFullscreen by remember { mutableStateOf(false) }

    // One player per URL (per retry). Created here — not in the AndroidView
    // factory — so the media is prepared before any view attaches.
    val player = remember(url, retryToken) {
        if (url.isBlank()) {
            null
        } else {
            ExoPlayer.Builder(context).build().apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        playerState = playbackState
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        playerError = error.message ?: "Could not play this video."
                    }
                })
                setMediaItem(MediaItem.fromUri(url))
                prepare()
                playWhenReady = true
            }
        }
    }

    // Release exactly the player this composition created.
    DisposableEffect(url, retryToken) {
        onDispose { player?.release() }
    }

    // Deterministic start trigger: assert play() once the screen is
    // composed instead of trusting prepare() + playWhenReady alone.
    LaunchedEffect(url, retryToken) {
        player?.play()
    }

    // Pause when the app leaves the foreground so audio never keeps
    // playing behind another app. Returning does not auto-resume: the
    // paused controller stays visible and the user taps play.
    DisposableEffect(lifecycleOwner, url, retryToken) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                player?.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // System back exits fullscreen first instead of leaving the screen.
    BackHandler(enabled = isFullscreen) { isFullscreen = false }

    val errorMessage = playerError
    if (isFullscreen && player != null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            VideoPlayerView(
                player = player,
                isFullscreen = true,
                onFullscreenToggle = { isFullscreen = false },
                modifier = Modifier.fillMaxSize(),
            )
            Text(
                text = "‹ Exit fullscreen",
                style = StarBrookType.label,
                color = StarBrookColors.gold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .clickable { isFullscreen = false },
            )
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = "‹ Back",
                style = StarBrookType.label,
                color = StarBrookColors.gold,
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .clickable(onClick = onBack),
            )
            if (url.isBlank()) {
                Text(
                    text = "Video link unavailable.",
                    style = StarBrookType.body,
                    color = StarBrookColors.textSecondary,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                // Briefings are 9:16 portrait (1080x1920). A 16:9 box
                // pillarboxed them to a ~115dp column with the controller
                // squeezed onto the black bars. Size the box for the
                // real format instead: full height, 9:16 wide, centered.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(480.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val activePlayer = player
                    if (activePlayer != null) {
                        VideoPlayerView(
                            player = activePlayer,
                            isFullscreen = false,
                            onFullscreenToggle = { isFullscreen = true },
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(9f / 16f),
                        )
                    }
                    if ((playerState == Player.STATE_IDLE ||
                            playerState == Player.STATE_BUFFERING) &&
                        errorMessage == null
                    ) {
                        CircularProgressIndicator(
                            color = StarBrookColors.gold,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = StarBrookType.title,
                color = StarBrookColors.textPrimary,
            )
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorMessage,
                    style = StarBrookType.bodySmall,
                    color = StarBrookColors.liveRed,
                )
                Text(
                    text = "Tap to retry",
                    style = StarBrookType.label,
                    color = StarBrookColors.gold,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .clickable {
                            playerError = null
                            playerState = Player.STATE_IDLE
                            retryToken++
                        },
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * The Media3 PlayerView hosted in Compose. The stock PlayerControlView
 * supplies the real controls: play/pause, seek bar with position and
 * duration, and — once a click listener is set — the fullscreen button.
 * Nothing here is decorative: every control is wired to the player.
 */
@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayerView(
    player: ExoPlayer,
    isFullscreen: Boolean,
    onFullscreenToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Recreate the view (not just rebind) when the player instance
    // changes, e.g. after a retry — the new player gets a fresh
    // PlayerView, a fresh attach, and a fresh showController().
    key(player) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    setUseController(true)
                    setControllerAutoShow(true)
                    setControllerShowTimeoutMs(5000)
                    // The buffering spinner is NEVER shown by default;
                    // without this the player stalls silently.
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT)
                    // Compose AndroidView + SurfaceView sync workaround
                    // (Media3 issue #1237, API 34 target).
                    setEnableComposeSurfaceSyncWorkaround(true)
                    // Setting a listener is what makes the fullscreen
                    // button appear in the stock controller.
                    setFullscreenButtonClickListener { _ -> onFullscreenToggle() }
                    // Do not rely on auto-show timing across the nav
                    // transition: show the controls now. They hide
                    // again after the timeout while playing, and stay
                    // up indefinitely while paused, ended, or failed.
                    showController()
                }
            },
            update = { view ->
                view.setFullscreenButtonState(isFullscreen)
            },
            onRelease = { view ->
                view.player = null
            },
            modifier = modifier,
        )
    }
}
