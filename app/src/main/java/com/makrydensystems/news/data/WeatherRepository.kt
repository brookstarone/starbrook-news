package com.makrydensystems.news.data

import com.makrydensystems.news.data.model.CurrentConditions
import com.makrydensystems.news.data.model.DailyForecast
import com.makrydensystems.news.data.model.HourlyPeriod
import com.makrydensystems.news.data.model.WeatherAlert
import com.makrydensystems.news.data.model.RadarFrame
import com.makrydensystems.news.data.model.WeatherBundle
import com.makrydensystems.news.data.model.ZipLocation
import com.makrydensystems.news.data.di.PushModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

// ---------------------------------------------------------------------------
// Weather — NWS ONLY (Matthew's word: NWS is the standard).
// All endpoints verified live 2026-11-19 ~12:51–12:56 CST. NWS requires a
// User-Agent with contact info; the AppContainer interceptor supplies the
// generic app UA, and NWS calls override it per-request below.
// ---------------------------------------------------------------------------
private const val NWS_USER_AGENT = "StarBrookNews/1.0 (makrydensystems.com)"
private const val ZIPCODE_USER_AGENT = "StarBrookNews/1.0 (Android)"
// Notification-toggle polling (PushModule): NWS alerts re-check interval
// while the Weather screen's alerts flow is collected (app open on
// Weather). OFF suspends the loop on the gate — zero network until the
// toggle comes back ON.
private const val ALERTS_POLL_MS = 10L * 60 * 1000 // NWS alerts every 10 min

interface WeatherRepository {
    suspend fun geocodeZip(zip: String): ZipLocation
    suspend fun currentAndForecast(lat: Double, lon: Double): WeatherBundle
    suspend fun alerts(lat: Double, lon: Double): List<WeatherAlert>
    // Live alerts feed for the Weather screen: emits the current alerts,
    // then re-checks NWS on ALERTS_POLL_MS while collected AND the
    // weather-alerts toggle is ON. Emits only when the alert list
    // actually changed. A failed check keeps the last list (never an
    // error surface); OFF suspends with zero network.
    fun alertsFlow(lat: Double, lon: Double): Flow<List<WeatherAlert>>
    suspend fun radarFrames(): List<RadarFrame>
    fun radarTileUrl(frame: RadarFrame, z: Int, x: Int, y: Int): String
    suspend fun weatherVideoExists(zip: String, date: String, slot: String): Boolean
}

// --- Wire DTOs (private; nullable everywhere — NWS omits fields freely) ---

/** api.zippopotam.us — verified: {"places":[{"latitude":"35.4726","longitude":"-97.5199","place name":"Oklahoma City","state abbreviation":"OK",...}]} (lat/lon are STRINGS). 404 body is {}. */
@Serializable
private data class ZipResponse(val places: List<ZipPlace> = emptyList())

@Serializable
private data class ZipPlace(
    val latitude: String = "",
    val longitude: String = "",
    @SerialName("place name") val placeName: String = "",
    @SerialName("state abbreviation") val stateAbbreviation: String = "",
)

@Serializable
private data class PointsResponse(val properties: PointsProperties? = null)

@Serializable
private data class PointsProperties(
    val forecast: String? = null,
    val forecastHourly: String? = null,
)

@Serializable
private data class ForecastResponse(val properties: ForecastProperties? = null)

@Serializable
private data class ForecastProperties(val periods: List<NwsPeriod> = emptyList())

/**
 * One NWS forecast/hourly period. Verified live for grid OUN (OKC):
 * daily periods carry name/startTime/endTime/isDaytime/temperature/
 * temperatureUnit/windSpeed ("10 mph")/windDirection/icon/shortForecast/
 * detailedForecast/probabilityOfPrecipitation; hourly adds dewpoint +
 * relativeHumidity {"value": 67}. temperatureTrend is routinely null.
 * probabilityOfPrecipitation.value is 0-100 (percent), or null when NWS
 * omits it. quantitativePrecipitation is routinely null — PoP is the
 * rain signal, not inches.
 */
@Serializable
private data class NwsPeriod(
    val startTime: String = "",
    val isDaytime: Boolean = false,
    val temperature: Int? = null,
    val windSpeed: String? = null, // e.g. "10 mph", "5 to 10 mph"
    val windDirection: String? = null,
    val shortForecast: String? = null,
    val icon: String? = null,
    val relativeHumidity: NwsQuantity? = null,
    val probabilityOfPrecipitation: NwsQuantity? = null,
)

@Serializable
private data class NwsQuantity(val value: Double? = null)

/** api.weather.gov/alerts — verified live: 0 active features at the OKC test point, so alert field parsing is structural (standard NWS properties). */
@Serializable
private data class AlertsResponse(val features: List<AlertFeature> = emptyList())

@Serializable
private data class AlertFeature(val properties: AlertProperties? = null)

@Serializable
private data class AlertProperties(
    val headline: String? = null,
    val severity: String? = null,
    val event: String? = null,
    val description: String? = null,
    val expires: String? = null,
)

/**
 * RainViewer — verified live: {"radar":{"past":[{"time":1795107000,
 * "path":"/v2/radar/6283c685342c"}, ...]}}. Frames are keyed by a path
 * HASH, not by epoch time: a tile built from the epoch returns HTTP 410,
 * a tile built from host + frame path returns HTTP 200 (verified
 * 2026-11-19 by direct HEAD). radarFrames() returns the frames with
 * both; radarTileUrl() builds host + path + "/256/{z}/{x}/{y}/2/1_1.png".
 */
@Serializable
private data class RainViewerResponse(val radar: RainViewerRadar? = null)

@Serializable
private data class RainViewerRadar(val past: List<RainViewerFrame> = emptyList())

@Serializable
private data class RainViewerFrame(
    val time: Long = 0L,
    val path: String = "",
)

class WeatherRepositoryImpl(
    private val client: OkHttpClient,
    // Notification toggle (SettingsStore, DataStore-backed). Nullable so
    // v1 still compiles before AppContainer passes it in — until then the
    // PushModule in-memory gate (mirrored from the store by
    // SettingsScreen) is the fallback. Either way the gate default
    // matches the store: weather alerts off.
    private val settings: SettingsStore? = null,
) : WeatherRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    override suspend fun geocodeZip(zip: String): ZipLocation =
        withContext(Dispatchers.IO) {
            val clean = zip.trim()
            require(clean.matches(Regex("\\d{5}"))) {
                "Enter a 5-digit US ZIP code."
            }
            val request = Request.Builder()
                .url("https://api.zippopotam.us/us/$clean")
                .header("User-Agent", ZIPCODE_USER_AGENT)
                .build()
            val text = client.newCall(request).execute().use { response ->
                if (response.code == 404) {
                    throw IllegalArgumentException("That ZIP code could not be found.")
                }
                if (!response.isSuccessful) {
                    throw IOException("ZIP lookup failed (HTTP ${response.code}).")
                }
                response.body?.string() ?: throw IOException("Empty response from ZIP lookup.")
            }
            val place = json.decodeFromString<ZipResponse>(text).places.firstOrNull()
                ?: throw IllegalArgumentException("That ZIP code could not be found.")
            val lat = place.latitude.toDoubleOrNull()
            val lon = place.longitude.toDoubleOrNull()
            if (lat == null || lon == null) {
                throw IOException("Could not read coordinates for ZIP $clean.")
            }
            // "Oklahoma City, OK" — the header shows the place, never a
            // bare ZIP. Falls back to the ZIP itself if Zippopotam ever
            // omits the name.
            val placeName = listOf(place.placeName, place.stateAbbreviation)
                .filter { it.isNotBlank() }
                .joinToString(", ")
                .ifBlank { clean }
            ZipLocation(lat = lat, lon = lon, placeName = placeName)
        }

    override suspend fun currentAndForecast(lat: Double, lon: Double): WeatherBundle =
        withContext(Dispatchers.IO) {
            coroutineScope {
                val pointsText = getNwsText("https://api.weather.gov/points/$lat,$lon")
                val props = json.decodeFromString<PointsResponse>(pointsText).properties
                    ?: throw IOException("Weather service returned no forecast links.")
                val forecastUrl = props.forecast
                    ?: throw IOException("Weather service returned no daily forecast link.")
                val hourlyUrl = props.forecastHourly
                    ?: throw IOException("Weather service returned no hourly forecast link.")
                val dailyDeferred = async {
                    json.decodeFromString<ForecastResponse>(getNwsText(forecastUrl))
                }
                val hourlyDeferred = async {
                    json.decodeFromString<ForecastResponse>(getNwsText(hourlyUrl))
                }
                val alertsDeferred = async { alerts(lat, lon) }
                val dailyPeriods = dailyDeferred.await().properties?.periods.orEmpty()
                val hourlyPeriods = hourlyDeferred.await().properties?.periods.orEmpty()
                // Alerts are isolated: a failed alerts fetch yields "couldn't
                // check", never a dead screen — the forecast still renders.
                val alertsResult = try {
                    alertsDeferred.await()
                } catch (e: Exception) {
                    null
                }
                WeatherBundle(
                    current = currentFromHourly(hourlyPeriods),
                    hourly = hourlyPeriods
                        .filter { it.temperature != null }
                        .take(24)
                        .map {
                            HourlyPeriod(
                                startTime = it.startTime,
                                temperatureF = it.temperature!!,
                                shortForecast = it.shortForecast ?: "",
                                isDaytime = it.isDaytime,
                                precipitationChancePct = it.probabilityOfPrecipitation?.value?.toInt(),
                            )
                        },
                    daily = dailyFromPeriods(dailyPeriods),
                    alerts = alertsResult.orEmpty(),
                    alertsAvailable = alertsResult != null,
                )
            }
        }

    override suspend fun alerts(lat: Double, lon: Double): List<WeatherAlert> =
        withContext(Dispatchers.IO) {
            val text = getNwsText(
                "https://api.weather.gov/alerts/active?point=$lat,$lon",
            )
            json.decodeFromString<AlertsResponse>(text).features.mapNotNull { feature ->
                val p = feature.properties ?: return@mapNotNull null
                WeatherAlert(
                    headline = p.headline ?: p.event ?: "Weather alert",
                    severity = p.severity ?: "Unknown",
                    event = p.event ?: "",
                    description = p.description ?: "",
                    expires = p.expires,
                )
            }
        }

    override fun alertsFlow(lat: Double, lon: Double): Flow<List<WeatherAlert>> = flow {
        val enabled = settings?.notifyWeatherFlow ?: PushModule.weatherAlertsEnabled
        var last: List<WeatherAlert>? = null
        try {
            last = alerts(lat, lon)
            emit(last)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // No alerts yet and the check failed: emit nothing — the
            // Weather screen keeps its own "couldn't check" state from
            // currentAndForecast. The poll loop below retries.
        }
        while (currentCoroutineContext().isActive) {
            if (!enabled.first()) {
                enabled.first { it }
                continue
            }
            delay(ALERTS_POLL_MS)
            if (!enabled.first()) continue
            try {
                val fresh = alerts(lat, lon)
                if (fresh != last) {
                    last = fresh
                    emit(fresh)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Failed poll: keep the last list; next poll retries.
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun radarFrames(): List<RadarFrame> = withContext(Dispatchers.IO) {
        val text = getText(
            "https://api.rainviewer.com/public/weather-maps.json",
            ZIPCODE_USER_AGENT,
        )
        json.decodeFromString<RainViewerResponse>(text).radar?.past.orEmpty()
            .filter { it.time > 0 && it.path.isNotBlank() }
            .map { RadarFrame(time = it.time, path = it.path) }
    }

    override fun radarTileUrl(frame: RadarFrame, z: Int, x: Int, y: Int): String =
        "https://tilecache.rainviewer.com${frame.path}/256/$z/$x/$y/2/1_1.png"

    /**
     * HEAD the per-ZIP weather video. A missing object is NOT an error —
     * Supabase answers HTTP 400 {"statusCode":"404","error":"not_found"}
     * (verified live for midday-73102-2026-11-19, which does not exist
     * yet). Any non-2xx, including no network, is simply "no video".
     * No ZIP = no video; there is no default ZIP anywhere in this layer.
     */
    override suspend fun weatherVideoExists(zip: String, date: String, slot: String): Boolean =
        withContext(Dispatchers.IO) {
            if (zip.isBlank()) return@withContext false
            val url = StarBrookApi.STORAGE_BASE +
                "news-videos/weather/$slot-$zip-$date.mp4"
            val request = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", ZIPCODE_USER_AGENT)
                .build()
            try {
                client.newCall(request).execute().use { it.isSuccessful }
            } catch (e: Exception) {
                false
            }
        }

    // --- Helpers ---

    /** Current conditions come from the first hourly period with a temperature. */
    private fun currentFromHourly(periods: List<NwsPeriod>): CurrentConditions {
        val first = periods.firstOrNull { it.temperature != null }
            ?: throw IOException("Weather service returned no current conditions.")
        return CurrentConditions(
            temperatureF = first.temperature!!,
            // NWS forecast/hourly periods carry no feels-like value.
            feelsLikeF = null,
            condition = first.shortForecast ?: "",
            shortForecast = first.shortForecast ?: "",
            windMph = parseWindMph(first.windSpeed),
            windDirection = first.windDirection,
            humidityPct = first.relativeHumidity?.value?.toInt(),
            iconUrl = first.icon,
            precipitationChancePct = first.probabilityOfPrecipitation?.value?.toInt(),
        )
    }

    /** NWS daily periods arrive as day/night pairs; pair them per calendar date. */
    private fun dailyFromPeriods(periods: List<NwsPeriod>): List<DailyForecast> {
        val byDate = periods.groupBy { it.startTime.take(10) }.toSortedMap()
        return byDate.entries.take(7).map { (date, dayPeriods) ->
            val day = dayPeriods.firstOrNull { it.isDaytime }
            val night = dayPeriods.firstOrNull { !it.isDaytime }
            DailyForecast(
                date = date,
                highF = day?.temperature,
                lowF = night?.temperature,
                shortForecast = day?.shortForecast ?: night?.shortForecast ?: "",
                iconUrl = day?.icon ?: night?.icon,
                precipitationChancePct = listOfNotNull(
                    day?.probabilityOfPrecipitation?.value?.toInt(),
                    night?.probabilityOfPrecipitation?.value?.toInt(),
                ).maxOrNull(),
            )
        }
    }

    /** "10 mph" -> 10; "5 to 10 mph" -> 5 (first number wins); unparsable -> null. */
    private fun parseWindMph(windSpeed: String?): Int? {
        if (windSpeed.isNullOrBlank()) return null
        return Regex("\\d+").find(windSpeed)?.value?.toIntOrNull()
    }

    private fun getNwsText(url: String): String = getText(url, NWS_USER_AGENT)

    private fun getText(url: String, userAgent: String): String {
        val request = Request.Builder().url(url).header("User-Agent", userAgent).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            return response.body?.string() ?: throw IOException("Empty response for $url")
        }
    }
}
