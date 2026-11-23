package com.makrydensystems.news

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.makrydensystems.news.data.StarBrookApi
import com.makrydensystems.news.data.di.AppContainer
import com.makrydensystems.news.data.push.NotificationChannels
import com.makrydensystems.news.data.push.StarBrookMessagingService
import com.makrydensystems.news.ui.HomeScreen
import com.makrydensystems.news.ui.ReadScreen
import com.makrydensystems.news.ui.SavedScreen
import com.makrydensystems.news.ui.SearchScreen
import com.makrydensystems.news.ui.SettingsScreen
import com.makrydensystems.news.ui.VideoScreen
import com.makrydensystems.news.ui.WeatherScreen
import com.makrydensystems.news.ui.components.AppUpdateDialog
import com.makrydensystems.news.ui.components.PortalCoachMark
import com.makrydensystems.news.ui.components.PortalHubScreen
import com.makrydensystems.news.ui.components.PortalStarButton
import com.makrydensystems.news.ui.components.StarBrookBottomNav
import com.makrydensystems.news.ui.components.StarBrookRoutes
import com.makrydensystems.news.ui.components.StarBrookTopBar
import com.makrydensystems.news.ui.theme.StarBrookTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    // Pending notification-tap route (StarBrookMessagingService puts
    // the route in EXTRA_ROUTE). A StateFlow so onNewIntent — a tap
    // while the app is already open (singleTop) — reaches the
    // composable the same way a cold-start intent does.
    private val deepLinkRoute = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deepLinkRoute.value = intent?.getStringExtra(StarBrookMessagingService.EXTRA_ROUTE)
        setContent {
            StarBrookTheme {
                val container = remember { AppContainer(applicationContext) }
                StarBrookApp(container, deepLinkRoute)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deepLinkRoute.value = intent.getStringExtra(StarBrookMessagingService.EXTRA_ROUTE)
    }
}

private val topLevelRoutes = setOf(
    StarBrookRoutes.NEWS,
    StarBrookRoutes.WEATHER,
    StarBrookRoutes.SAVED,
    StarBrookRoutes.SETTINGS,
)

/**
 * The Portal Hub route (Concept B). The optional [reason] is the
 * save-prompt contract: other agents navigate to "portal?reason=save"
 * on a signed-out save tap and the Hub shows the save context.
 * Plain "portal" (no reason) opens the Hub as-is.
 */
private const val PORTAL_ROUTE = "portal?reason={reason}"

private fun openPortal(navController: NavController, reason: String? = null) {
    val route = if (reason.isNullOrBlank()) "portal" else "portal?reason=$reason"
    navController.navigate(route) {
        popUpTo(StarBrookRoutes.NEWS) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun titleFor(route: String?): String = when {
    route == null -> "StarBrook News"
    route.startsWith("portal") -> "Portal"
    route == StarBrookRoutes.WEATHER -> "Weather"
    route == StarBrookRoutes.SAVED -> "Saved"
    route == StarBrookRoutes.SETTINGS -> "Settings"
    route == "search" -> "Search"
    else -> "StarBrook News"
}

@Composable
private fun StarBrookApp(container: AppContainer, deepLinkRoute: MutableStateFlow<String?>) {
    val navController = rememberNavController()
    // FCM push (Build #24): notification channels exist from the first
    // cold start, and this device's token is registered with the server
    // (or unregistered when every toggle is off). Both are best-effort
    // — a failure never blocks the UI, and the next cold start retries.
    val appContext = LocalContext.current.applicationContext
    LaunchedEffect(Unit) {
        NotificationChannels.createChannels(appContext)
        container.fcmRegistrar.ensureRegistered()
    }
    // In-app updater (Build #25): check on launch, then once a day
    // while the app stays open. The dialog below carries the visible
    // Update button — no browser, no Drive link, the whole flow runs
    // in-app. Check failures are silent by design.
    val updateChecker = container.appUpdateChecker
    val updateState by updateChecker.state.collectAsState()
    LaunchedEffect(Unit) {
        updateChecker.monitorUpdates()
    }
    // Notification tap: navigate to the pushed route once, then clear
    // it so a recomposition does not re-navigate. The route was already
    // validated by StarBrookMessagingService (validRoute).
    val pendingRoute by deepLinkRoute.collectAsState()
    LaunchedEffect(pendingRoute) {
        val route = pendingRoute ?: return@LaunchedEffect
        deepLinkRoute.value = null
        navController.navigate(route) { launchSingleTop = true }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // The Hub is a top-level route: the masthead keeps the Live Star as
    // a visual only, the bottom nav stays reachable from the Hub, and
    // system back pops the Hub straight to News.
    val isPortal = currentRoute?.startsWith("portal") == true
    val isTopLevel = currentRoute in topLevelRoutes || isPortal
    val scope = rememberCoroutineScope()
    val portalHintSeen by container.settingsStore.portalHintSeenFlow
        .collectAsState(initial = true)
    // Signed-in state for the Home bookmark seam + the Shelf guest line:
    // the portal session (mk-portal-session mirror) is null when signed
    // out. Collected here so MainActivity can pass it down.
    val portalSession by container.settingsStore.portalSessionFlow
        .collectAsState(initial = null)
    val isSignedIn = portalSession != null
    // First-run coach mark: once, on the home tab, until dismissed.
    val showPortalHint = !portalHintSeen && currentRoute == StarBrookRoutes.NEWS

    fun dismissPortalHint() {
        scope.launch { container.settingsStore.setPortalHintSeen() }
    }

    Scaffold(
        topBar = {
            if (isTopLevel) {
                StarBrookTopBar(title = titleFor(currentRoute))
            }
        },
        bottomBar = {
            if (isTopLevel) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    StarBrookBottomNav(
                        // On the Hub no tab is selected (the star is the
                        // Hub's mark); on every other top-level screen
                        // the matching tab is.
                        currentRoute = currentRoute ?: StarBrookRoutes.NEWS,
                        onNavigate = { route ->
                            navController.navigate(route) {
                                popUpTo(StarBrookRoutes.NEWS) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                    // The raised gold Portal star, centred on the bar and
                    // lifted above it — labelled "Portal", unmissable.
                    // It sits on the Weather/Saved boundary; the tab
                    // icons sit ~45dp either side of centre, the star
                    // is 64dp wide, so no tab icon or label is covered.
                    PortalStarButton(
                        onClick = {
                            dismissPortalHint()
                            openPortal(navController)
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = (-18).dp),
                    )
                    if (showPortalHint) {
                        PortalCoachMark(
                            onDismiss = { dismissPortalHint() },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .offset(y = (-92).dp)
                                .padding(horizontal = 16.dp),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = StarBrookRoutes.NEWS,
            modifier = Modifier.padding(padding),
        ) {
            composable(StarBrookRoutes.NEWS) {
                HomeScreen(
                    container = container,
                    onStoryClick = { story ->
                        navController.navigate(
                            "story?link=${Uri.encode(story.link)}" +
                                "&title=${Uri.encode(story.title)}",
                        )
                    },
                    onVideoClick = { video ->
                        navController.navigate(
                            "play?url=${Uri.encode(StarBrookApi.videoUrl(video.file))}" +
                                "&title=${Uri.encode(video.title)}",
                        )
                    },
                    onBreakingClick = { breaking ->
                        navController.navigate(
                            "story?link=${Uri.encode(breaking.url)}" +
                                "&title=${Uri.encode(breaking.title)}",
                        )
                    },
                    onLiveClick = { live ->
                        navController.navigate(
                            "story?link=${Uri.encode(live.url)}" +
                                "&title=${Uri.encode(live.title)}",
                        )
                    },
                    onSearchClick = { navController.navigate("search") },
                    // Merge hookups (Wave 2): drawer Weather row -> the
                    // Weather tab; bookmark seam -> the portal session
                    // state + the save prompt (portal?reason=save).
                    onWeatherClick = {
                        navController.navigate(StarBrookRoutes.WEATHER) {
                            popUpTo(StarBrookRoutes.NEWS) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    isSignedIn = isSignedIn,
                    onPortalClick = { openPortal(navController, "save") },
                )
            }
            composable(StarBrookRoutes.WEATHER) {
                WeatherScreen(
                    container = container,
                    onPlayVideo = { url, title ->
                        navController.navigate(
                            "play?url=${Uri.encode(url)}" +
                                "&title=${Uri.encode(title)}",
                        )
                    },
                )
            }
            composable(StarBrookRoutes.SAVED) {
                SavedScreen(
                    container = container,
                    signedIn = isSignedIn,
                    onStoryClick = { story ->
                        navController.navigate(
                            "story?link=${Uri.encode(story.link)}" +
                                "&title=${Uri.encode(story.title)}",
                        )
                    },
                )
            }
            composable(StarBrookRoutes.SETTINGS) {
                SettingsScreen(container = container)
            }
            composable(
                route = PORTAL_ROUTE,
                arguments = listOf(
                    navArgument("reason") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                // The Portal Hub (Concept B). Exits: the Hub's own
                // "← Back to News" / "Back to News" buttons, the bottom
                // nav (always visible on this top-level route), and
                // system back (the Hub sits above News on the stack).
                PortalHubScreen(
                    container = container,
                    reason = entry.arguments?.getString("reason")
                        ?.takeIf { it.isNotBlank() },
                    onBack = {
                        navController.navigate(StarBrookRoutes.NEWS) {
                            popUpTo(StarBrookRoutes.NEWS) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenSaved = {
                        navController.navigate(StarBrookRoutes.SAVED) {
                            popUpTo(StarBrookRoutes.NEWS) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable("search") {
                SearchScreen(
                    container = container,
                    onStoryClick = { story ->
                        navController.navigate(
                            "story?link=${Uri.encode(story.link)}" +
                                "&title=${Uri.encode(story.title)}",
                        )
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = "story?link={link}&title={title}",
                arguments = listOf(
                    navArgument("link") { type = NavType.StringType; defaultValue = "" },
                    navArgument("title") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                ReadScreen(
                    container = container,
                    link = entry.arguments?.getString("link").orEmpty(),
                    title = entry.arguments?.getString("title").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = "play?url={url}&title={title}",
                arguments = listOf(
                    navArgument("url") { type = NavType.StringType; defaultValue = "" },
                    navArgument("title") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                VideoScreen(
                    url = entry.arguments?.getString("url").orEmpty(),
                    title = entry.arguments?.getString("title").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
    // In-app update prompt (Build #25): renders above every screen.
    // "Later" records this version as dismissed; a failed download's
    // Later only hides it for this session, so the next check
    // re-prompts.
    AppUpdateDialog(
        state = updateState,
        onUpdate = { scope.launch { updateChecker.startUpdate() } },
        onInstall = { updateChecker.installDownloaded() },
        onOpenSettings = { updateChecker.openUnknownSourcesSettings() },
        onLater = { scope.launch { updateChecker.dismissUpdate() } },
        onHide = { updateChecker.hidePrompt() },
    )
}
