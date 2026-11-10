package com.novaping.boostlab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkPathSelectorTest {
    @Test
    fun selectsFastestPathWhenNoCurrentPathIsMeasured() {
        val decision = NetworkPathSelector.select(listOf(
            path("wifi", "Wi-Fi", 120L, current = false),
            path("cell", "Données mobiles", 70L, current = false)
        ))
        assertEquals("cell", decision?.selected?.networkId)
    }

    @Test
    fun switchesWhenAlternativeIsAtLeastFifteenPercentFaster() {
        val decision = NetworkPathSelector.select(listOf(
            path("wifi", "Wi-Fi", 200L, current = true),
            path("cell", "Données mobiles", 160L, current = false)
        ))
        assertEquals("cell", decision?.selected?.networkId)
    }

    @Test
    fun keepsCurrentPathWhenGainIsBelowFifteenPercent() {
        val decision = NetworkPathSelector.select(listOf(
            path("wifi", "Wi-Fi", 200L, current = true),
            path("cell", "Données mobiles", 180L, current = false)
        ))
        assertEquals("wifi", decision?.selected?.networkId)
    }

    @Test
    fun ignoresCandidateWithFewerThanFiveSuccessfulProbes() {
        val decision = NetworkPathSelector.select(listOf(
            path("wifi", "Wi-Fi", 50L, successes = 4, current = false),
            path("cell", "Données mobiles", 100L, successes = 7, current = false)
        ))
        assertEquals("cell", decision?.selected?.networkId)
    }

    @Test
    fun returnsNoDecisionWhenNoCandidateHasEnoughSuccesses() {
        assertNull(NetworkPathSelector.select(listOf(
            path("wifi", "Wi-Fi", 50L, successes = 4, current = true)
        )))
    }

    @Test
    fun rejectsInvalidLatencyAndSuccessCounts() {
        assertNull(NetworkPathSelector.select(listOf(
            path("zero", "Wi-Fi", 0L, current = false),
            path("bad-count", "Cellulaire", 100L, successes = 8, current = false)
        )))
    }

    private fun path(
        id: String,
        label: String,
        median: Long,
        successes: Int = 7,
        current: Boolean
    ) = NetworkPathScore(id, label, median, 0L, successes, 7, current)
}
