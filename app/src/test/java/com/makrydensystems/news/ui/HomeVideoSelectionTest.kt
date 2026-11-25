package com.makrydensystems.news.ui

import com.makrydensystems.news.data.model.BriefingVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * R-19 / R-45 / R-20 (2026-11-25): the newest breaking video is the
 * newest BREAKING entry wherever it sits; the Home video row pins the
 * daily briefings first; the fallback label names the real source.
 */
class HomeVideoSelectionTest {

    private fun video(
        id: String,
        date: String = "2026-11-25",
        edition: String = "",
        isBreaking: Boolean = false,
        title: String = id,
    ) = BriefingVideo(
        id = id,
        date = date,
        edition = edition,
        isBreaking = isBreaking,
        title = title,
        duration = "1:30",
        file = "$id.mp4",
        posterUrl = null,
    )

    // Manifest order, newest first: close, flash, midday, evening, morning.
    private val manifest = listOf(
        video("2026-11-25-close", edition = "business-close"),
        video("flash-1147", edition = "", isBreaking = true, title = "Flash 11:47"),
        video("2026-11-25-midday", edition = "business-midday"),
        video("2026-11-25-evening", edition = "evening"),
        video("2026-11-25-morning", edition = "morning"),
        video("2026-11-24-morning", date = "2026-11-24", edition = "morning"),
    )

    @Test
    fun `newest breaking video is found behind the business close video`() {
        assertEquals("flash-1147", newestBreakingVideo(manifest)?.id)
    }

    @Test
    fun `no breaking video means no top card and no banner`() {
        val noFlash = manifest.filter { !it.isBreaking }
        assertNull(newestBreakingVideo(noFlash))
        assertNull(newestUnseenBreakingVideo(noFlash, null))
    }

    @Test
    fun `unseen breaking video prompts until it is seen`() {
        assertEquals("flash-1147", newestUnseenBreakingVideo(manifest, null)?.id)
        assertNull(newestUnseenBreakingVideo(manifest, "flash-1147"))
    }

    @Test
    fun `an older unseen flash still prompts when the newest is seen`() {
        val twoFlashes = listOf(
            video("flash-new", isBreaking = true),
            video("flash-old", isBreaking = true),
        )
        assertEquals("flash-old", newestUnseenBreakingVideo(twoFlashes, "flash-new")?.id)
    }

    @Test
    fun `home row pins morning and evening briefings then flash then business`() {
        assertEquals(
            listOf(
                "2026-11-25-morning",
                "2026-11-25-evening",
                "flash-1147",
                "2026-11-25-close",
            ),
            selectHomeVideos(manifest).map { it.id },
        )
    }

    @Test
    fun `home row on a quiet day is just the two briefings`() {
        val quiet = manifest.filter { it.edition == "morning" || it.edition == "evening" }
        assertEquals(
            listOf("2026-11-25-morning", "2026-11-25-evening"),
            selectHomeVideos(quiet).map { it.id },
        )
    }

    @Test
    fun `home row is empty when there are no videos`() {
        assertEquals(emptyList<BriefingVideo>(), selectHomeVideos(emptyList()))
    }

    @Test
    fun `fallback note is null when the source is the requested edition`() {
        assertNull(editionFallbackNote("morning", "morning"))
        assertNull(editionFallbackNote("evening", null))
    }

    @Test
    fun `fallback note names the other edition`() {
        assertEquals(
            "Showing the morning edition — the evening edition isn't out yet.",
            editionFallbackNote("evening", "morning"),
        )
    }

    @Test
    fun `fallback note names latest when latest is the source`() {
        assertEquals(
            "Showing the latest stories — the evening edition isn't out yet.",
            editionFallbackNote("evening", "latest"),
        )
    }
}
