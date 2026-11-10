package com.novaping.boostlab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProbeStatsTest {
    @Test
    fun computesMedianForOddSampleCount() {
        val stats = ProbeStats(attempts = 5, timesMs = listOf(120, 40, 80, 60, 100))

        assertEquals(80L, stats.medianMs)
        assertEquals(40L, stats.minMs)
        assertEquals(120L, stats.maxMs)
        assertEquals(5, stats.successes)
    }

    @Test
    fun computesMedianForEvenSampleCount() {
        val stats = ProbeStats(attempts = 5, timesMs = listOf(90, 30, 50, 70))

        assertEquals(60L, stats.medianMs)
        assertEquals(30L, stats.minMs)
        assertEquals(90L, stats.maxMs)
        assertEquals(4, stats.successes)
    }

    @Test
    fun emptySamplesHaveNoLatencyStatistics() {
        val stats = ProbeStats(attempts = 5, timesMs = emptyList())

        assertNull(stats.medianMs)
        assertNull(stats.minMs)
        assertNull(stats.maxMs)
        assertEquals(0, stats.successes)
    }
}
