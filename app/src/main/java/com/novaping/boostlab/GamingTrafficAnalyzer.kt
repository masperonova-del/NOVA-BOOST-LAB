package com.novaping.boostlab

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/**
 * Summary of app-owned HTTPS probes. These values are not game-packet telemetry.
 */
internal data class GamingTrafficSummary(
    val totalProbes: Int,
    val successfulProbes: Int,
    val failedProbes: Int,
    val medianResponseMs: Long?,
    val medianResponseVariationMs: Long?,
    val defaultNetworkLossEvents: Int,
    val networkEventMonitoringAvailable: Boolean,
    val durationMs: Long,
    val observedTransports: List<String>
) {
    val successRatePercent: Int
        get() = if (totalProbes == 0) 0 else (successfulProbes * 100) / totalProbes
}

internal object GamingTrafficMetricsCalculator {
    fun calculate(
        samples: List<Long?>,
        defaultNetworkLossEvents: Int,
        networkEventMonitoringAvailable: Boolean,
        durationMs: Long,
        observedTransports: Set<String>
    ): GamingTrafficSummary {
        val valid = samples.map { value -> value?.takeIf { it > 0L } }
        val successful = valid.count { it != null }
        val failed = valid.size - successful
        val adjacentVariations = valid.zipWithNext().mapNotNull { (left, right) ->
            if (left != null && right != null) absoluteDifference(right, left) else null
        }
        return GamingTrafficSummary(
            totalProbes = valid.size,
            successfulProbes = successful,
            failedProbes = failed,
            medianResponseMs = median(valid.filterNotNull()),
            medianResponseVariationMs = median(adjacentVariations),
            defaultNetworkLossEvents = defaultNetworkLossEvents.coerceAtLeast(0),
            networkEventMonitoringAvailable = networkEventMonitoringAvailable,
            durationMs = durationMs.coerceAtLeast(0L),
            observedTransports = observedTransports.toList().sorted()
        )
    }

    private fun absoluteDifference(left: Long, right: Long): Long = if (left >= right) left - right else right - left

    private fun median(values: List<Long>): Long? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            sorted[middle - 1] + (sorted[middle] - sorted[middle - 1]) / 2L
        }
    }
}

/**
 * Runs a bounded foreground-only observation session using HTTPS response probes
 * over Android's current default network. It does not inspect or redirect other apps.
 */
internal class GamingTrafficAnalyzer(context: Context) {
    private val connectivity = context.applicationContext
        .getSystemService(ConnectivityManager::class.java)

    suspend fun runSession(onProgress: (completed: Int, total: Int) -> Unit): GamingTrafficSummary {
        val manager = connectivity
        val samples = mutableListOf<Long?>()
        val observedTransports = ConcurrentHashMap.newKeySet<String>()
        val networkLossEvents = AtomicInteger(0)
        var monitoringAvailable = false
        var callbackRegistered = false

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                manager?.getNetworkCapabilities(network)?.let { capabilities ->
                    transportLabel(capabilities)?.let(observedTransports::add)
                }
            }

            override fun onLost(network: Network) {
                networkLossEvents.incrementAndGet()
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                transportLabel(capabilities)?.let(observedTransports::add)
            }
        }

        val startedAt = SystemClock.elapsedRealtime()
        try {
            if (manager != null) {
                try {
                    manager.registerDefaultNetworkCallback(callback)
                    callbackRegistered = true
                    monitoringAvailable = true
                } catch (_: Exception) {
                    // Probes remain useful when callback registration is restricted.
                    monitoringAvailable = false
                }
            }

            repeat(PROBE_COUNT) { index ->
                currentCoroutineContext().ensureActive()
                val network = manager?.activeNetwork
                val capabilities = if (manager != null && network != null) {
                    manager.getNetworkCapabilities(network)
                } else {
                    null
                }
                val validated = capabilities != null &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

                if (network != null && capabilities != null) {
                    transportLabel(capabilities)?.let(observedTransports::add)
                }

                val responseTime = if (network != null && validated) {
                    probe(network)
                } else {
                    null
                }
                samples += responseTime
                onProgress(index + 1, PROBE_COUNT)

                if (index < PROBE_COUNT - 1) delay(INTERVAL_MS)
            }
        } finally {
            if (callbackRegistered && manager != null) {
                try {
                    manager.unregisterNetworkCallback(callback)
                } catch (_: Exception) {
                    // The session result remains valid; callback cleanup is best-effort.
                }
            }
        }

        return GamingTrafficMetricsCalculator.calculate(
            samples = samples,
            defaultNetworkLossEvents = networkLossEvents.get(),
            networkEventMonitoringAvailable = monitoringAvailable,
            durationMs = SystemClock.elapsedRealtime() - startedAt,
            observedTransports = observedTransports
        )
    }

    private suspend fun probe(network: Network): Long? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = network.openConnection(URL(PROBE_URL)) as HttpURLConnection
            connection.connectTimeout = PROBE_TIMEOUT_MS
            connection.readTimeout = PROBE_TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            val startedAt = System.nanoTime()
            val responseCode = connection.responseCode
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L
            if (responseCode == EXPECTED_STATUS) elapsedMs.coerceAtLeast(1L) else null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun transportLabel(capabilities: NetworkCapabilities): String? = when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Données mobiles"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
        else -> null
    }

    private companion object {
        const val PROBE_COUNT = 20
        const val INTERVAL_MS = 1_500L
        const val PROBE_TIMEOUT_MS = 1_800
        const val EXPECTED_STATUS = 204
        const val PROBE_URL = "https://www.google.com/generate_204"
    }
}
