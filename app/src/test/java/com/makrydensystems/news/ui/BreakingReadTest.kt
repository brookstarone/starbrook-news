package com.makrydensystems.news.ui

import com.makrydensystems.news.data.model.Breaking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the breaking-reader fix (2026-11-25): a breaking-only
 * story whose lead is a video/paywalled page must resolve to a Found
 * state carrying the live breaking entry's excerpt when extraction
 * fails, instead of a bare Unavailable with no text at all.
 *
 * JVM-only, like QuotesDataSourceTest: [breakingReadStory] is pure
 * (no android.net.Uri — the caller parses the read link's ?u=), and
 * the Breaking decode uses only kotlinx.serialization. The full
 * [ReadViewModel] branch (Found + Unavailable vs bare Unavailable)
 * is not unit-covered here — the ViewModel is private to ReadScreen;
 * it is covered by CI compile + the phone tap-test (1917 story).
 */
class BreakingReadTest {

    private val json = Json { ignoreUnknownKeys = true }

    // The 2026-11-25 19:17 defect case, verbatim: the CBS video lead
    // whose excerpt is the feed's og:description sentence the 1917
    // flash narrated.
    private val videoUrl =
        "https://www.cbsnews.com/video/trump-blames-volatile-stock-market-midterm-election-results/"
    private val readLink = "https://makrydensystems.com/news/read?u=$videoUrl"
    private val excerpt =
        "President Trump blamed the volatile stock market on the midterm election results that will put Democrats in control of Congress in January."
    private val breaking1917 = Breaking(
        title = "Trump blames volatile stock market on midterm election results",
        source = "CBS News",
        age = "9m ago",
        outletCount = 2,
        url = readLink,
        excerpt = excerpt,
    )

    // --- breakingReadStory: matching ---

    @Test
    fun matchesByReadPageLink() {
        val story = breakingReadStory(breaking1917, readLink, "", videoUrl, videoUrl)
        assertEquals(excerpt, story?.excerpt)
        assertEquals(videoUrl, story?.link)
        assertEquals("CBS News", story?.source)
    }

    @Test
    fun matchesByArticleUrlInsideReadLink() {
        // The tap arrives with a different read-link wrapper but the
        // same article URL inside the breaking entry's ?u=.
        val otherReadLink = "https://makrydensystems.com/news/read?u=$videoUrl&x=1"
        val story = breakingReadStory(breaking1917, otherReadLink, "", videoUrl, videoUrl)
        assertEquals(excerpt, story?.excerpt)
    }

    @Test
    fun matchesByTitleWhenLinksDiffer() {
        // Neither the read link nor either article URL matches — only
        // the title does (the entry's ?u= is the video URL, the tap's
        // is a different article).
        val story = breakingReadStory(
            breaking1917,
            "https://makrydensystems.com/news/read?u=https://example.com/other",
            "Trump blames volatile stock market on midterm election results",
            "https://example.com/other",
            videoUrl,
        )
        assertEquals(excerpt, story?.excerpt)
    }

    @Test
    fun linkMatchResolvesEvenWhenTitleDiffers() {
        // The read link is the strongest key, same as the edition
        // cache: a reworded headline on the same link still resolves.
        val story = breakingReadStory(
            breaking1917,
            readLink,
            "Trump blames market slide on midterms",
            videoUrl,
            videoUrl,
        )
        assertEquals(excerpt, story?.excerpt)
    }

    @Test
    fun differentStoryReturnsNull() {
        assertNull(
            breakingReadStory(
                breaking1917,
                "https://makrydensystems.com/news/read?u=https://example.com/other",
                "Something else entirely",
                "https://example.com/other",
                "https://example.com/other",
            ),
        )
    }

    // --- breakingReadStory: excerpt gating ---

    @Test
    fun blankExcerptReturnsNull() {
        // No excerpt + failed extraction must still land on the honest
        // Unavailable state — the helper returns null so resolveOnce
        // falls through to it instead of a Found with no text.
        assertNull(breakingReadStory(breaking1917.copy(excerpt = "   "), readLink, "", videoUrl, videoUrl))
        assertNull(breakingReadStory(breaking1917.copy(excerpt = null), readLink, "", videoUrl, videoUrl))
    }

    @Test
    fun nullBreakingReturnsNull() {
        assertNull(breakingReadStory(null, readLink, "", videoUrl, videoUrl))
    }

    @Test
    fun excerptIsTrimmedAndLinkFallsBackToBreakingUrl() {
        val story = breakingReadStory(
            breaking1917.copy(excerpt = "  $excerpt  "),
            readLink,
            "",
            null,
            null,
        )
        assertEquals(excerpt, story?.excerpt)
        assertEquals(readLink, story?.link)
    }

    // --- Breaking JSON decode: the `e` key ---

    @Test
    fun breakingJsonWithExcerptDecodes() {
        val inner = json.decodeFromString<Breaking>(
            """{"t":"Trump blames volatile stock market on midterm election results","s":"CBS News","a":"9m ago","n":2,"u":"$readLink","e":"$excerpt"}""",
        )
        assertEquals(excerpt, inner.excerpt)
        assertEquals(2, inner.outletCount)
        assertEquals(readLink, inner.url)
    }

    @Test
    fun breakingJsonWithoutExcerptDecodesToNull() {
        val inner = json.decodeFromString<Breaking>(
            """{"t":"Senate passes bill","s":"BBC News","u":"https://makrydensystems.com/news/read?u=https://www.bbc.com/news/x"}""",
        )
        assertNull(inner.excerpt)
        assertEquals(1, inner.outletCount)
    }
}
