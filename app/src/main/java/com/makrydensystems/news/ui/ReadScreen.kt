package com.makrydensystems.news.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.makrydensystems.news.data.ExtractResult
import com.makrydensystems.news.data.NewsRepository
import com.makrydensystems.news.data.di.AppContainer
import com.makrydensystems.news.data.model.Breaking
import com.makrydensystems.news.data.model.Story
import com.makrydensystems.news.openWebUrl
import com.makrydensystems.news.ui.components.EmptyState
import com.makrydensystems.news.ui.components.ErrorState
import com.makrydensystems.news.ui.components.GoldOriginalBadge
import com.makrydensystems.news.ui.components.LiveStarMark
import com.makrydensystems.news.ui.components.SkeletonList
import com.makrydensystems.news.ui.theme.StarBrookColors
import com.makrydensystems.news.ui.theme.StarBrookShapes
import com.makrydensystems.news.ui.theme.StarBrookType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private sealed interface ReadState {
    data object Loading : ReadState

    /** Story found, with its body state. `byline` is the stored Original byline when one exists; otherwise the byline is built from the beat. */
    data class Found(
        val story: Story,
        val body: BodyState,
        val articleUrl: String?,
        val byline: String? = null,
    ) : ReadState

    /**
     * The story/URL is known but the source would not share the full
     * text (blocked / paywalled / too short). Distinct from [Missing]:
     * there IS something to read at the source, so this state always
     * carries the "Read at [source]" link. Never a dead error.
     */
    data class Unavailable(
        val title: String,
        val articleUrl: String?,
        val source: String?,
    ) : ReadState

    /** No story in any cache and no usable URL — nothing to show but retry. */
    data object Missing : ReadState
}

private sealed interface BodyState {
    /** No full body and no article URL to fetch one from — excerpt only. */
    data object None : BodyState

    /** Full body paragraphs (extracted live, or stored in the Original row). */
    data class Ready(val paragraphs: List<String>) : BodyState

    /** Extraction was attempted and failed — excerpt plus a "Read at source" link. */
    data object Unavailable : BodyState
}

/** What an incoming read link can carry: a direct article URL, a read-page ?u= / ?original=, or a /n/<code> short code. */
private data class ParsedReadLink(
    val articleUrl: String?,
    val originalId: String?,
    val shortCode: String?,
)

private fun parseReadLink(rawLink: String): ParsedReadLink {
    val trimmed = rawLink.trim()
    if (trimmed.isBlank()) return ParsedReadLink(null, null, null)
    val uri = runCatching { Uri.parse(trimmed) }.getOrNull()
    val host = uri?.host?.lowercase().orEmpty()
    val path = uri?.path.orEmpty()
    if ("makrydensystems.com" in host) {
        if ("/news/read" in path) {
            return ParsedReadLink(
                articleUrl = uri?.getQueryParameter("u")?.takeIf { it.isNotBlank() },
                originalId = uri?.getQueryParameter("original")?.takeIf { it.isNotBlank() },
                shortCode = null,
            )
        }
        val code = Regex("^/n/([A-Za-z0-9]{8})$").find(path)?.groupValues?.getOrNull(1)
        if (code != null) return ParsedReadLink(null, null, code)
    }
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        ParsedReadLink(trimmed, null, null)
    } else {
        ParsedReadLink(null, null, null)
    }
}

/** Same article ignoring tracking query params (?traffic_source=rss etc.) and fragments. */
private fun stripQuery(url: String): String =
    url.substringBefore('?').substringBefore('#').trimEnd('/')

private fun sameArticleUrl(a: String, b: String): Boolean =
    a.isNotBlank() && b.isNotBlank() && stripQuery(a) == stripQuery(b)

/**
 * Breaking-reader fix (2026-11-25): a story shell for the LIVE
 * breaking entry, when it is the story being read and it carries a
 * real excerpt. breaking.json's `u` is a read-page link, so the match
 * runs against the read link, the article URL inside it
 * ([breakingArticleUrl], parsed by the caller with [parseReadLink]),
 * and the title — the same three keys the edition cache uses.
 * Returns null when the entry is a different story or its excerpt is
 * blank: the caller then falls through to the honest Unavailable
 * state. Pure (no Android) so unit tests can cover it directly.
 */
internal fun breakingReadStory(
    breaking: Breaking?,
    link: String,
    title: String,
    articleUrl: String?,
    breakingArticleUrl: String?,
): Story? {
    if (breaking == null) return null
    val excerpt = breaking.excerpt?.trim().orEmpty()
    if (excerpt.isBlank()) return null
    val matches = breaking.url == link ||
        (articleUrl != null && (breaking.url == articleUrl || sameArticleUrl(breaking.url, articleUrl))) ||
        (breakingArticleUrl != null && articleUrl != null && sameArticleUrl(breakingArticleUrl, articleUrl)) ||
        (title.isNotBlank() && breaking.title.equals(title, ignoreCase = true))
    if (!matches) return null
    return Story(
        title = breaking.title,
        source = breaking.source,
        excerpt = excerpt,
        link = articleUrl ?: breakingArticleUrl ?: breaking.url,
        published = 0L,
        section = "national",
        image = null,
    )
}

private fun hostLabel(articleUrl: String): String =
    runCatching { Uri.parse(articleUrl).host }.getOrNull()
        ?.removePrefix("www.").orEmpty().ifBlank { "the source" }

private fun paragraphsOf(body: String): List<String> =
    body.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }

private class ReadViewModel(
    private val repo: NewsRepository,
    private val link: String,
    private val title: String,
) : ViewModel() {
    // Retry trigger (same one-shot-flow defect Wave 1 fixed on Home):
    // editionsFlow is a one-shot `flow {}` builder — each collect runs
    // once (cache, then one refresh) and completes — so a bare
    // repo.refreshAll() writing fresh rows to Room never re-emitted and
    // the old Try again button re-fetched into the void. Bumping this
    // tick re-runs the whole resolution below, which re-collects the
    // flows and re-attempts extraction. Every Try again taps refresh().
    private val refreshTick = MutableStateFlow(0)

    val state: StateFlow<ReadState> = refreshTick
        .flatMapLatest { resolve() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadState.Loading)

    private fun resolve(): kotlinx.coroutines.flow.Flow<ReadState> = flow {
        emit(ReadState.Loading)
        emit(resolveOnce())
    }.flowOn(Dispatchers.IO)

    /**
     * Resolution order (mirrors the web Shelf read view: stored text
     * first, live fallback, distinct unavailable state):
     *  1. Edition caches (morning + evening) — exact link, query-
     *     stripped link, then title.
     *  2. Saved row (stored excerpt) — same matching.
     *  3. Original row (stored FULL body) for ?original= links, and
     *     /n/<code> short codes (their row carries the article URL).
     *  4. Live breaking entry (stored excerpt) — a breaking-only
     *     story lives in breaking.json, not in either edition, and a
     *     video/paywalled lead then extracts as Unavailable with no
     *     text at all. The entry is consulted only when steps 1–3
     *     missed, so the extraction order for edition stories is
     *     unchanged.
     *  5. Live story-body-extract call on the article URL — this is
     *     what rescues breaking/live taps, whose URLs are usually NOT
     *     in today's editions (verified live 2026-11-19: the Al
     *     Jazeera breaking URL is in neither edition, title unmatched,
     *     but extraction returns 2,439 chars). When extraction fails
     *     and step 4 found an excerpt, the story resolves to Found
     *     with the excerpt shown instead of bare Unavailable.
     *  6. Honest [ReadState.Unavailable] when extraction is blocked
     *     and no stored text exists, with the Read Original link —
     *     never a dead error.
     * `first()` on editionsFlow takes the warm cache emission when
     * there is one (instant standard); a cold cache waits for the
     * post-refresh emission. Home's own collection keeps the cache
     * warm while the app is open.
     */
    private suspend fun resolveOnce(): ReadState {
        val parsed = parseReadLink(link)
        val articleUrl = parsed.articleUrl

        val cached = runCatching {
            val morning = runCatching { repo.editionsFlow("morning").first().stories }.getOrNull().orEmpty()
            val evening = runCatching { repo.editionsFlow("evening").first().stories }.getOrNull().orEmpty()
            (morning + evening).firstOrNull { story ->
                story.link == link ||
                    (articleUrl != null &&
                        (story.link == articleUrl || sameArticleUrl(story.link, articleUrl))) ||
                    (title.isNotBlank() && story.title.equals(title, ignoreCase = true))
            }
        }.getOrNull()

        val saved = if (cached != null) {
            null
        } else {
            runCatching { repo.savedFlow().first() }.getOrNull()
                ?.firstOrNull { row ->
                    row.link == link ||
                        (articleUrl != null &&
                            (row.link == articleUrl || sameArticleUrl(row.link, articleUrl)))
                }
        }

        val original = if (parsed.originalId != null) {
            runCatching { repo.fetchOriginal(parsed.originalId) }.getOrNull()
        } else {
            null
        }

        val shortCodeStory = if (parsed.shortCode != null) {
            runCatching { repo.resolveShortCode(parsed.shortCode) }.getOrNull()
        } else {
            null
        }

        // An Original row carries its stored full body: no extraction call.
        // articleUrl stays the read page's ?u= (usually absent for
        // Originals) — the story link itself is the read page, not the
        // source article, so it must never be offered as "Read at source".
        if (original != null) {
            return ReadState.Found(
                story = original.story,
                body = BodyState.Ready(paragraphsOf(original.body)),
                articleUrl = parsed.articleUrl,
                byline = original.byline,
            )
        }

        val story = cached ?: saved ?: shortCodeStory
        val resolvedArticleUrl = articleUrl ?: shortCodeStory?.link
        // Breaking-reader fix (2026-11-25): only when steps 1–3 missed.
        // A fresh fetch, not the flow's first emission — the flow emits
        // the Room cache first, which is null on a cold start, while
        // refreshBreaking() returns the live entry (null when no story
        // qualifies or detectedAt is stale, per the 6h web rule).
        // A failure here is swallowed: the extraction branches below
        // still run and land on the honest Unavailable state.
        val breakingStory = if (story == null) {
            runCatching { repo.refreshBreaking() }.getOrNull()
                ?.let { b -> breakingReadStory(b, link, title, articleUrl, parseReadLink(b.url).articleUrl) }
        } else {
            null
        }
        if (story != null && resolvedArticleUrl != null) {
            return when (val extracted = extract(resolvedArticleUrl)) {
                is ExtractResult.Ok -> ReadState.Found(
                    story = story,
                    body = BodyState.Ready(paragraphsOf(extracted.body)),
                    articleUrl = resolvedArticleUrl,
                )
                is ExtractResult.Unavailable -> ReadState.Found(
                    story = story,
                    body = BodyState.Unavailable,
                    articleUrl = resolvedArticleUrl,
                )
            }
        }
        if (story != null) {
            return ReadState.Found(
                story = story,
                body = BodyState.None,
                articleUrl = null,
            )
        }
        if (resolvedArticleUrl != null) {
            return when (val extracted = extract(resolvedArticleUrl)) {
                is ExtractResult.Ok -> ReadState.Found(
                    // The breaking entry's title/source are the real
                    // ones; the extracted body is what renders.
                    story = breakingStory ?: synthesizedStory(resolvedArticleUrl, extracted.body),
                    body = BodyState.Ready(paragraphsOf(extracted.body)),
                    articleUrl = resolvedArticleUrl,
                )
                is ExtractResult.Unavailable -> if (breakingStory != null) {
                    // Breaking-reader fix: the stored excerpt is shown
                    // (Found + Unavailable renders it) instead of the
                    // bare Unavailable state with no text at all.
                    ReadState.Found(
                        story = breakingStory,
                        body = BodyState.Unavailable,
                        articleUrl = resolvedArticleUrl,
                    )
                } else {
                    ReadState.Unavailable(
                        title = title.ifBlank { hostLabel(resolvedArticleUrl) },
                        articleUrl = resolvedArticleUrl,
                        source = hostLabel(resolvedArticleUrl),
                    )
                }
            }
        }
        if (breakingStory != null) {
            // No article URL to extract from — the excerpt is the
            // whole story (Found + None renders it).
            return ReadState.Found(
                story = breakingStory,
                body = BodyState.None,
                articleUrl = null,
            )
        }
        return ReadState.Missing
    }

    private suspend fun extract(articleUrl: String): ExtractResult =
        runCatching { repo.extractBody(articleUrl) }
            .getOrElse { ExtractResult.Unavailable(it.message) }

    /** A story shell for a URL no cache knows: title from the nav arg, source = the article host, excerpt = the body's first paragraph. */
    private fun synthesizedStory(articleUrl: String, body: String): Story {
        val firstPara = paragraphsOf(body).firstOrNull().orEmpty()
        return Story(
            title = title.ifBlank { hostLabel(articleUrl) },
            source = hostLabel(articleUrl),
            excerpt = firstPara.take(200),
            link = articleUrl,
            published = 0L,
            section = "national",
            image = null,
        )
    }

    // Saved state follows the FOUND story's link (breaking/live taps
    // arrive with a read-page URL; the saved row is keyed by the
    // edition link).
    val isSaved: StateFlow<Boolean> = state
        .flatMapLatest { s ->
            val key = (s as? ReadState.Found)?.story?.link ?: link.ifBlank { title }
            repo.isSavedFlow(key)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun toggleSave() {
        viewModelScope.launch {
            val story = (state.value as? ReadState.Found)?.story ?: return@launch
            if (isSaved.value) repo.unsaveStory(story.link) else repo.saveStory(story)
        }
    }

    fun refresh() {
        refreshTick.value += 1
        viewModelScope.launch { runCatching { repo.refreshAll() } }
    }
}

private fun formatStoryTime(publishedMillis: Long): String {
    if (publishedMillis <= 0L) return ""
    val diffMin = (System.currentTimeMillis() - publishedMillis) / 60_000L
    return when {
        diffMin < 1 -> "Just now"
        diffMin < 60 -> "$diffMin min ago"
        diffMin < 60 * 24 -> "${diffMin / 60}h ago"
        else -> SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(publishedMillis))
    }
}

/**
 * Native read view: title, source + time, hero image, and the FULL
 * article body — extracted live via story-body-extract, or stored in
 * the row for StarBrook Originals. When extraction is blocked the
 * excerpt stays plus a "Read at [source]" link (opened in the in-app
 * browser — a Custom Tab, never a WebView, never the system chooser).
 * A story no cache knows
 * still resolves through live extraction; only a blocked source with
 * no stored text lands on the honest unavailable state.
 */
@Composable
fun ReadScreen(
    container: AppContainer,
    link: String,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: ReadViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ReadViewModel(container.newsRepository, link, title)
            }
        },
    )
    val state by vm.state.collectAsState()
    val isSaved by vm.isSaved.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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
        when (val s = state) {
            is ReadState.Loading -> SkeletonList(count = 3)
            is ReadState.Missing -> ErrorState(
                message = "This story isn't in today's editions yet — it may have just landed. Try again in a moment.",
                onRetry = { vm.refresh() },
            )
            is ReadState.Unavailable -> {
                if (s.title.isNotBlank()) {
                    Text(
                        text = s.title,
                        style = StarBrookType.display,
                        color = StarBrookColors.textPrimary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = "The source wouldn't share the full text of this story — it may be paywalled or blocking readers.",
                    style = StarBrookType.body,
                    color = StarBrookColors.textSecondary,
                )
                if (s.articleUrl != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Read at ${s.source ?: "the source"} ›",
                        style = StarBrookType.label,
                        color = StarBrookColors.gold,
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .clickable { openWebUrl(context, s.articleUrl) },
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Try again",
                    style = StarBrookType.label,
                    color = StarBrookColors.gold,
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .clickable { vm.refresh() },
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
            is ReadState.Found -> {
                val story = s.story
                if (story.isOriginal) {
                    GoldOriginalBadge()
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = story.title,
                    style = StarBrookType.display,
                    color = StarBrookColors.textPrimary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                val time = formatStoryTime(story.published)
                Text(
                    text = if (time.isBlank()) story.source else "${story.source} · $time",
                    style = StarBrookType.caption,
                    color = StarBrookColors.textSecondary,
                )
                if (story.isOriginal) {
                    Spacer(modifier = Modifier.height(4.dp))
                    // Stored byline when the Original row carried one
                    // (already cased: "… — Oklahoma Desk. Edited by
                    // CADE."); otherwise build it from the beat.
                    val desk = (story.beat ?: story.section)
                        .replaceFirstChar { it.uppercaseChar() }
                    Text(
                        text = s.byline
                            ?: "Reported by the StarBrook Newsroom — $desk Desk. Edited by CADE.",
                        style = StarBrookType.caption,
                        color = StarBrookColors.textSecondary,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    contentAlignment = Alignment.Center,
                ) {
                    LiveStarMark(size = 48.dp)
                    if (!story.image.isNullOrBlank()) {
                        AsyncImage(
                            model = story.image,
                            contentDescription = story.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                when (val body = s.body) {
                    is BodyState.Ready -> {
                        if (body.paragraphs.isEmpty()) {
                            // Stored body was blank: fall back to excerpt.
                            if (!story.excerpt.isNullOrBlank()) {
                                Text(
                                    text = story.excerpt,
                                    style = StarBrookType.body,
                                    color = StarBrookColors.textPrimary,
                                )
                            } else {
                                EmptyState(
                                    title = "Story text coming",
                                    body = "The full text of this story arrives with the next update.",
                                )
                            }
                        } else {
                            body.paragraphs.forEach { paragraph ->
                                Text(
                                    text = paragraph,
                                    style = StarBrookType.body,
                                    color = StarBrookColors.textPrimary,
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                    is BodyState.Unavailable -> {
                        if (!story.excerpt.isNullOrBlank()) {
                            Text(
                                text = story.excerpt,
                                style = StarBrookType.body,
                                color = StarBrookColors.textPrimary,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        Text(
                            text = "The full text couldn't be loaded from the source — it may be paywalled or blocking readers.",
                            style = StarBrookType.bodySmall,
                            color = StarBrookColors.textSecondary,
                        )
                        if (s.articleUrl != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Read at ${story.source} ›",
                                style = StarBrookType.label,
                                color = StarBrookColors.gold,
                                modifier = Modifier
                                    .padding(vertical = 12.dp)
                                    .clickable { openWebUrl(context, s.articleUrl) },
                            )
                        }
                        Text(
                            text = "Try again",
                            style = StarBrookType.label,
                            color = StarBrookColors.gold,
                            modifier = Modifier
                                .padding(vertical = 12.dp)
                                .clickable { vm.refresh() },
                        )
                    }
                    is BodyState.None -> {
                        if (!story.excerpt.isNullOrBlank()) {
                            Text(
                                text = story.excerpt,
                                style = StarBrookType.body,
                                color = StarBrookColors.textPrimary,
                            )
                        } else {
                            EmptyState(
                                title = "Story summary coming",
                                body = "The full text of this story arrives with the next update.",
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = if (isSaved) "★ Saved to your Shelf" else "☆ Save to your Shelf",
                    style = StarBrookType.label,
                    color = StarBrookColors.gold,
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .clickable {
                            scope.launch { vm.toggleSave() }
                        },
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
