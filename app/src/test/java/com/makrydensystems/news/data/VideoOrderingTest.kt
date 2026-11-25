package com.makrydensystems.news.data

import com.makrydensystems.news.data.model.BriefingVideo
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * R-40 (2026-11-25): videos sharing one date string order by manifest
 * position, not by unspecified row order; newer dates still come first.
 */
class VideoOrderingTest {

    private fun video(id: String, date: String, manifestIndex: Int) = BriefingVideo(
        id = id,
        date = date,
        edition = "morning",
        isBreaking = false,
        title = id,
        duration = "1:00",
        file = "$id.mp4",
        posterUrl = null,
        manifestIndex = manifestIndex,
    )

    @Test
    fun `same-day videos order by manifest index`() {
        val shuffled = listOf(
            video("v2", "2026-11-25", 2),
            video("v0", "2026-11-25", 0),
            video("v1", "2026-11-25", 1),
        )
        assertEquals(
            listOf("v0", "v1", "v2"),
            sortVideosForDisplay(shuffled).map { it.id },
        )
    }

    @Test
    fun `newer date wins over manifest index`() {
        val videos = listOf(
            video("old-day-first", "2026-11-24", 0),
            video("new-day-last", "2026-11-25", 5),
        )
        assertEquals(
            listOf("new-day-last", "old-day-first"),
            sortVideosForDisplay(videos).map { it.id },
        )
    }
}
