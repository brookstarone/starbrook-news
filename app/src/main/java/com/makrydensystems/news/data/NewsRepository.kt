package com.makrydensystems.news.data

import com.makrydensystems.news.data.local.AppDatabase
import com.makrydensystems.news.data.model.BriefingVideo
import com.makrydensystems.news.data.model.Breaking
import com.makrydensystems.news.data.model.LiveItem
import com.makrydensystems.news.data.model.SavedStoryEntity
import com.makrydensystems.news.data.model.Story
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

// ---------------------------------------------------------------------------
// Public endpoints (all verified live 2026-11-19, ~12:51–12:55 CST).
// Base URLs live in StarBrookApi — never duplicated here.
// ---------------------------------------------------------------------------
private const val EDITIONS_BASE = "news-editions/"
private const val NEWS_USER_AGENT = "StarBrookNews/1.0 (Android)"
private const val BREAKING_FRESH_MS = 6L * 60 * 60 * 1000 // web rule: 6 hours from detectedAt
private const val LATEST_FRESH_MS = 2L * 60 * 60 * 1000 // web rule: 2 hours from builtAt

interface NewsRepository {
    fun editionsFlow(edition: String): Flow<List<Story>> // "morning" | "evening"
    fun breakingFlow(): Flow<Breaking?>
    fun liveNowFlow(): Flow<List<LiveItem>>
    fun videosFlow(): Flow<List<BriefingVideo>>
    fun searchStories(query: String): Flow<List<Story>>
    fun savedFlow(): Flow<List<Story>>
    suspend fun saveStory(story: Story)
    suspend fun unsaveStory(link: String)
    fun isSavedFlow(link: String): Flow<Boolean>
    suspend fun refreshAll()
}

// --- Wire DTOs (private; only the shapes the live files actually have) ---

/** edition-morning/evening.json — verified: {"stories":[{...15 stories...}]} */
@Serializable
private data class EditionResponse(val stories: List<Story> = emptyList())

/**
 * breaking.json — verified live as {"breaking":null,
 * "detectedAt":"2026-11-19T18:55:01.422Z"}. The non-null object shape
 * (compact t/s/a/n/u) is confirmed from the live web page JS, not from a
 * live non-null file — see Models.kt.
 */
@Serializable
private data class BreakingResponse(
    val breaking: Breaking? = null,
    val detectedAt: String? = null,
)

/** latest.json — verified: {"stories":[...8...], "builtAt":"2026-11-19T18:55:01.422Z"} */
@Serializable
private data class LatestResponse(
    val stories: List<LiveItem> = emptyList(),
    val builtAt: String? = null,
)

/**
 * news-briefing-manifest.json — verified: {"latest":"2026-11-19-morning",
 * "videos":[...]}. Breaking flashes carry "file"/"poster" (bare bucket
 * filenames); daily morning/evening entries carry NEITHER, so the repo
 * derives them web-parity (see mapToVideo below).
 */
@Serializable
private data class ManifestResponse(
    val latest: String? = null,
    val videos: List<ManifestVideo> = emptyList(),
)

@Serializable
private data class ManifestVideo(
    val id: String,
    val date: String,
    val edition: String = "",
    @SerialName("breaking") val isBreaking: Boolean = false,
    val title: String = "",
    val duration: String = "",
    val file: String? = null,
    val poster: String? = null,
)

class NewsRepositoryImpl(
    private val client: OkHttpClient,
    private val db: AppDatabase,
) : NewsRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    // --- Cache-first flows (INSTANT STANDARD) ---
    // Every flow emits the Room cache first (instant render, never a blank
    // load), then refreshes quietly in the background. A failed refresh
    // with a warm cache is swallowed — the UI keeps showing real data. A
    // failed refresh with an EMPTY cache throws IOException so the UI can
    // show ErrorState + retry. Nothing here ever returns fake data.

    override fun editionsFlow(edition: String): Flow<List<Story>> = flow {
        val cached = db.cachedStoryDao().storiesForEdition(edition)
        if (cached.isNotEmpty()) emit(cached)
        try {
            refreshEdition(edition)
            emit(db.cachedStoryDao().storiesForEdition(edition))
        } catch (e: Exception) {
            if (cached.isEmpty()) {
                throw IOException(
                    "Could not load the $edition edition. Check your connection and try again.",
                    e,
                )
            }
            // Warm cache: keep showing it; the next collect / refreshAll retries.
        }
    }.flowOn(Dispatchers.IO)

    override fun breakingFlow(): Flow<Breaking?> = flow {
        emit(db.breakingDao().getBreaking())
        try {
            emit(refreshBreaking())
        } catch (e: Exception) {
            // Breaking null is a valid state (strip hidden); a failed refresh
            // just keeps whatever the cache had. Never an error surface here.
        }
    }.flowOn(Dispatchers.IO)

    override fun liveNowFlow(): Flow<List<LiveItem>> = flow {
        val cached = db.liveItemDao().getAll()
        if (cached.isNotEmpty()) emit(cached)
        try {
            refreshLive()
            emit(db.liveItemDao().getAll())
        } catch (e: Exception) {
            if (cached.isEmpty()) {
                throw IOException(
                    "Could not load live updates. Check your connection and try again.",
                    e,
                )
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun videosFlow(): Flow<List<BriefingVideo>> = flow {
        val cached = db.videoDao().getAll()
        if (cached.isNotEmpty()) emit(cached)
        try {
            refreshVideos()
            emit(db.videoDao().getAll())
        } catch (e: Exception) {
            if (cached.isEmpty()) {
                throw IOException(
                    "Could not load videos. Check your connection and try again.",
                    e,
                )
            }
        }
    }.flowOn(Dispatchers.IO)

    // --- Search (local Room only — no network search endpoint exists) ---

    override fun searchStories(query: String): Flow<List<Story>> {
        if (query.isBlank()) return flowOf(emptyList())
        val escaped = query.trim()
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return db.cachedStoryDao().searchStories("%$escaped%")
    }

    // --- Saved / Shelf (local Room only in v1) ---

    override fun savedFlow(): Flow<List<Story>> =
        db.savedStoryDao().savedStories().map { rows -> rows.map { it.toStory() } }

    override suspend fun saveStory(story: Story) {
        withContext(Dispatchers.IO) {
            db.savedStoryDao().insert(SavedStoryEntity.fromStory(story))
        }
    }

    override suspend fun unsaveStory(link: String) {
        withContext(Dispatchers.IO) { db.savedStoryDao().deleteByLink(link) }
    }

    override fun isSavedFlow(link: String): Flow<Boolean> =
        db.savedStoryDao().isSaved(link)

    // --- Manual refresh (pull-to-refresh / ErrorState retry) ---

    override suspend fun refreshAll() {
        withContext(Dispatchers.IO) {
            val failures = mutableListOf<String>()
            try {
                refreshEdition("morning")
            } catch (e: Exception) {
                failures += "morning edition (${e.message})"
            }
            try {
                refreshEdition("evening")
            } catch (e: Exception) {
                failures += "evening edition (${e.message})"
            }
            try {
                refreshBreaking()
            } catch (e: Exception) {
                failures += "breaking (${e.message})"
            }
            try {
                refreshLive()
            } catch (e: Exception) {
                failures += "live now (${e.message})"
            }
            try {
                refreshVideos()
            } catch (e: Exception) {
                failures += "videos (${e.message})"
            }
            if (failures.isNotEmpty()) {
                throw IOException("Refresh failed: " + failures.joinToString("; "))
            }
        }
    }

    // --- Network fetches + cache writes ---

    private fun getText(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", NEWS_USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            return response.body?.string() ?: throw IOException("Empty response for $url")
        }
    }

    private suspend fun refreshEdition(edition: String) {
        val url = StarBrookApi.STORAGE_BASE + EDITIONS_BASE + "edition-$edition.json"
        val stories = json.decodeFromString<EditionResponse>(getText(url)).stories
        val dao = db.cachedStoryDao()
        dao.clearEdition(edition)
        dao.insertAll(stories.map { it.copy(edition = edition) })
    }

    /** Returns the live breaking story, or null when none qualifies. */
    private suspend fun refreshBreaking(): Breaking? {
        val url = StarBrookApi.STORAGE_BASE + EDITIONS_BASE + "breaking.json"
        val parsed = json.decodeFromString<BreakingResponse>(getText(url))
        val dao = db.breakingDao()
        val breaking = parsed.breaking
        val detectedAtMs = parsed.detectedAt?.let { parseInstantMsOrNull(it) }
        // Web rule (news_mix.html): live only if detectedAt is within 6h.
        // A missing/unparseable detectedAt means "not fresh" — strip hides.
        val fresh = breaking != null && detectedAtMs != null &&
            System.currentTimeMillis() - detectedAtMs <= BREAKING_FRESH_MS
        dao.clear()
        if (fresh) dao.insert(breaking!!)
        return if (fresh) breaking else null
    }

    private suspend fun refreshLive() {
        val url = StarBrookApi.STORAGE_BASE + EDITIONS_BASE + "latest.json"
        val parsed = json.decodeFromString<LatestResponse>(getText(url))
        val dao = db.liveItemDao()
        val builtAtMs = parsed.builtAt?.let { parseInstantMsOrNull(it) }
        // Web rule: a latest.json older than 2h hides both surfaces.
        // No builtAt at all = accept the stories (older files predate it).
        val stale = builtAtMs != null &&
            System.currentTimeMillis() - builtAtMs > LATEST_FRESH_MS
        dao.clearAll()
        if (!stale) dao.insertAll(parsed.stories)
    }

    private suspend fun refreshVideos() {
        val url = StarBrookApi.STORAGE_BASE + "news-videos/news-briefing-manifest.json"
        val videos = json.decodeFromString<ManifestResponse>(getText(url)).videos
            .map { it.toBriefingVideo() }
        val dao = db.videoDao()
        dao.clearAll()
        dao.insertAll(videos)
    }

    /**
     * Daily morning/evening entries have no "file"/"poster" in the manifest.
     * Web parity (news_mix.html videoUrl/videoPoster): the daily file is
     * "news-briefing-<date>.mp4" and its poster "news-briefing-<date>.jpg"
     * (both verified to exist for 2026-11-19 and 2026-11-18). Breaking
     * flashes use their explicit manifest filenames. Morning and evening
     * entries for the same date resolve to the same daily file — that is
     * what the live web page does; if the pipeline later adds explicit
     * "file" fields to daily entries, those win automatically.
     */
    private fun ManifestVideo.toBriefingVideo(): BriefingVideo = BriefingVideo(
        id = id,
        date = date,
        edition = edition,
        isBreaking = isBreaking,
        title = title,
        duration = duration,
        file = file ?: "news-briefing-$date.mp4",
        posterUrl = StarBrookApi.posterUrl(poster ?: "news-briefing-$date.jpg"),
    )

    private fun parseInstantMsOrNull(value: String): Long? = try {
        java.time.Instant.parse(value).toEpochMilli()
    } catch (e: Exception) {
        null
    }
}
