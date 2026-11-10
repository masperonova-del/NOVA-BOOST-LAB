package com.novaping.boostlab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkPathSelectorTest {
    @Test
    fun selectsFastestStablePathWhenNoCurrentPathIsMeasured() {
        val decision = NetworkPathSelector.select(listOf(
            path("wifi", "Wi-Fi", 120L, jitter = 10L, current = false),
            path("cell", "Données mobiles", 70L, jitter = 2L, current = false)
        ))
        assertEquals("cell", decision?.selected?.networkId)
    }

    @Test
    fun switchesWhenAlternativeHasAtLeastFifteenPercentBetterAdjustedScore() {
        val decision = NetworkPathSelector.select(listOf(
            path("wifi", "Wi-Fi", 200L, jitter = 5L, current = true),
            path("cell", "Données mobiles", 150L, jitter = 5L, current = false)
        ))
        assertEquals("cell", decision?.selected?.networkId)
    }

    @Test
    fun keepsCurrentPathWhenAdjustedGainIsBelowFifteenPercent() {
        val decision = NetworkPathSelector.select(listOf(
            path("wifi", "Wi-Fi", 200L, jitter = 5L, current = true),
            path("cell", "Données mobiles", 180L, jitter = 5L, current = false)
        ))
        assertEquals("wifi", decision?.selected?.networkId)
    }

    @Test
    fun avoidsSwitchingToFastButUnstableAlternative() {
        val decision = NetworkPathSelector.select(listOf(
            path("wifi", "Wi-Fi", 200L, jitter = 5L, current = true),
            path("cell", "Données mobiles", 150L, jitter = 40L, current = false)
        ))
        assertEquals("wifi", decision?.selected?.networkId)
    }

    @Test
    fun choosesStablePathWhenRawMediansAreClose() {
        val decision = NetworkPathSelector.select(listOf(
            path("unstable", "Wi-Fi", 90L, jitter = 30L, current = false),
            path("stable", "Données mobiles", 100L, jitter = 2L, current = false)
        ))
        assertEquals("stable", decision?.selected?.networkId)
    }

    @Test
    fun ignoresCandidateWithFewerThanFiveSuccessfulProbes() {
        val decision = NetworkPathSelector.select(listOf(
            path("wifi", "Wi-Fi", 50L, successes = 4, current = false),
            path("cell", "Données mobiles", 100L, current = false)
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
    fun rejectsInvalidLatencyJitterAndSuccessCounts() {
        assertNull(NetworkPathSelector.select(listOf(
            path("zero", "Wi-Fi", 0L, current = false),
            path("negative-jitter", "Wi-Fi", 100L, jitter = -1L, current = false),
            path("bad-count", "Cellulaire", 100L, successes = 8, current = false)
        )))
    }

    private fun path(
        id: String,
        label: String,
        median: Long,
        jitter: Long = 0L,
        successes: Int = 7,
        current: Boolean
    ) = NetworkPathScore(id, label, median, jitter, successes, 7, current)
}
