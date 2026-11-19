package com.makrydensystems.news.ui.components

import java.util.concurrent.TimeUnit

/**
 * Relative time for story timestamps, matching the web's friendly
 * phrasing ("12m ago", "2h ago"). Epoch millis in, short string out.
 * Never returns an empty string; future timestamps read "just now".
 */
internal fun formatRelativeTime(publishedMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val diff = (nowMillis - publishedMillis).coerceAtLeast(0L)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    if (minutes < 1) return "just now"
    if (minutes < 60) return "${minutes}m ago"
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    if (hours < 24) return "${hours}h ago"
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    if (days < 7) return "${days}d ago"
    val weeks = days / 7
    return "${weeks}w ago"
}

/** Section labels as the reader sees them — title case, never raw keys. */
internal fun formatSection(section: String): String {
    val trimmed = section.trim()
    if (trimmed.isEmpty()) return ""
    return trimmed.lowercase().replaceFirstChar { it.uppercaseChar() }
}
