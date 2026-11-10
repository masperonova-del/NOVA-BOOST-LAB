package com.novaping.boostlab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProbeStatsTest {
    @Test
    fun computesMedianForOddSampleCount() {
        val stats = ProbeStats(attempts = 5, timesMs = listOf(120L, 40L, 80L, 60L, 100L))

        assertEquals(80L, stats.medianMs)
        assertEquals(40L, stats.minMs)
        assertEquals(120L, stats.maxMs)
        assertEquals(5, stats.successes)
    }

    @Test
    fun computesMedianForEvenSampleCount() {
        val stats = ProbeStats(attempts = 5, timesMs = listOf(90L, 30L, 50L, 70L))

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
