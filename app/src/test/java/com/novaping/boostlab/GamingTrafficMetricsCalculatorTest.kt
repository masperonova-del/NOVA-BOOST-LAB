package com.novaping.boostlab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GamingTrafficMetricsCalculatorTest {
    @Test
    fun calculatesMedianAndSuccessRateFromSuccessfulProbes() {
        val result = GamingTrafficMetricsCalculator.calculate(
            samples = listOf(100L, 120L, null, 80L),
            defaultNetworkLossEvents = 1,
            networkEventMonitoringAvailable = true,
            durationMs = 6_000L,
            observedTransports = setOf("Wi-Fi")
        )

        assertEquals(4, result.totalProbes)
        assertEquals(3, result.successfulProbes)
        assertEquals(1, result.failedProbes)
        assertEquals(100L, result.medianResponseMs)
        assertEquals(75, result.successRatePercent)
        assertEquals(1, result.defaultNetworkLossEvents)
        assertEquals(listOf("Wi-Fi"), result.observedTransports)
    }

    @Test
    fun calculatesVariationOnlyBetweenAdjacentSuccessfulSamples() {
        val result = GamingTrafficMetricsCalculator.calculate(
            samples = listOf(100L, 130L, null, 110L, 150L),
            defaultNetworkLossEvents = 0,
            networkEventMonitoringAvailable = true,
            durationMs = 10_000L,
            observedTransports = setOf("Wi-Fi", "Données mobiles")
        )

        assertEquals(30L, result.medianResponseVariationMs)
        assertEquals(listOf("Données mobiles", "Wi-Fi"), result.observedTransports)
    }

    @Test
    fun returnsNullLatencyMetricsWhenEveryProbeFails() {
        val result = GamingTrafficMetricsCalculator.calculate(
            samples = listOf(null, null, 0L, -4L),
            defaultNetworkLossEvents = 0,
            networkEventMonitoringAvailable = false,
            durationMs = 5_000L,
            observedTransports = emptySet()
        )

        assertEquals(0, result.successfulProbes)
        assertEquals(4, result.failedProbes)
        assertEquals(0, result.successRatePercent)
        assertNull(result.medianResponseMs)
        assertNull(result.medianResponseVariationMs)
        assertEquals(false, result.networkEventMonitoringAvailable)
    }

    @Test
    fun doesNotInventVariationFromSingleSuccessfulProbe() {
        val result = GamingTrafficMetricsCalculator.calculate(
            samples = listOf(null, 90L, null),
            defaultNetworkLossEvents = -3,
            networkEventMonitoringAvailable = true,
            durationMs = -1L,
            observedTransports = emptySet()
        )

        assertEquals(90L, result.medianResponseMs)
        assertNull(result.medianResponseVariationMs)
        assertEquals(0, result.defaultNetworkLossEvents)
        assertEquals(0L, result.durationMs)
    }

    @Test
    fun handlesEmptySessionWithoutDividingByZero() {
        val result = GamingTrafficMetricsCalculator.calculate(
            samples = emptyList(),
            defaultNetworkLossEvents = 0,
            networkEventMonitoringAvailable = false,
            durationMs = 0L,
            observedTransports = emptySet()
        )

        assertEquals(0, result.totalProbes)
        assertEquals(0, result.successRatePercent)
        assertNull(result.medianResponseMs)
    }
}
