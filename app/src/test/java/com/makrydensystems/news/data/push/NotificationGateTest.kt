package com.makrydensystems.news.data.push

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R-43 (2026-11-25): the FCM receive path must honour the local
 * toggles — a kind that is off is dropped, never shown. Unknown kinds
 * fall under breaking (the default kind).
 */
class NotificationGateTest {

    private val allOn = listOf(true, true, true, true)
    private val allOff = listOf(false, false, false, false)

    private fun gate(
        kind: String,
        flags: List<Boolean>,
    ): Boolean = shouldShowNotification(
        kind,
        breaking = flags[0],
        morning = flags[1],
        evening = flags[2],
        weather = flags[3],
    )

    @Test
    fun `every kind shows when its toggle is on`() {
        assertTrue(gate("breaking", allOn))
        assertTrue(gate("morning", allOn))
        assertTrue(gate("evening", allOn))
        assertTrue(gate("weather", allOn))
    }

    @Test
    fun `every kind is dropped when its toggle is off`() {
        assertFalse(gate("breaking", allOff))
        assertFalse(gate("morning", allOff))
        assertFalse(gate("evening", allOff))
        assertFalse(gate("weather", allOff))
    }

    @Test
    fun `one kind off does not silence the others`() {
        assertFalse(gate("morning", listOf(true, false, true, true)))
        assertTrue(gate("breaking", listOf(true, false, true, true)))
        assertTrue(gate("evening", listOf(true, false, true, true)))
        assertTrue(gate("weather", listOf(true, false, true, true)))
    }

    @Test
    fun `unknown kind falls under breaking`() {
        assertTrue(gate("sports", listOf(true, false, false, false)))
        assertFalse(gate("sports", listOf(false, true, true, true)))
    }
}
