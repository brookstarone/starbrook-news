package com.makrydensystems.news.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * R-41 (2026-11-25): a missing timestamp (0) has no honest age — it
 * must render "" (callers omit the time element) instead of
 * "~2920w ago". The normal bands are pinned here too.
 */
class FormatTest {

    private val now = 1_769_000_000_000L

    @Test
    fun `zero timestamp renders empty`() {
        assertEquals("", formatRelativeTime(0L, now))
    }

    @Test
    fun `negative timestamp renders empty`() {
        assertEquals("", formatRelativeTime(-100L, now))
    }

    @Test
    fun `just now for current and future timestamps`() {
        assertEquals("just now", formatRelativeTime(now, now))
        assertEquals("just now", formatRelativeTime(now + 60_000L, now))
        assertEquals("just now", formatRelativeTime(now - 30_000L, now))
    }

    @Test
    fun `minutes hours days weeks bands`() {
        assertEquals("5m ago", formatRelativeTime(now - 5L * 60 * 1000, now))
        assertEquals("59m ago", formatRelativeTime(now - 59L * 60 * 1000, now))
        assertEquals("2h ago", formatRelativeTime(now - 2L * 60 * 60 * 1000, now))
        assertEquals("23h ago", formatRelativeTime(now - 23L * 60 * 60 * 1000, now))
        assertEquals("3d ago", formatRelativeTime(now - 3L * 24 * 60 * 60 * 1000, now))
        assertEquals("2w ago", formatRelativeTime(now - 14L * 24 * 60 * 60 * 1000, now))
    }
}
