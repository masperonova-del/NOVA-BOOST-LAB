package com.novaping.boostlab

import org.junit.Assert.assertEquals
import org.junit.Test

class SmartNetworkOptimizerTest {

    @Test
    fun stableSamplesRecommendKeepingCurrentNetwork() {
        val assessment = SmartNetworkOptimizer.assess(result())

        assertEquals(OptimizerState.STABLE, assessment.state)
        assertEquals(OptimizerAction.KEEP_CURRENT, assessment.action)
    }

    @Test
    fun missingInternetCapabilityRequestsConnectionCheck() {
        val assessment = SmartNetworkOptimizer.assess(result(hasInternet = false))

        assertEquals(OptimizerState.NO_INTERNET, assessment.state)
        assertEquals(OptimizerAction.CHECK_CONNECTION, assessment.action)
    }

    @Test
    fun unvalidatedNetworkRequestsConnectionCheck() {
        val assessment = SmartNetworkOptimizer.assess(result(validated = false))

        assertEquals(OptimizerState.NOT_VALIDATED, assessment.state)
        assertEquals(OptimizerAction.CHECK_CONNECTION, assessment.action)
    }

    @Test
    fun tooFewSuccessfulSamplesDoNotProduceAQualityVerdict() {
        val assessment = SmartNetworkOptimizer.assess(
            result(tcpTimes = listOf(90L, 100L), httpsTimes = listOf(300L, 310L, 320L, 330L, 340L))
        )

        assertEquals(OptimizerState.INSUFFICIENT_DATA, assessment.state)
        assertEquals(OptimizerAction.RETEST, assessment.action)
    }

    @Test
    fun lowSuccessRatioIsClassifiedAsUnstable() {
        val assessment = SmartNetworkOptimizer.assess(
            result(tcpTimes = listOf(80L, 90L, 100L), httpsTimes = listOf(300L, 310L, 320L, 330L, 340L))
        )

        assertEquals(OptimizerState.UNSTABLE, assessment.state)
        assertEquals(OptimizerAction.RETEST, assessment.action)
    }

    @Test
    fun excessiveSpreadIsClassifiedAsUnstable() {
        val assessment = SmartNetworkOptimizer.assess(
            result(tcpTimes = listOf(80L, 85L, 90L, 95L, 180L))
        )

        assertEquals(OptimizerState.UNSTABLE, assessment.state)
        assertEquals(OptimizerAction.RETEST, assessment.action)
    }

    @Test
    fun slowTcpMedianRecommendsManualComparisonOnly() {
        val assessment = SmartNetworkOptimizer.assess(
            result(tcpTimes = listOf(320L, 325L, 330L, 335L, 340L))
        )

        assertEquals(OptimizerState.SLOW_RESPONSES, assessment.state)
        assertEquals(OptimizerAction.COMPARE_NETWORKS, assessment.action)
    }

    @Test
    fun slowHttpsMedianIsEvaluatedSeparatelyFromTcp() {
        val assessment = SmartNetworkOptimizer.assess(
            result(httpsTimes = listOf(1_600L, 1_610L, 1_620L, 1_630L, 1_640L))
        )

        assertEquals(OptimizerState.SLOW_RESPONSES, assessment.state)
        assertEquals(OptimizerAction.COMPARE_NETWORKS, assessment.action)
    }

    @Test
    fun zeroOrNegativeSamplesAreRejectedAsInvalidData() {
        val assessment = SmartNetworkOptimizer.assess(
            result(tcpTimes = listOf(80L, 90L, -1L, 100L, 105L))
        )

        assertEquals(OptimizerState.INSUFFICIENT_DATA, assessment.state)
        assertEquals(OptimizerAction.RETEST, assessment.action)
    }

    private fun result(
        tcpTimes: List<Long> = listOf(90L, 92L, 95L, 94L, 93L),
        httpsTimes: List<Long> = listOf(300L, 305L, 310L, 308L, 306L),
        hasInternet: Boolean = true,
        validated: Boolean = true
    ) = NetworkTestResult(
        tcp = ProbeStats(attempts = 5, timesMs = tcpTimes),
        https = ProbeStats(attempts = 5, timesMs = httpsTimes),
        network = NetworkSnapshot(
            label = "Wi-Fi",
            hasInternet = hasInternet,
            validated = validated
        ),
        completedAtMillis = 0L
    )
}
