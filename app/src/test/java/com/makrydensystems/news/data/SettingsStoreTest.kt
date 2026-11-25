package com.makrydensystems.news.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Fix 1 (2026-11-24): the ZIP the app shows/uses is the stored ZIP only
 * while signed in; signed out the app behaves as if no ZIP exists.
 * [SettingsStore.effectiveZip] is the pure decision behind
 * SettingsStore.effectiveZipFlow — covered here without Android.
 */
class SettingsStoreTest {

    @Test
    fun `signed out with stored ZIP shows no ZIP`() {
        assertNull(SettingsStore.effectiveZip("73120", signedIn = false))
    }

    @Test
    fun `signed in with stored ZIP shows the ZIP`() {
        assertEquals("73120", SettingsStore.effectiveZip("73120", signedIn = true))
    }

    @Test
    fun `fresh install with no stored ZIP shows no ZIP either way`() {
        assertNull(SettingsStore.effectiveZip(null, signedIn = false))
        assertNull(SettingsStore.effectiveZip(null, signedIn = true))
    }

    @Test
    fun `signed in with no stored ZIP shows no ZIP`() {
        assertNull(SettingsStore.effectiveZip(null, signedIn = true))
    }
}
