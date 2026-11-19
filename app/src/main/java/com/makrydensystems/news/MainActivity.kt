package com.makrydensystems.news

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.makrydensystems.news.data.StarBrookApi
import com.makrydensystems.news.data.di.AppContainer
import com.makrydensystems.news.ui.HomeScreen
import com.makrydensystems.news.ui.ReadScreen
import com.makrydensystems.news.ui.SavedScreen
import com.makrydensystems.news.ui.SearchScreen
import com.makrydensystems.news.ui.SettingsScreen
import com.makrydensystems.news.ui.VideoScreen
import com.makrydensystems.news.ui.WeatherScreen
import com.makrydensystems.news.ui.components.StarBrookBottomNav
import com.makrydensystems.news.ui.components.StarBrookRoutes
import com.makrydensystems.news.ui.components.StarBrookTopBar
import com.makrydensystems.news.ui.theme.StarBrookTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StarBrookTheme {
                val container = remember { AppContainer(applicationContext) }
                StarBrookApp(container)
            }
        }
    }
}

private val topLevelRoutes = setOf(
    StarBrookRoutes.NEWS,
    StarBrookRoutes.WEATHER,
    StarBrookRoutes.SAVED,
    StarBrookRoutes.SETTINGS,
)

private fun titleFor(route: String?): String = when (route) {
    StarBrookRoutes.WEATHER -> "Weather"
    StarBrookRoutes.SAVED -> "Saved"
    StarBrookRoutes.SETTINGS -> "Settings"
    "search" -> "Search"
    else -> "StarBrook News"
}

@Composable
private fun StarBrookApp(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTopLevel = currentRoute in topLevelRoutes

    Scaffold(
        topBar = {
            if (isTopLevel) {
                StarBrookTopBar(title = titleFor(currentRoute))
            }
        },
        bottomBar = {
            if (isTopLevel) {
                StarBrookBottomNav(
                    currentRoute = currentRoute ?: StarBrookRoutes.NEWS,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(StarBrookRoutes.NEWS) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
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
}
