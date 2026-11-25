package com.makrydensystems.news.ui.components

import com.makrydensystems.news.data.model.BriefingVideo
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * R-42 (2026-11-25): business editions are named like the daily ones
 * — no raw "business-midday briefing" slug on the card. The breaking
 * branch is untouched: flashes keep the manifest title.
 */
class VideoTitlesTest {

    private fun video(
        edition: String,
        isBreaking: Boolean = false,
        title: String = "Manifest title",
        duration: String = "1:31",
        date: String = "2026-11-24",
    ) = BriefingVideo(
        id = "$edition-$date",
        date = date,
        edition = edition,
        isBreaking = isBreaking,
        title = title,
        duration = duration,
        file = "x.mp4",
        posterUrl = null,
    )

    @Test
    fun `daily briefings are named by edition with runtime and date`() {
        assertEquals("Morning briefing", videoDisplayTitle(video("morning")))
        assertEquals("1:31 · 2026-11-24", videoDisplayCaption(video("morning")))
        assertEquals("Evening briefing", videoDisplayTitle(video("evening")))
        assertEquals("1:31 · 2026-11-24", videoDisplayCaption(video("evening")))
    }

    @Test
    fun `business midday is the Midday business report`() {
        val v = video("business-midday")
        assertEquals("Midday business report", videoDisplayTitle(v))
        assertEquals("1:31 · 2026-11-24", videoDisplayCaption(v))
    }

    @Test
    fun `business close is Market close`() {
        val v = video("business-close")
        assertEquals("Market close", videoDisplayTitle(v))
        assertEquals("1:31 · 2026-11-24", videoDisplayCaption(v))
    }

    @Test
    fun `breaking flash keeps the manifest title`() {
        val v = video("breaking", isBreaking = true, title = "Flash: markets tumble")
        assertEquals("Flash: markets tumble", videoDisplayTitle(v))
        assertEquals("breaking briefing · 2026-11-24", videoDisplayCaption(v))
    }

    @Test
    fun `unknown edition keeps the manifest title and slug caption`() {
        val v = video("weekend", title = "Weekend roundup")
        assertEquals("Weekend roundup", videoDisplayTitle(v))
        assertEquals("weekend briefing · 2026-11-24", videoDisplayCaption(v))
    }
}
