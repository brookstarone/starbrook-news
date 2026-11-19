package com.makrydensystems.news.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.makrydensystems.news.data.SettingsStore
import com.makrydensystems.news.data.StarBrookApi
import com.makrydensystems.news.data.WeatherRepository
import com.makrydensystems.news.data.di.AppContainer
import com.makrydensystems.news.data.model.RadarFrame
import com.makrydensystems.news.data.model.WeatherBundle
import com.makrydensystems.news.ui.components.EmptyState
import com.makrydensystems.news.ui.components.ErrorState
import com.makrydensystems.news.ui.components.SectionHeader
import com.makrydensystems.news.ui.components.SkeletonList
import com.makrydensystems.news.ui.theme.StarBrookColors
import com.makrydensystems.news.ui.theme.StarBrookShapes
import com.makrydensystems.news.ui.theme.StarBrookType
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private sealed interface WeatherState {
    data object NeedZip : WeatherState
    data object Loading : WeatherState
    data class Error(val message: String) : WeatherState
    data class Ready(
        val bundle: WeatherBundle,
        val radarFrames: List<RadarFrame>,
        val videoExists: Boolean,
        val zip: String,
        val placeName: String,
        val lat: Double,
        val lon: Double,
    ) : WeatherState
}

private class WeatherViewModel(
    private val repo: WeatherRepository,
    private val settings: SettingsStore,
) : ViewModel() {
    val state: kotlinx.coroutines.flow.StateFlow<WeatherState> =
        settings.zipFlow.flatMapLatest { zip ->
            if (zip.isNullOrBlank()) {
                flowOf<WeatherState>(WeatherState.NeedZip)
            } else {
                flow {
                    emit(WeatherState.Loading)
                    val loc = repo.geocodeZip(zip)
                    val bundle = repo.currentAndForecast(loc.lat, loc.lon)
                    val frames = repo.radarFrames()
                    val today = LocalDate.now(ZoneId.of("America/Chicago")).toString()
                    val videoExists = repo.weatherVideoExists(zip, today, "midday")
                    emit(
                        WeatherState.Ready(
                            bundle = bundle,
                            radarFrames = frames,
                            videoExists = videoExists,
                            zip = zip,
                            placeName = loc.placeName,
                            lat = loc.lat,
                            lon = loc.lon,
                        ),
                    )
                }.catch {
                    emit(
                        WeatherState.Error(
                            "Couldn't load the weather for that ZIP — check the ZIP and your connection, then try again.",
                        ),
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeatherState.Loading)

    fun setZip(zip: String) {
        viewModelScope.launch { settings.setZip(zip) }
    }

    fun clearZip() {
        viewModelScope.launch { settings.setZip(null) }
    }
}

/** Slippy-map tile for a lat/lon at zoom z. */
private fun tileXY(lat: Double, lon: Double, z: Int): Pair<Int, Int> {
    val n = 1 shl z
    val x = ((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
    val latRad = Math.toRadians(lat)
    val y = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n).toInt()
        .coerceIn(0, n - 1)
    return x to y
}

private fun formatRadarTime(epochSeconds: Long): String {
    val ms = epochSeconds * 1_000L
    return SimpleDateFormat("h:mm a", Locale.US).format(Date(ms))
}

/**
 * "2026-11-19T13:00:00-06:00" (NWS hourly startTime) -> "1 PM".
 * Unparsable input falls back to the raw string — never crashes.
 */
private fun formatHourLabel(iso: String): String {
    return try {
        OffsetDateTime.parse(iso)
            .format(DateTimeFormatter.ofPattern("h a", Locale.US))
    } catch (e: Exception) {
        iso
    }
}

/**
 * "2026-11-19" (daily forecast date) -> "Thu, Nov 19".
 * Unparsable input falls back to the raw string — never crashes.
 */
private fun formatDayLabel(date: String): String {
    return try {
        LocalDate.parse(date)
            .format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US))
    } catch (e: Exception) {
        date
    }
}

/** "68° / 53°" for today, or "—" when NWS gives neither. */
private fun todayHighLow(bundle: WeatherBundle): String {
    val today = bundle.daily.firstOrNull() ?: return "—"
    return buildString {
        today.highF?.let { append("$it°") }
        if (today.highF != null && today.lowF != null) append(" / ")
        today.lowF?.let { append("$it°") }
    }.ifEmpty { "—" }
}

/**
 * Expected-rain line from the NWS hourly precipitation chances (next
 * 12 hours). Honest about all three states: no data, no rain, rain.
 */
private fun expectedRainText(bundle: WeatherBundle): String {
    val next = bundle.hourly.take(12)
    val chances = next.mapNotNull { it.precipitationChancePct }
    if (chances.isEmpty()) return "Rain outlook unavailable right now."
    val max = chances.max()
    if (max <= 0) return "No rain expected in the next 12 hours."
    val peak = next.firstOrNull { it.precipitationChancePct == max }
    val whenText = peak?.let { " around ${formatHourLabel(it.startTime)}" } ?: ""
    return "Highest chance of rain: $max%$whenText."
}

/** One cell of the hero stats grid: small label over the value. */
@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = StarBrookType.caption,
            color = StarBrookColors.textSecondary,
        )
        Text(
            text = value,
            style = StarBrookType.body,
            color = StarBrookColors.textPrimary,
        )
    }
}
/** OpenStreetMap base-map tile — the map the RainViewer radar sits on. */
private fun baseMapTileUrl(z: Int, x: Int, y: Int): String =
    "https://tile.openstreetmap.org/$z/$x/$y.png"

/**
 * Weather tab (v1). NWS only. No ZIP = prompt, no video, no default
 * city — ever. Radar is native RainViewer tiles (3x3 grid, zoom 7) over
 * an OpenStreetMap base map, with a play/pause loop; the per-ZIP midday
 * video plays natively.
 */
@Composable
fun WeatherScreen(
    container: AppContainer,
    onPlayVideo: (url: String, title: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: WeatherViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                WeatherViewModel(container.weatherRepository, container.settingsStore)
            }
        },
    )
    val state by vm.state.collectAsState()
    var zipInput by remember { mutableStateOf("") }
    var zipError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (val s = state) {
            is WeatherState.NeedZip -> {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Your weather starts with your ZIP code",
                    style = StarBrookType.title,
                    color = StarBrookColors.textPrimary,
                )
                Text(
                    text = "Enter the ZIP you want the forecast for. The same ZIP drives your local news.",
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
            }
            is WeatherState.Loading -> SkeletonList(count = 4)
            is WeatherState.Error -> ErrorState(
                message = s.message,
                onRetry = { vm.clearZip() },
            )
            is WeatherState.Ready -> {
                val bundle = s.bundle
                val current = bundle.current
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Weather for ${s.placeName}",
                        style = StarBrookType.title,
                        color = StarBrookColors.textPrimary,
                    )
                    Text(
                        text = "Change ZIP",
                        style = StarBrookType.label,
                        color = StarBrookColors.gold,
                        modifier = Modifier.clickable { vm.clearZip() },
                    )
                }
                // Current conditions hero card (mirrors the web weather
                // card: big temp + condition, then a stats grid). NWS
                // hourly periods carry no feels-like value, so the grid
                // shows only stats NWS actually provides — never a
                // placeholder number. "—" means NWS omitted the value.
                Surface(
                    color = StarBrookColors.surface,
                    shape = StarBrookShapes.card,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "${current.temperatureF}°",
                                style = StarBrookType.display,
                                color = StarBrookColors.textPrimary,
                            )
                            Column {
                                Text(
                                    text = current.condition,
                                    style = StarBrookType.title,
                                    color = StarBrookColors.textPrimary,
                                )
                                Text(
                                    text = s.placeName,
                                    style = StarBrookType.bodySmall,
                                    color = StarBrookColors.textSecondary,
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCell(
                                label = "Wind",
                                value = current.windMph?.let {
                                    "${current.windDirection.orEmpty()} $it mph".trim()
                                } ?: "—",
                                modifier = Modifier.weight(1f),
                            )
                            StatCell(
                                label = "Humidity",
                                value = current.humidityPct?.let { "$it%" } ?: "—",
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCell(
                                label = "Rain chance",
                                value = current.precipitationChancePct?.let { "$it%" } ?: "—",
                                modifier = Modifier.weight(1f),
                            )
                            StatCell(
                                label = "High / Low",
                                value = todayHighLow(bundle),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                // Expected rain — NWS precipitation chances, next 12 hours.
                SectionHeader(title = "Expected rain")
                Text(
                    text = expectedRainText(bundle),
                    style = StarBrookType.bodySmall,
                    color = StarBrookColors.textSecondary,
                )
                // Alerts — a failed alerts fetch must not kill the screen,
                // and no alerts is a real state, not a blank gap.
                SectionHeader(title = "Alerts")
                if (bundle.alerts.isNotEmpty()) {
                    for (alert in bundle.alerts) {
                        val severe = alert.severity.equals("Severe", ignoreCase = true) ||
                            alert.severity.equals("Extreme", ignoreCase = true)
                        val watch = alert.event.contains("Watch", ignoreCase = true)
                        Surface(
                            color = when {
                                severe -> StarBrookColors.liveRed
                                watch -> StarBrookColors.gold
                                else -> StarBrookColors.accentPurple
                            },
                            shape = StarBrookShapes.card,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = alert.headline,
                                style = StarBrookType.bodySmall,
                                color = if (watch) StarBrookColors.onGold else StarBrookColors.textPrimary,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                } else if (bundle.alertsAvailable) {
                    Text(
                        text = "No active weather alerts.",
                        style = StarBrookType.bodySmall,
                        color = StarBrookColors.textSecondary,
                    )
                } else {
                    Text(
                        text = "Couldn't check for weather alerts right now — the forecast above is still live.",
                        style = StarBrookType.bodySmall,
                        color = StarBrookColors.textSecondary,
                    )
                }
                // Per-ZIP midday video.
                if (s.videoExists) {
                    val today = LocalDate.now(ZoneId.of("America/Chicago")).toString()
                    val videoUrl = StarBrookApi.STORAGE_BASE +
                        "news-videos/weather/midday-${s.zip}-$today.mp4"
                    Surface(
                        color = StarBrookColors.surface,
                        shape = StarBrookShapes.card,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onPlayVideo(
                                    videoUrl,
                                    "StarBrook Weather Midday — ${s.zip}",
                                )
                            },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Midday video for ${s.zip}",
                                style = StarBrookType.body,
                                color = StarBrookColors.textPrimary,
                            )
                            Text(
                                text = "▶ Watch",
                                style = StarBrookType.label,
                                color = StarBrookColors.gold,
                            )
                        }
                    }
                } else {
                    // Honest empty state: the midday video posts around
                    // midday — before that there is nothing to play yet,
                    // so the card says so instead of vanishing.
                    Surface(
                        color = StarBrookColors.surface,
                        shape = StarBrookShapes.card,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "Midday video for ${s.zip}",
                                style = StarBrookType.body,
                                color = StarBrookColors.textPrimary,
                            )
                            Text(
                                text = "Not up yet — the midday video posts around midday. Check back then.",
                                style = StarBrookType.bodySmall,
                                color = StarBrookColors.textSecondary,
                            )
                        }
                    }
                }
                // Hourly (next 12). The right text takes the remaining
                // width and wraps inside it, so it can never run into
                // the hour label — the 12.dp gap always holds.
                SectionHeader(title = "Hourly")
                for (hour in bundle.hourly.take(12)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = formatHourLabel(hour.startTime),
                            style = StarBrookType.bodySmall,
                            color = StarBrookColors.textPrimary,
                        )
                        Text(
                            text = "${hour.temperatureF}° · ${hour.shortForecast}",
                            style = StarBrookType.bodySmall,
                            color = StarBrookColors.textSecondary,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                // Daily.
                SectionHeader(title = "7-day forecast")
                for (day in bundle.daily) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = formatDayLabel(day.date),
                            style = StarBrookType.bodySmall,
                            color = StarBrookColors.textPrimary,
                        )
                        Text(
                            text = buildString {
                                day.highF?.let { append("$it° / ") }
                                day.lowF?.let { append("$it°") }
                                append(" · ${day.shortForecast}")
                            },
                            style = StarBrookType.bodySmall,
                            color = StarBrookColors.textSecondary,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                // Radar — native tiles, play/pause loop.
                SectionHeader(title = "Radar")
                RadarView(
                    repo = container.weatherRepository,
                    frames = s.radarFrames,
                    lat = s.lat,
                    lon = s.lon,
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun RadarView(
    repo: WeatherRepository,
    frames: List<RadarFrame>,
    lat: Double,
    lon: Double,
) {
    if (frames.isEmpty()) {
        EmptyState(
            title = "Radar isn't available right now",
            body = "The forecast above is still live — radar comes back on its own.",
        )
        return
    }
    val loopFrames = remember(frames) { frames.takeLast(6) }
    var frameIndex by remember(loopFrames) { mutableStateOf(loopFrames.lastIndex) }
    var playing by remember { mutableStateOf(false) }
    LaunchedEffect(playing, loopFrames) {
        while (playing) {
            delay(700)
            frameIndex = (frameIndex + 1) % loopFrames.size
        }
    }
    // Zoom 7 is the closest RainViewer serves: zoom 8+ returns its
    // "Zoom Level Not Supported" placeholder tile instead of radar
    // (verified live 2026-11-19). Zoom 6 was continental scale, so even
    // real rain rendered as a speck.
    val zoom = 7
    val (cx, cy) = tileXY(lat, lon, zoom)
    val frame = loopFrames[frameIndex]
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(StarBrookColors.surface),
            contentAlignment = Alignment.Center,
        ) {
            Column {
                for (dy in -1..1) {
                    Row {
                        for (dx in -1..1) {
                            // Base map underneath, radar overlay on top.
                            // RainViewer tiles are precipitation-only
                            // transparent PNGs, so without a base map the
                            // box was bare near-black background with a
                            // few faint specks — that was the dark/faint
                            // radar. Overlay at 0.7 opacity, matching the
                            // live website's radar.
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f),
                            ) {
                                AsyncImage(
                                    model = baseMapTileUrl(zoom, cx + dx, cy + dy),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                AsyncImage(
                                    model = repo.radarTileUrl(frame, zoom, cx + dx, cy + dy),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .alpha(0.7f),
                                )
                            }
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Radar · ${formatRadarTime(frame.time)}",
                style = StarBrookType.caption,
                color = StarBrookColors.textSecondary,
            )
            Text(
                text = if (playing) "❚❚ Pause" else "▶ Play loop",
                style = StarBrookType.label,
                color = StarBrookColors.gold,
                modifier = Modifier.clickable { playing = !playing },
            )
        }
        Text(
            text = "Map © OpenStreetMap contributors · Radar: RainViewer",
            style = StarBrookType.caption,
            color = StarBrookColors.textSecondary,
        )
    }
}
