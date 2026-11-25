package com.makrydensystems.news.data

import com.makrydensystems.news.data.local.AppDatabase
import com.makrydensystems.news.data.model.BriefingVideo
import com.makrydensystems.news.data.model.Breaking
import com.makrydensystems.news.data.model.LiveItem
import com.makrydensystems.news.data.model.SavedStoryEntity
import com.makrydensystems.news.data.model.Story
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

// ---------------------------------------------------------------------------
// Public endpoints (all verified live 2026-11-19, ~12:51–12:55 CST).
// Base URLs live in StarBrookApi — never duplicated here.
// ---------------------------------------------------------------------------
private const val EDITIONS_BASE = "news-editions/"
private const val NEWS_USER_AGENT = "StarBrookNews/1.0 (Android)"
private const val BREAKING_FRESH_MS = 6L * 60 * 60 * 1000 // web rule: 6 hours from detectedAt
private const val LATEST_FRESH_MS = 2L * 60 * 60 * 1000 // web rule: 2 hours from builtAt
private const val EDITION_STALE_MS = 18L * 60 * 60 * 1000 // an edition file older than this counts as "not built yet" for its slot (see isCurrentEdition)
// Background refresh intervals while a Home flow is collected (app
// open on News). R-20 (2026-11-25): these loops run regardless of the
// notification toggles — the toggles drive FCM registration only.
private const val BREAKING_POLL_MS = 5L * 60 * 1000 // breaking.json every 5 min
private const val EDITION_POLL_MS = 15L * 60 * 1000 // edition file every 15 min
private const val LIVE_POLL_MS = 5L * 60 * 1000 // latest.json (Live Now) every 5 min

// Supabase REST + functions bases (Wave 2). These name the same project
// host as StarBrookApi's storage base; Api.kt is owned by another
// agent, so the REST/functions bases live here until that agent
// consolidates them. No keys here: the anon key arrives via the
// constructor (BuildConfig from env, TODO.md §1) and is never a literal.
private const val SUPABASE_REST_BASE = "https://bamrgsjgvakyucyqtwlg.supabase.co/rest/v1/"
private const val STORY_BODY_EXTRACT_URL =
    "https://bamrgsjgvakyucyqtwlg.supabase.co/functions/v1/story-body-extract"
private const val ORIGINAL_READ_BASE = "https://makrydensystems.com/news/read?original="

interface NewsRepository {
    // R-20 (2026-11-25): an edition load carries WHICH file the stories
    // actually came from — the requested edition, the other edition, or
    // latest.json — so Home can label a fallback render instead of
    // silently caching another edition's stories under the requested
    // key. [sourceEdition] is "morning" | "evening" | "latest".
    fun editionsFlow(edition: String): Flow<EditionLoad> // "morning" | "evening"
    fun breakingFlow(): Flow<Breaking?>
    fun liveNowFlow(): Flow<List<LiveItem>>
    fun videosFlow(): Flow<List<BriefingVideo>>
    fun searchStories(query: String): Flow<List<Story>>
    fun savedFlow(): Flow<List<Story>>
    suspend fun saveStory(story: Story)
    suspend fun unsaveStory(link: String)
    fun isSavedFlow(link: String): Flow<Boolean>
    suspend fun refreshAll()
    // Targeted refreshes (public so the notification polling loops — and
    // any future FCM-triggered refresh — can re-fetch one feed without
    // re-fetching everything). Each writes through the Room cache, so a
    // re-collected flow surfaces the new rows.
    suspend fun refreshBreaking(): Breaking?
    suspend fun refreshEdition(edition: String)

    // --- Wave 2: Originals + Business + full-body read ---
    /** Published StarBrook Originals (PostgREST). Empty when the anon key is not wired yet. */
    fun originalsFlow(): Flow<List<Story>>

    /** Business for one edition slot: at-a-glance line + the edition's "Business" section stories. Null when the edition carries neither. */
    fun marketsFlow(edition: String): Flow<MarketsData?>

    /** One published Original by id (the ?original= read-page param), with its stored full body. Null when unknown or the key is not wired. */
    suspend fun fetchOriginal(id: String): OriginalStory?

    /** A /n/<code> short code resolved to its story shell (article URL in [Story.link]). Null when unknown or the key is not wired. */
    suspend fun resolveShortCode(code: String): Story?

    /** Full article body via the story-body-extract function (public, no auth). */
    suspend fun extractBody(articleUrl: String): ExtractResult
}

/** One edition load: the stories Home shows plus which file they actually came from (see [NewsRepository.editionsFlow]). */
data class EditionLoad(
    val stories: List<Story>,
    val sourceEdition: String,
)

/** Business section data: the at-a-glance ticker line plus the edition's Business stories.
 * [sectionName] is the display name straight from the edition JSON's section entry
 * ("Business" in the evening edition; the morning edition still says "Markets" until
 * the edition pipeline renames it — the app shows whatever the JSON says, never a
 * hardcoded label). It falls back to "Business" when the edition carries a markets
 * line but no matching section. */
data class MarketsData(
    val markets: String,
    val marketsLabel: String?,
    val stories: List<Story>,
    val sectionName: String,
)

/** A published Original with its stored full body (the row's `body` column — no extraction needed). */
data class OriginalStory(
    val story: Story,
    val body: String,
    val byline: String?,
)

/** Result of a story-body-extract call. `Unavailable` means the source blocked extraction (or the call failed) — the caller shows the excerpt plus a "Read at source" link, never a dead error. */
sealed interface ExtractResult {
    data class Ok(val body: String) : ExtractResult
    data class Unavailable(val error: String?) : ExtractResult
}

// --- Wire DTOs (private; only the shapes the live files actually have) ---

/**
 * edition-morning/evening.json — verified live 2026-11-19:
 * {"stories":[{...15 stories...}], "sections":[...], "atAGlance":{...},
 * "builtAt":"2026-11-19T13:00:09.781Z"}. Only "stories" and "builtAt" are
 * read; the rest is ignored (ignoreUnknownKeys). "builtAt" drives the
 * stale-edition fallback in refreshEdition: a file older than
 * EDITION_STALE_MS is "not built yet" for its slot.
 * Wave 2 also reads "sections" (the edition's named sections — "Top
 * Stories", "Business", "Oklahoma", ...; each carries its stories) and
 * "atAGlance.markets" (the ticker line) for the Business section.
 */
@Serializable
private data class EditionResponse(
    val stories: List<Story> = emptyList(),
    val sections: List<EditionSection> = emptyList(),
    val atAGlance: AtAGlance? = null,
    val builtAt: String? = null,
)

@Serializable
private data class EditionSection(
    // The edition JSON's sections carry id + name (the business section's
    // name is "Business"). "id" is absent from the live files today
    // (name only) — the default keeps the decode safe either way, and the
    // match below prefers the id when it is present.
    val id: String = "",
    val name: String = "",
    val stories: List<Story> = emptyList(),
)

@Serializable
private data class AtAGlance(
    val markets: String? = null,
    val marketsLabel: String? = null,
)

/**
 * news_original_stories row (PostgREST) — columns verified against the
 * live table 2026-11-19 ~15:10 CST (1 published row: OKC data-centers
 * pause, beat oklahoma, body 851 chars). The mapping to [Story] mirrors
 * the web pipeline's originalToStory (publish-stage/edition-originals.ts):
 * title=headline, source="StarBrook Newsroom", link=read?original=<id>,
 * section from beat (national/tech/culture, else local), excerpt = first
 * 200 chars of body at a word boundary.
 */
@Serializable
private data class OriginalRow(
    val id: String,
    val headline: String,
    val body: String = "",
    val label: String? = null,
    val byline: String? = null,
    val beat: String? = null,
    val status: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("publish_date") val publishDate: String? = null,
    @SerialName("place_city") val placeCity: String? = null,
    @SerialName("place_metro") val placeMetro: String? = null,
    @SerialName("national_significant") val nationalSignificant: Boolean = false,
)

/** shared_stories row for a /n/<code> short code — query verbatim from the web read page (read.js lookupShortCode). */
@Serializable
private data class SharedStoryRow(
    val title: String? = null,
    val source: String? = null,
    val excerpt: String? = null,
    @SerialName("article_url") val articleUrl: String? = null,
)

/** story-body-extract request/response — contract verbatim from the live function (story-body-extract/index.ts): POST {"url"}, no auth; success {"ok":true,"body","cached"}; business failure 200 {"ok":false,"error"}; bad URL 400 {"ok":false,"error":"invalid-url"}. */
@Serializable
private data class ExtractRequest(val url: String)

@Serializable
private data class ExtractResponse(
    val ok: Boolean = false,
    val body: String? = null,
    val error: String? = null,
)

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
    // Notification toggles (SettingsStore, DataStore-backed). R-20
    // (2026-11-25): the in-app polling loops no longer read these —
    // the toggles drive FCM registration only, and background refresh
    // runs whenever a flow is collected. Kept (nullable) so
    // AppContainer's construction is unchanged.
    private val settings: SettingsStore? = null,
    // Supabase anon key for PostgREST (Originals + short codes). Nullable
    // so the app compiles and runs before the key is wired (BuildConfig
    // from env, TODO.md §1 — the Api.kt owner / coordinator passes it
    // in); with no key, Originals stay empty and original-id / short-code
    // resolution falls through to extraction / unavailable. NEVER a
    // literal here: the key is public in the portal JS but it still
    // belongs in env, not in source.
    private val anonKey: String? = null,
) : NewsRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    // In-memory caches for data with no Room table (Originals + Markets).
    // Written only on Dispatchers.IO (flows use flowOn(IO); refreshAll
    // runs withContext(IO)); read on the same dispatcher. Process-lifetime
    // only — a cold start refetches, like every other flow here.
    private var originalsCache: List<OriginalStory> = emptyList()
    private val marketsCache = mutableMapOf<String, MarketsData>()

    // --- Cache-first flows (INSTANT STANDARD) ---
    // Every flow emits the Room cache first (instant render, never a blank
    // load), then refreshes quietly in the background. A failed refresh
    // with a warm cache is swallowed — the UI keeps showing real data. A
    // failed refresh with an EMPTY cache throws IOException so the UI can
    // show ErrorState + retry. Nothing here ever returns fake data.

    // Last known actual source per requested edition (R-20): set by
    // every refreshEdition, read by editionsFlow for the cache-first
    // emit. In-memory only, touched on Dispatchers.IO only (flows use
    // flowOn(IO); refreshAll runs withContext(IO)). A cold start has
    // no entry, so the first render labels nothing until the refresh
    // lands — the label then appears with the real source.
    private val editionSources = mutableMapOf<String, String>()

    override fun editionsFlow(edition: String): Flow<EditionLoad> = flow {
        val cached = db.cachedStoryDao().storiesForEdition(edition)
        var lastEmitted = EditionLoad(cached, editionSources[edition] ?: edition)
        if (cached.isNotEmpty()) emit(lastEmitted)
        try {
            refreshEdition(edition)
            val fresh = db.cachedStoryDao().storiesForEdition(edition)
            lastEmitted = EditionLoad(fresh, editionSources[edition] ?: edition)
            emit(lastEmitted)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (cached.isEmpty()) {
                throw IOException(
                    "Could not load the $edition edition. Check your connection and try again.",
                    e,
                )
            }
            // Warm cache: keep showing it; the next collect / refreshAll retries.
        }
        // R-20 (2026-11-25): background refresh is decoupled from the
        // notification opt-in — the toggles drive FCM registration
        // only. While this flow is collected (Home open) the edition
        // file is re-checked every EDITION_POLL_MS whether the edition
        // toggle is on or off, so a new edition surfaces within one
        // poll after it lands — no reopen needed. Emits only when the
        // load actually changed.
        while (currentCoroutineContext().isActive) {
            delay(EDITION_POLL_MS)
            try {
                refreshEdition(edition)
                val fresh = db.cachedStoryDao().storiesForEdition(edition)
                val load = EditionLoad(fresh, editionSources[edition] ?: edition)
                if (load != lastEmitted) {
                    lastEmitted = load
                    emit(load)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Failed poll with a warm cache: keep showing it; the
                // next poll retries. Never an error surface here.
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun breakingFlow(): Flow<Breaking?> = flow {
        emit(db.breakingDao().getBreaking())
        try {
            emit(refreshBreaking())
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Breaking null is a valid state (strip hidden); a failed refresh
            // just keeps whatever the cache had. Never an error surface here.
        }
        // R-20 (2026-11-25): decoupled from the Breaking toggle (see
        // editionsFlow) — re-check breaking.json every BREAKING_POLL_MS
        // for as long as the flow is collected (Home open), whether the
        // toggle is on or off. Breaking is a data class, so an unchanged
        // re-emit is conflated downstream and the strip does not flicker.
        while (currentCoroutineContext().isActive) {
            delay(BREAKING_POLL_MS)
            try {
                emit(refreshBreaking())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Failed poll: keep the cached breaking; next poll retries.
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun liveNowFlow(): Flow<List<LiveItem>> = flow {
        val cached = db.liveItemDao().getAll()
        var lastEmitted = cached
        if (cached.isNotEmpty()) emit(cached)
        try {
            refreshLive()
            val fresh = db.liveItemDao().getAll()
            lastEmitted = fresh
            emit(fresh)
        } catch (e: Exception) {
            if (cached.isEmpty()) {
                throw IOException(
                    "Could not load live updates. Check your connection and try again.",
                    e,
                )
            }
        }
        // R-20 (2026-11-25): Live Now had no polling loop at all — it
        // went stale while Home sat open. Re-check latest.json every
        // LIVE_POLL_MS for as long as the flow is collected (Home open),
        // regardless of any toggle (toggles drive FCM registration
        // only). Emits only when the list actually changed.
        while (currentCoroutineContext().isActive) {
            delay(LIVE_POLL_MS)
            try {
                refreshLive()
                val fresh = db.liveItemDao().getAll()
                if (fresh != lastEmitted) {
                    lastEmitted = fresh
                    emit(fresh)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Failed poll with a warm cache: keep showing it; the
                // next poll retries. Never an error surface here.
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun videosFlow(): Flow<List<BriefingVideo>> = flow {
        val cached = db.videoDao().getAll()
        var lastEmitted = cached
        if (cached.isNotEmpty()) emit(cached)
        try {
            refreshVideos()
            val fresh = db.videoDao().getAll()
            lastEmitted = fresh
            emit(fresh)
        } catch (e: Exception) {
            if (cached.isEmpty()) {
                throw IOException(
                    "Could not load videos. Check your connection and try again.",
                    e,
                )
            }
        }
        // R-20 (2026-11-25): decoupled from the Breaking toggle (see
        // editionsFlow) — re-fetch the manifest every BREAKING_POLL_MS
        // for as long as the flow is collected (Home open), whether the
        // toggle is on or off, so a flash posted while Home sits open
        // surfaces within one poll. Emits only when the list actually
        // changed, so the screen never flickers.
        while (currentCoroutineContext().isActive) {
            delay(BREAKING_POLL_MS)
            try {
                refreshVideos()
                val fresh = db.videoDao().getAll()
                if (fresh != lastEmitted) {
                    lastEmitted = fresh
                    emit(fresh)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Failed poll with a warm cache: keep showing it; the
                // next poll retries. Never an error surface here.
            }
        }
    }.map { sortVideosForDisplay(it) }.flowOn(Dispatchers.IO)

    // --- Wave 2: Originals + Business + full-body read ---

    override fun originalsFlow(): Flow<List<Story>> = flow {
        emit(originalsCache.map { it.story })
        try {
            refreshOriginals()
            emit(originalsCache.map { it.story })
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // No key = no fetch attempted (empty list above); a failed
            // fetch with an empty cache is an error surface with retry.
            if (originalsCache.isEmpty() && anonKey != null) {
                throw IOException(
                    "Could not load StarBrook Originals. Check your connection and try again.",
                    e,
                )
            }
            // Warm in-memory cache: keep showing it.
        }
    }.flowOn(Dispatchers.IO)

    override fun marketsFlow(edition: String): Flow<MarketsData?> = flow {
        marketsCache[edition]?.let { emit(it) }
        try {
            val fresh = fetchMarkets(edition)
            if (fresh != null) {
                marketsCache[edition] = fresh
                emit(fresh)
            } else if (marketsCache[edition] == null) {
                emit(null)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Business is a below-fold section: a failed fetch hides it
            // instead of erroring the screen. The next collect retries.
            if (marketsCache[edition] == null) emit(null)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun fetchOriginal(id: String): OriginalStory? {
        originalsCache.firstOrNull { it.story.link == ORIGINAL_READ_BASE + id }
            ?.let { return it }
        val key = anonKey ?: return null
        val url = (SUPABASE_REST_BASE + "news_original_stories")
            .toHttpUrl().newBuilder()
            .addQueryParameter(
                "select",
                "id,headline,body,label,byline,beat,status,published_at,publish_date,place_city,place_metro,national_significant",
            )
            .addQueryParameter("id", "eq.$id")
            .addQueryParameter("status", "eq.published")
            .addQueryParameter("limit", "1")
            .build()
        val rows = runCatching {
            json.decodeFromString<List<OriginalRow>>(getWithAnonKey(url.toString(), key))
        }.getOrNull() ?: return null
        return rows.firstOrNull()?.toOriginalStory()
    }

    override suspend fun resolveShortCode(code: String): Story? {
        val key = anonKey ?: return null
        val url = (SUPABASE_REST_BASE + "shared_stories")
            .toHttpUrl().newBuilder()
            .addQueryParameter("code", "eq.$code")
            .addQueryParameter("select", "title,source,excerpt,article_url")
            .build()
        val rows = runCatching {
            json.decodeFromString<List<SharedStoryRow>>(getWithAnonKey(url.toString(), key))
        }.getOrNull() ?: return null
        val row = rows.firstOrNull() ?: return null
        val articleUrl = row.articleUrl?.takeIf { it.isNotBlank() } ?: return null
        return Story(
            title = row.title.orEmpty(),
            source = row.source.orEmpty(),
            excerpt = row.excerpt,
            link = articleUrl,
            published = 0L,
            section = "national",
            image = null,
        )
    }

    override suspend fun extractBody(articleUrl: String): ExtractResult =
        withContext(Dispatchers.IO) {
            val payload = json.encodeToString(ExtractRequest.serializer(), ExtractRequest(articleUrl))
            val request = Request.Builder()
                .url(STORY_BODY_EXTRACT_URL)
                .header("User-Agent", NEWS_USER_AGENT)
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    val parsed = runCatching {
                        json.decodeFromString<ExtractResponse>(text)
                    }.getOrNull()
                    if (response.isSuccessful && parsed?.ok == true &&
                        !parsed.body.isNullOrBlank()
                    ) {
                        ExtractResult.Ok(parsed.body)
                    } else {
                        ExtractResult.Unavailable(parsed?.error ?: "http-${response.code}")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ExtractResult.Unavailable(e.message)
            }
        }

    /** PostgREST list fetch for [refreshOriginals]; single-row fetches use [fetchOriginal] instead. */
    private suspend fun refreshOriginals() {
        val key = anonKey ?: return // key not wired yet: Originals stay empty, no failure.
        val url = (SUPABASE_REST_BASE + "news_original_stories")
            .toHttpUrl().newBuilder()
            .addQueryParameter(
                "select",
                "id,headline,body,label,byline,beat,status,published_at,publish_date,place_city,place_metro,national_significant",
            )
            .addQueryParameter("status", "eq.published")
            .addQueryParameter("order", "published_at.desc")
            .addQueryParameter("limit", "20")
            .build()
        val rows = json.decodeFromString<List<OriginalRow>>(getWithAnonKey(url.toString(), key))
        originalsCache = rows.map { it.toOriginalStory() }
    }

    /** Business for one edition slot, from the edition JSON file itself.
     * The section is matched by id "business" when the JSON carries one,
     * else by name — "Business" (evening edition) or the pre-rename
     * "Markets" (morning edition still carries it). The displayed name is
     * the matched section's own JSON name, verbatim — never hardcoded —
     * falling back to "Business" when only the markets line is present. */
    private suspend fun fetchMarkets(edition: String): MarketsData? {
        val parsed = fetchEditionJson(edition)
        val marketsLine = parsed.atAGlance?.markets?.takeIf { it.isNotBlank() }
        val section = parsed.sections.firstOrNull {
            it.id.equals("business", ignoreCase = true) ||
                it.name.equals("Business", ignoreCase = true) ||
                it.name.equals("Markets", ignoreCase = true)
        }
        val sectionStories = section?.stories.orEmpty()
        if (marketsLine == null && sectionStories.isEmpty()) return null
        return MarketsData(
            markets = marketsLine.orEmpty(),
            marketsLabel = parsed.atAGlance?.marketsLabel,
            stories = sectionStories,
            sectionName = section?.name?.takeIf { it.isNotBlank() } ?: "Business",
        )
    }

    private suspend fun refreshMarkets(edition: String) {
        fetchMarkets(edition)?.let { marketsCache[edition] = it }
    }

    /** A PostgREST GET with the anon key (apikey + Bearer, the portal's pattern). */
    private fun getWithAnonKey(url: String, key: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", NEWS_USER_AGENT)
            .header("apikey", key)
            .header("Authorization", "Bearer $key")
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            return response.body?.string() ?: throw IOException("Empty response for $url")
        }
    }

    /**
     * Original row → [Story], mirroring the web pipeline's originalToStory
     * (publish-stage/edition-originals.ts): beat national/tech/culture
     * keeps its section, every other beat (incl. oklahoma) is local.
     * Excerpt is the first 200 chars of the stored body at a word
     * boundary. The stored `byline` is kept verbatim (it is already
     * cased: "… — Oklahoma Desk. Edited by CADE.").
     */
    private fun OriginalRow.toOriginalStory(): OriginalStory {
        val beatValue = beat?.takeIf { it.isNotBlank() } ?: "oklahoma"
        val section = when (beatValue.lowercase()) {
            "national" -> "national"
            "tech" -> "tech"
            "culture" -> "culture"
            else -> "local"
        }
        val story = Story(
            title = headline,
            source = "StarBrook Newsroom",
            excerpt = excerptOf(body),
            link = ORIGINAL_READ_BASE + id,
            published = publishedAt?.let { parseInstantMsOrNull(it) }
                ?: System.currentTimeMillis(),
            section = section,
            image = null,
            isOriginal = true,
            beat = beatValue,
        )
        return OriginalStory(story, body, byline?.takeIf { it.isNotBlank() })
    }

    private fun excerptOf(body: String, limit: Int = 200): String {
        val text = body.replace(Regex("\\s+"), " ").trim()
        if (text.length <= limit) return text
        val cut = text.substring(0, limit).split(" ").dropLast(1).joinToString(" ")
        return (cut.ifBlank { text.substring(0, limit) }).trimEnd() + " …"
    }

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
            try {
                refreshOriginals()
            } catch (e: Exception) {
                failures += "originals (${e.message})"
            }
            try {
                refreshMarkets("morning")
            } catch (e: Exception) {
                failures += "morning markets (${e.message})"
            }
            try {
                refreshMarkets("evening")
            } catch (e: Exception) {
                failures += "evening markets (${e.message})"
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

    /**
     * DEFECT 1 fallback (2026-11-19): the requested edition is not always
     * there to load. Before ~16:00 the evening file is still yesterday's
     * (builtAt 2026-11-18T22:00:06Z when verified), and on a first-ever
     * build day it can 404 outright. Never show an error screen when some
     * edition exists: try the requested file, then the other edition, then
     * latest.json. Whatever wins is cached under the REQUESTED edition key —
     * the cache is "what Home shows for this switcher position", and the
     * next refresh overwrites it when the real file lands. Only when all
     * three sources fail does this throw (offline / empty cache → ErrorState
     * + retry, which is the honest state then).
     */
    override suspend fun refreshEdition(edition: String) {
        val (source, stories) = fetchEditionWithFallback(edition)
        editionSources[edition] = source
        val dao = db.cachedStoryDao()
        dao.clearEdition(edition)
        dao.insertAll(stories.map { it.copy(edition = edition) })
    }

    /** One edition file plus its build time (null when the file predates builtAt). */
    private data class EditionFile(val stories: List<Story>, val builtAtMs: Long?)

    /** The whole edition file, parsed once — the cache path reads stories/builtAt, Business reads sections/atAGlance. */
    private suspend fun fetchEditionJson(edition: String): EditionResponse {
        val url = StarBrookApi.STORAGE_BASE + EDITIONS_BASE + "edition-$edition.json"
        return json.decodeFromString<EditionResponse>(getText(url))
    }

    private suspend fun fetchEditionFile(edition: String): EditionFile {
        val parsed = fetchEditionJson(edition)
        return EditionFile(parsed.stories, parsed.builtAt?.let { parseInstantMsOrNull(it) })
    }

    /**
     * "Current" = built within the last 18h. The pipeline builds at ~07:00
     * and ~16:00, so the latest file of a slot is at most ~15h old
     * (evening overnight); a superseded file is >= 24h old. 18h sits
     * between the two, so a day-old evening file reads as "not built yet"
     * while the overnight edition still counts as current. A file with no
     * builtAt at all is accepted (older files predate the field).
     */
    private fun isCurrentEdition(builtAtMs: Long?): Boolean =
        builtAtMs == null || System.currentTimeMillis() - builtAtMs <= EDITION_STALE_MS

    /**
     * The requested edition, the other edition, or latest.json — in
     * that order. Returns WHICH source won alongside the stories (R-20:
     * the cache is "what Home shows for this switcher position", and
     * the UI labels the fallback).
     */
    private suspend fun fetchEditionWithFallback(edition: String): Pair<String, List<Story>> {
        val requested = runCatching { fetchEditionFile(edition) }.getOrNull()
        if (requested != null && requested.stories.isNotEmpty() &&
            isCurrentEdition(requested.builtAtMs)
        ) {
            return edition to requested.stories
        }
        val other = if (edition == "morning") "evening" else "morning"
        val fallback = runCatching { fetchEditionFile(other) }.getOrNull()
        if (fallback != null && fallback.stories.isNotEmpty() &&
            isCurrentEdition(fallback.builtAtMs)
        ) {
            return other to fallback.stories
        }
        // Last resort: latest.json — the freshest stories the backend has.
        // No freshness gate here: as a fallback, slightly stale latest
        // stories still beat an error screen.
        val latest = runCatching { fetchLatestAsStories() }.getOrNull()
        if (!latest.isNullOrEmpty()) return "latest" to latest
        throw IOException(
            "Could not load the $edition edition. Check your connection and try again.",
        )
    }

    /** latest.json (compact t/s/a/u keys) mapped onto Story for display. */
    private suspend fun fetchLatestAsStories(): List<Story> {
        val url = StarBrookApi.STORAGE_BASE + EDITIONS_BASE + "latest.json"
        val parsed = json.decodeFromString<LatestResponse>(getText(url))
        return parsed.stories.map { item ->
            Story(
                title = item.title,
                source = item.source,
                excerpt = null,
                link = item.url,
                published = item.published,
                section = "national",
                image = null,
            )
        }
    }

    /** Returns the live breaking story, or null when none qualifies. */
    override suspend fun refreshBreaking(): Breaking? {
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
        // R-39: the row carries detectedAt so the strip can age live
        // instead of freezing on the server's display string.
        if (fresh) dao.insert(breaking!!.copy(detectedAtMs = detectedAtMs ?: 0L))
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
            // R-40: the manifest position is the within-day order —
            // every video of one day shares the date string.
            .mapIndexed { index, video -> video.toBriefingVideo().copy(manifestIndex = index) }
        val dao = db.videoDao()
        dao.clearAll()
        dao.insertAll(videos)
    }

    /**
     * Daily morning/evening entries have no "file"/"poster" in the manifest.
     * The two editions are SEPARATE bucket objects: morning is
     * "news-briefing-<date>.mp4" (poster "news-briefing-<date>.jpg") and
     * evening is "news-briefing-<date>-evening.mp4" (poster
     * "news-briefing-<date>-evening.jpg") — both verified to exist live
     * (morning 2026-11-19, evening 2026-11-18). The old mapping sent
     * evening entries to the morning file, so tapping an evening briefing
     * played the morning video. Breaking flashes use their explicit
     * manifest filenames; if the pipeline later adds explicit "file"
     * fields to daily entries, those win automatically.
     */
    private fun ManifestVideo.toBriefingVideo(): BriefingVideo {
        val baseName = if (edition == "evening") {
            "news-briefing-$date-evening"
        } else {
            "news-briefing-$date"
        }
        return BriefingVideo(
            id = id,
            date = date,
            edition = edition,
            isBreaking = isBreaking,
            title = title,
            duration = duration,
            file = file ?: "$baseName.mp4",
            posterUrl = StarBrookApi.posterUrl(poster ?: "$baseName.jpg"),
        )
    }

    private fun parseInstantMsOrNull(value: String): Long? = try {
        java.time.Instant.parse(value).toEpochMilli()
    } catch (e: Exception) {
        null
    }
}

/**
 * R-40 (2026-11-25): the display order for videos — newest date
 * first, manifest order within a day. The DAO orders the same way;
 * this in-memory sort is the backstop so the "newest" selection never
 * depends on Room's row order alone. Pure (no Android) so unit tests
 * can cover it directly.
 */
internal fun sortVideosForDisplay(videos: List<BriefingVideo>): List<BriefingVideo> =
    videos.sortedWith(
        compareByDescending<BriefingVideo> { it.date }.thenBy { it.manifestIndex },
    )
