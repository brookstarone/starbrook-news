package com.makrydensystems.news.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fix 2 (2026-11-24): sign-in must merge the server news_subs row with
 * the local migrated set instead of applying the raw row — a stale
 * pre-R-02 row (original five only) must not switch the six new
 * sections off. [PortalRepository.mergeNewsroomSections] is the pure
 * merge; [PortalRepository.encodeSectionsForPush] is the pure server
 * encoding for the merged set.
 */
class PortalRepositoryTest {

    private val allEleven = SettingsStore.ALL_SECTIONS
    private val staleFive = listOf("national", "local", "tech", "business", "culture")

    @Test
    fun `stale server row plus migrated local eleven merges to eleven`() {
        val merged = PortalRepository.mergeNewsroomSections(staleFive, allEleven)
        assertEquals(allEleven, merged)
        // The six R-02 sections stay on.
        assertTrue(merged.containsAll(SettingsStore.SECTIONS_ADDED_R02))
    }

    @Test
    fun `local deliberate off of a new section stays off`() {
        val local = allEleven - "gaming"
        val merged = PortalRepository.mergeNewsroomSections(staleFive, local)
        assertFalse(merged.contains("gaming"))
        assertEquals(local, merged)
    }

    @Test
    fun `server deliberate off among the original five stays off`() {
        // Culture off on the web portal, on locally.
        val server = listOf("national", "local", "tech", "business")
        val merged = PortalRepository.mergeNewsroomSections(server, allEleven)
        assertFalse(merged.contains("culture"))
        assertEquals(allEleven - "culture", merged)
    }

    @Test
    fun `local deliberate off among the original five stays off`() {
        // Tech off locally, server says all.
        val local = allEleven - "tech"
        val merged = PortalRepository.mergeNewsroomSections(null, local)
        assertFalse(merged.contains("tech"))
        assertEquals(local, merged)
    }

    @Test
    fun `server null means all so local subset wins`() {
        val local = setOf("national", "local", "tech", "sports", "gaming")
        val merged = PortalRepository.mergeNewsroomSections(null, local)
        assertEquals(local, merged)
    }

    @Test
    fun `server row with an outside-eleven id keeps it out of the merge but in the push`() {
        val server = staleFive + "podcasts"
        val merged = PortalRepository.mergeNewsroomSections(server, allEleven)
        // The merge itself only ever yields app sections.
        assertEquals(allEleven, merged)
        assertFalse(merged.contains("podcasts"))
        // The push encoding preserves the outside id.
        val encoded = PortalRepository.encodeSectionsForPush(merged, listOf("podcasts"))
        assertTrue(encoded != null && encoded.contains("podcasts"))
        assertTrue(encoded != null && encoded.containsAll(allEleven))
        assertEquals(12, encoded!!.size)
    }

    @Test
    fun `both full eleven merge to eleven and encode as null`() {
        val merged = PortalRepository.mergeNewsroomSections(allEleven.toList(), allEleven)
        assertEquals(allEleven, merged)
        assertNull(PortalRepository.encodeSectionsForPush(merged, emptyList()))
    }

    @Test
    fun `partial merged set encodes as an explicit list in ALL_SECTIONS order`() {
        val merged = allEleven - "fashion" - "culture"
        val encoded = PortalRepository.encodeSectionsForPush(merged, emptyList())
        assertEquals(
            listOf(
                "national", "local", "tech", "business",
                "sports", "faith", "science", "entertainment", "gaming",
            ),
            encoded,
        )
    }
}
