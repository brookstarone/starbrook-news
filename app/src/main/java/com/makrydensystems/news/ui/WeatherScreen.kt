package com.makrydensystems.news.ui

import android.net.Uri
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
import androidx.compose.ui.layout.ContentScale
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
import java.time.ZoneId
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
                    val (lat, lon) = repo.geocodeZip(zip)
                    val bundle = repo.currentAndForecast(lat, lon)
                    val frames = repo.radarFrames()
                    val today = LocalDate.now(ZoneId.of("America/Chicago")).toString()
                    val videoExists = repo.weatherVideoExists(zip, today, "midday")
                    emit(
                        WeatherState.Ready(
                            bundle = bundle,
                            radarFrames = frames,
                            videoExists = videoExists,
                            zip = zip,
                            lat = lat,
                            lon = lon,
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
 * Weather tab (v1). NWS only. No ZIP = prompt, no video, no default
 * city — ever. Radar is native RainViewer tiles (3x3 grid, zoom 6)
 * with a play/pause loop; the per-ZIP midday video plays natively.
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
                        text = "Weather for ${s.zip}",
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
                // Current conditions hero.
                Text(
                    text = "${current.temperatureF}°",
                    style = StarBrookType.display,
                    color = StarBrookColors.textPrimary,
                )
                Text(
                    text = current.condition,
                    style = StarBrookType.title,
                    color = StarBrookColors.textPrimary,
                )
                Text(
                    text = buildString {
                        current.feelsLikeF?.let { append("Feels like $it°  ·  ") }
                        current.windMph?.let { append("Wind ${current.windDirection.orEmpty()} $it mph  ·  ") }
                        current.humidityPct?.let { append("Humidity $it%") }
                    },
                    style = StarBrookType.bodySmall,
                    color = StarBrookColors.textSecondary,
                )
                // Alerts.
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
                }
                // Hourly (next 12).
                SectionHeader(title = "Hourly")
                for (hour in bundle.hourly.take(12)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = hour.startTime,
                            style = StarBrookType.bodySmall,
                            color = StarBrookColors.textPrimary,
                        )
                        Text(
                            text = "${hour.temperatureF}° · ${hour.shortForecast}",
                            style = StarBrookType.bodySmall,
                            color = StarBrookColors.textSecondary,
                        )
                    }
                }
                // Daily.
                SectionHeader(title = "7-day forecast")
                for (day in bundle.daily) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = day.date,
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
    val zoom = 6
    val (cx, cy) = tileXY(lat, lon, zoom)
    val frame = loopFrames[frameIndex]
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            Column {
                for (dy in -1..1) {
                    Row {
                        for (dx in -1..1) {
                            AsyncImage(
                                model = repo.radarTileUrl(frame, zoom, cx + dx, cy + dy),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f),
                            )
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
    }
}
