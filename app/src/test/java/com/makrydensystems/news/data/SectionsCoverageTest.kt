package com.makrydensystems.news.data

import com.makrydensystems.news.ui.components.DRAWER_SECTIONS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R-02 verification (2026-11-25 sweep): the Home filter, the Settings
 * toggles and the drawer must all cover the same eleven live sections
 * — the 6-of-17 drop happened because the old five-only set was the
 * filter. These tests pin the coverage so a future section addition
 * cannot silently drop stories again. (The Settings toggles iterate
 * the same eleven literals; the drawer rows are asserted here.)
 */
class SectionsCoverageTest {

    private val expectedEleven = setOf(
        "national", "local", "tech", "business", "culture",
        "sports", "faith", "science",
        "entertainment", "gaming", "fashion",
    )

    @Test
    fun `ALL_SECTIONS holds all eleven live sections`() {
        assertEquals(expectedEleven, SettingsStore.ALL_SECTIONS)
    }

    @Test
    fun `the six R-02 sections are the only addition to the original five`() {
        assertEquals(
            setOf("sports", "faith", "science", "entertainment", "gaming", "fashion"),
            SettingsStore.SECTIONS_ADDED_R02,
        )
        assertEquals(
            setOf("national", "local", "tech", "business", "culture"),
            SettingsStore.ALL_SECTIONS - SettingsStore.SECTIONS_ADDED_R02,
        )
    }

    @Test
    fun `every live section has a drawer row`() {
        val drawerSections = DRAWER_SECTIONS.mapNotNull { it.storySection }.toSet()
        assertTrue(drawerSections.containsAll(SettingsStore.ALL_SECTIONS))
    }

    @Test
    fun `Entertainment Gaming and Fashion are browsable from the drawer`() {
        val drawerSections = DRAWER_SECTIONS.mapNotNull { it.storySection }.toSet()
        assertTrue(drawerSections.contains("entertainment"))
        assertTrue(drawerSections.contains("gaming"))
        assertTrue(drawerSections.contains("fashion"))
    }
}
