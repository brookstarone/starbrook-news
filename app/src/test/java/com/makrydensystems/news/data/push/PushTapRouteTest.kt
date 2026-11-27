package com.makrydensystems.news.data.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

/**
 * Unit tests for the push-tap article link (2026-11-27): the Black
 * Friday announcement push arrived with route "news" and an article
 * link, and the tap opened the News tab with no article in sight.
 * [pushTapDestination] must turn that into a "web?url=" destination
 * the app opens in the in-app browser, without touching breaking or
 * video taps.
 *
 * JVM-only, like BreakingReadTest: the helpers are pure (java.net,
 * no android.net.Uri), and the round-trip decode uses the same
 * java.net.URLDecoder MainActivity uses.
 */
class PushTapRouteTest {

    private val article = "https://makrydensystems.com/news/black-friday-deals/"
    private val artifact =
        "https://muse.ai/s/black-friday-sales-article-sxg6xk241hmxs2/0ZPmBBPjBy3_a2sl_-0U7-3VeIDIoRdDa65Ic9fW8tQ"

    private fun decoded(destination: String): String =
        URLDecoder.decode(destination.removePrefix("web?url="), "UTF-8")

    // --- the Black Friday case ---

    @Test
    fun morningRouteWithArticleLinkOpensWeb() {
        val destination = pushTapDestination("news", article)
        assertTrue(destination.startsWith("web?url="))
        assertEquals(article, decoded(destination))
    }

    @Test
    fun artifactLinkWithQueryParamsRoundTrips() {
        // Query params ride through URLEncoder/URLDecoder exactly —
        // %20 stays %20, & stays a separator after decoding.
        val link = "$artifact?x=1&y=hello%20world"
        val destination = pushTapDestination("news", link)
        assertTrue(destination.startsWith("web?url="))
        assertEquals(link, decoded(destination))
    }

    @Test
    fun httpLinkAlsoOpensWeb() {
        val destination = pushTapDestination("news", "http://example.com/a")
        assertTrue(destination.startsWith("web?url="))
        assertEquals("http://example.com/a", decoded(destination))
    }

    @Test
    fun linkWithSurroundingWhitespaceIsTrimmed() {
        val destination = pushTapDestination("news", "  $article  ")
        assertEquals(article, decoded(destination))
    }

    // --- no link (or a bad link) keeps the existing route ---

    @Test
    fun noLinkKeepsRoute() {
        assertEquals("news", pushTapDestination("news", null))
        assertEquals("news", pushTapDestination("news", ""))
        assertEquals("news", pushTapDestination("news", "   "))
        assertEquals("weather", pushTapDestination("weather", null))
    }

    @Test
    fun nonHttpLinkKeepsRoute() {
        assertEquals("news", pushTapDestination("news", "javascript:alert(1)"))
        assertEquals("news", pushTapDestination("news", "ftp://example.com/x"))
        assertEquals("news", pushTapDestination("news", "intent://example.com#Intent;scheme=https;end"))
        assertEquals("news", pushTapDestination("news", "file:///etc/passwd"))
        assertEquals("news", pushTapDestination("news", "makrydensystems.com/news/"))
        assertEquals("news", pushTapDestination("news", "https://"))
    }

    // --- routes that already carry their own URL are untouched ---

    @Test
    fun breakingStoryRouteUntouched() {
        val story = "story?link=https%3A%2F%2Fwww.cbsnews.com%2Fvideo%2Fx&title=Trump"
        assertEquals(story, pushTapDestination(story, "https://www.cbsnews.com/video/x"))
        assertEquals(story, pushTapDestination(story, null))
    }

    @Test
    fun playRouteUntouched() {
        val play = "play?url=https%3A%2F%2Fexample.com%2Fv.mp4&title=Video"
        assertEquals(play, pushTapDestination(play, article))
    }

    @Test
    fun webRouteUntouched() {
        val web = "web?url=https%3A%2F%2Fexample.com%2Fa"
        assertEquals(web, pushTapDestination(web, article))
    }

    // --- route validation still applies ---

    @Test
    fun invalidRouteWithLinkOpensWeb() {
        // An unknown route falls back to News, and the link still wins.
        val destination = pushTapDestination("bogus-route", article)
        assertTrue(destination.startsWith("web?url="))
        assertEquals(article, decoded(destination))
    }

    @Test
    fun invalidRouteWithoutLinkFallsBackToNews() {
        assertEquals("news", pushTapDestination("bogus-route", null))
        assertEquals("news", pushTapDestination(null, null))
    }

    @Test
    fun validPushRouteAcceptsKnownRoutes() {
        assertEquals("news", validPushRoute("news"))
        assertEquals("weather", validPushRoute("weather"))
        assertEquals("portal", validPushRoute("portal"))
        assertEquals("search", validPushRoute("search"))
        assertEquals("news", validPushRoute(""))
        assertEquals("news", validPushRoute(null))
    }
}
