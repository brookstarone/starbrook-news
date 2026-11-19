package com.makrydensystems.news.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makrydensystems.news.ui.theme.StarBrookColors
import com.makrydensystems.news.ui.theme.StarBrookShapes
import com.makrydensystems.news.ui.theme.StarBrookType

/** Navigation routes, matching CONTRACT.md. */
object StarBrookRoutes {
    const val NEWS = "news"
    const val WEATHER = "weather"
    const val SAVED = "saved"
    const val SETTINGS = "settings"
}

/**
 * The app top bar: the web masthead, native — deep #160F2B, a 3px
 * gold bottom border, the 32px Live Star, the two-tone wordmark
 * (STARBROOK white, NEWS gold), the tagline, and a status row with
 * the green pulsing LIVE dot. The star is a visual logo only: the
 * portal front door is a raised gold star button in the BOTTOM BAR
 * (another agent) — never a star button in the masthead.
 *
 * On the home screen ([title] == "StarBrook News") the full masthead
 * renders. Inner screens get the same deep bar + gold border with
 * their own title. [editionLabel] (e.g. "Morning Edition") and
 * [sourcesLabel] (e.g. "29 sources") fill the status-row pills when
 * the caller knows them; MainActivity does not pass them yet, so the
 * pills stay hidden until the caller is wired.
 */
@Composable
fun StarBrookTopBar(
    title: String,
    editionLabel: String? = null,
    sourcesLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = StarBrookColors.surfaceVariant,
    ) {
        Column {
            if (title == "StarBrook News") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LiveStarMark(size = 32.dp)
                    Column(modifier = Modifier.padding(start = 10.dp)) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = StarBrookColors.textPrimary)) {
                                    append("STARBROOK ")
                                }
                                withStyle(SpanStyle(color = StarBrookColors.gold)) {
                                    append("NEWS")
                                }
                            },
                            style = StarBrookType.title.copy(
                                fontSize = 16.sp,
                                lineHeight = 18.sp,
                                letterSpacing = 0.5.sp,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "In the moment. In the now.",
                            style = StarBrookType.label.copy(fontSize = 12.sp),
                            color = StarBrookColors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // Status row (web .live-row): LIVE dot + label, sources
                // pill, edition chip pinned to the right.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LiveDot(size = 8.dp)
                    Text(
                        text = "LIVE",
                        style = StarBrookType.label.copy(
                            fontSize = 11.sp,
                            letterSpacing = 0.9.sp,
                        ),
                        color = StarBrookLiveGreen,
                        maxLines = 1,
                    )
                    if (sourcesLabel != null) {
                        MastheadPill(text = sourcesLabel)
                    }
                    if (editionLabel != null) {
                        Spacer(modifier = Modifier.weight(1f))
                        MastheadPill(text = editionLabel)
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LiveStarMark(size = 32.dp)
                    Text(
                        text = title,
                        style = StarBrookType.title,
                        color = StarBrookColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
            HorizontalDivider(color = StarBrookColors.gold, thickness = 3.dp)
        }
    }
}

/** Gold pill for the masthead status row (web .sourcespill / .edchip):
 * gold text on goldsoft, 1px gold border, fully round. */
@Composable
private fun MastheadPill(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = StarBrookShapes.chip,
        color = StarBrookColors.goldsoft,
        border = BorderStroke(1.dp, StarBrookColors.gold),
    ) {
        Text(
            text = text,
            style = StarBrookType.label.copy(fontSize = 11.sp),
            color = StarBrookColors.gold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp),
        )
    }
}

/**
 * Bottom-nav glyphs, hand-drawn on Canvas so the design layer has
 * zero icon-font dependencies. Four geometric marks, 24dp, 1.8dp
 * stroke, round caps — newspaper, sun-behind-cloud, bookmark,
 * sliders. They read at arm's length and stay crisp at any density.
 */
@Composable
private fun StarBrookNavIcon(route: String, selected: Boolean) {
    val color = if (selected) StarBrookColors.gold else StarBrookColors.textSecondary
    Canvas(modifier = Modifier.size(24.dp)) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        val w = size.width
        val h = size.height
        when (route) {
            StarBrookRoutes.NEWS -> {
                // Newspaper: outline + headline rule + text lines.
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.12f, h * 0.14f),
                    size = androidx.compose.ui.geometry.Size(w * 0.76f, h * 0.72f),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                    style = stroke,
                )
                drawLine(color, Offset(w * 0.24f, h * 0.32f), Offset(w * 0.76f, h * 0.32f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.24f, h * 0.50f), Offset(w * 0.62f, h * 0.50f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.24f, h * 0.66f), Offset(w * 0.62f, h * 0.66f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            StarBrookRoutes.WEATHER -> {
                // Sun (upper left) behind a cloud (lower right).
                drawCircle(color, radius = 3.2.dp.toPx(), center = Offset(w * 0.32f, h * 0.32f), style = stroke)
                drawCircle(color, radius = 2.6.dp.toPx(), center = Offset(w * 0.52f, h * 0.62f))
                drawCircle(color, radius = 3.4.dp.toPx(), center = Offset(w * 0.66f, h * 0.58f))
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.30f, h * 0.60f),
                    size = androidx.compose.ui.geometry.Size(w * 0.52f, h * 0.22f),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                )
            }
            StarBrookRoutes.SAVED -> {
                // Bookmark outline.
                val path = Path().apply {
                    moveTo(w * 0.28f, h * 0.14f)
                    lineTo(w * 0.72f, h * 0.14f)
                    lineTo(w * 0.72f, h * 0.86f)
                    lineTo(w * 0.50f, h * 0.68f)
                    lineTo(w * 0.28f, h * 0.86f)
                    close()
                }
                drawPath(path, color, style = stroke)
            }
            StarBrookRoutes.SETTINGS -> {
                // Sliders: three rails, one knob each.
                val rails = listOf(0.28f to 0.62f, 0.50f to 0.36f, 0.72f to 0.58f)
                for ((yFrac, knobFrac) in rails) {
                    val y = h * yFrac
                    drawLine(color, Offset(w * 0.16f, y), Offset(w * 0.84f, y), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawCircle(color, radius = 2.4.dp.toPx(), center = Offset(w * knobFrac, y))
                }
            }
        }
    }
}

private data class NavDestination(val route: String, val label: String)

private val destinations = listOf(
    NavDestination(StarBrookRoutes.NEWS, "News"),
    NavDestination(StarBrookRoutes.WEATHER, "Weather"),
    NavDestination(StarBrookRoutes.SAVED, "Saved"),
    NavDestination(StarBrookRoutes.SETTINGS, "Settings"),
)

/**
 * Bottom navigation — News / Weather / Saved / Settings, the four
 * tabs of the app. Gold marks the current tab; the indicator is a
 * quiet surface pill, never a loud block. Tapping a tab is the only
 * way between top-level screens; every tab always works.
 */
@Composable
fun StarBrookBottomNav(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier,
        containerColor = StarBrookColors.surface,
    ) {
        for (destination in destinations) {
            val selected = currentRoute == destination.route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(destination.route) },
                icon = { StarBrookNavIcon(destination.route, selected) },
                label = {
                    Text(
                        text = destination.label,
                        style = StarBrookType.caption,
                        color = if (selected) StarBrookColors.gold else StarBrookColors.textSecondary,
                        maxLines = 1,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = StarBrookColors.gold,
                    unselectedIconColor = StarBrookColors.textSecondary,
                    selectedTextColor = StarBrookColors.gold,
                    unselectedTextColor = StarBrookColors.textSecondary,
                    indicatorColor = StarBrookColors.surfaceVariant,
                ),
            )
        }
    }
}
