package com.makrydensystems.news.ui.components

import com.makrydensystems.news.data.model.Breaking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * R-39 (2026-11-25): the strip's age is a live relative time from
 * detectedAt when the row carries it — hour 5 must not still read
 * "34m ago". The server string is the fallback when detectedAt is
 * unknown.
 */
class BreakingAgeTest {

    private val now = 1_769_000_000_000L

    private fun breaking(age: String? = "34m ago", detectedAtMs: Long = 0L) = Breaking(
        title = "Test breaking",
        source = "Test Source",
        age = age,
        outletCount = 3,
        url = "https://example.com/story",
        detectedAtMs = detectedAtMs,
    )

    @Test
    fun `detectedAt renders a live relative age`() {
        assertEquals("34m ago", breakingAgeText(breaking(detectedAtMs = now - 34L * 60 * 1000), now))
        assertEquals("5h ago", breakingAgeText(breaking(detectedAtMs = now - 5L * 60 * 60 * 1000), now))
    }

    @Test
    fun `unknown detectedAt falls back to the server age string`() {
        assertEquals("34m ago", breakingAgeText(breaking(age = "34m ago"), now))
    }

    @Test
    fun `unknown detectedAt and no server age renders no age`() {
        assertNull(breakingAgeText(breaking(age = null), now))
        assertNull(breakingAgeText(breaking(age = ""), now))
    }
}
