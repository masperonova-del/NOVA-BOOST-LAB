package com.novaping.boostlab

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

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
    val observedTransports: List<String>,
    val interruptedForNoValidatedNetwork: Boolean = false
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
        observedTransports: Set<String>,
        interruptedForNoValidatedNetwork: Boolean = false
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
            observedTransports = observedTransports.toList().sorted(),
            interruptedForNoValidatedNetwork = interruptedForNoValidatedNetwork
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

internal object GamingTrafficSessionPolicy {
    const val NETWORK_RECOVERY_GRACE_MS = 2_500L

    fun shouldStopForMissingNetwork(
        hasValidatedDefaultNetwork: Boolean,
        unavailableDurationMs: Long
    ): Boolean {
        return !hasValidatedDefaultNetwork &&
            unavailableDurationMs.coerceAtLeast(0L) >= NETWORK_RECOVERY_GRACE_MS
    }
}

/**
 * Runs a bounded foreground-only observation session using Android's current default
 * network. Network switches are re-evaluated between probes. It does not inspect or
 * redirect traffic belonging to other apps.
 */
internal class GamingTrafficAnalyzer(context: Context) {
    private val connectivity = context.applicationContext
        .getSystemService(ConnectivityManager::class.java)

    suspend fun runSession(
        onProgress: (completed: Int, total: Int) -> Unit,
        onNetworkState: (message: String) -> Unit
    ): GamingTrafficSummary {
        val manager = connectivity
        val samples = mutableListOf<Long?>()
        val observedTransports = ConcurrentHashMap.newKeySet<String>()
        val networkLossEvents = AtomicInteger(0)
        val networkChanges = Channel<Unit>(Channel.CONFLATED)
        var monitoringAvailable = false
        var callbackRegistered = false
        var interruptedForNoValidatedNetwork = false

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                manager?.getNetworkCapabilities(network)?.let { capabilities ->
                    transportLabel(capabilities)?.let(observedTransports::add)
                }
                networkChanges.trySend(Unit)
                onNetworkState("Réseau disponible détecté. Actualisation du chemin actif…")
            }

            override fun onLost(network: Network) {
                networkLossEvents.incrementAndGet()
                networkChanges.trySend(Unit)
                onNetworkState("Perte du réseau par défaut détectée. Recherche d'un autre chemin…")
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                transportLabel(capabilities)?.let(observedTransports::add)
                networkChanges.trySend(Unit)
                onNetworkState(
                    "Réseau actualisé : " + (transportLabel(capabilities) ?: "type non identifié") +
                        ". Vérification de l'accès Internet…"
                )
            }
        }

        val startedAt = SystemClock.elapsedRealtime()
        try {
            if (manager != null) {
                try {
                    manager.registerDefaultNetworkCallback(callback, Handler(Looper.getMainLooper()))
                    callbackRegistered = true
                    monitoringAvailable = true
                } catch (_: Exception) {
                    monitoringAvailable = false
                    onNetworkState("Surveillance événementielle indisponible ; vérification périodique du réseau activée.")
                }
            }

            for (index in 0 until PROBE_COUNT) {
                currentCoroutineContext().ensureActive()

                val network = awaitValidatedDefaultNetwork(manager, networkChanges, onNetworkState)
                if (network == null) {
                    interruptedForNoValidatedNetwork = true
                    onNetworkState(
                        "Analyse arrêtée : aucun réseau Internet validé n'est revenu après " +
                            NETWORK_RECOVERY_GRACE_MS + " ms."
                    )
                    break
                }

                val capabilities = manager?.getNetworkCapabilities(network)
                val label = transportLabel(capabilities) ?: "Réseau par défaut"
                onNetworkState(label + " actif • sonde " + (index + 1) + "/" + PROBE_COUNT)

                val responseTime = probe(network)
                currentCoroutineContext().ensureActive()

                // A response from a route that ceased to be the Android default during
                // the request is discarded rather than attributed to the new route.
                val stillCurrent = validatedDefaultNetwork(manager) == network
                samples += if (stillCurrent) responseTime else null
                onProgress(index + 1, PROBE_COUNT)

                if (!stillCurrent) {
                    onNetworkState("Changement de chemin détecté. La mesure précédente est écartée ; réévaluation…")
                } else if (index < PROBE_COUNT - 1) {
                    delay(INTERVAL_MS)
                }
            }
        } finally {
            if (callbackRegistered && manager != null) {
                try {
                    manager.unregisterNetworkCallback(callback)
                } catch (_: Exception) {
                    // Callback cleanup is best-effort; the session result remains usable.
                }
            }
            networkChanges.close()
        }

        return GamingTrafficMetricsCalculator.calculate(
            samples = samples,
            defaultNetworkLossEvents = networkLossEvents.get(),
            networkEventMonitoringAvailable = monitoringAvailable,
            durationMs = SystemClock.elapsedRealtime() - startedAt,
            observedTransports = observedTransports,
            interruptedForNoValidatedNetwork = interruptedForNoValidatedNetwork
        )
    }

    /**
     * Returns the active default network only when Android currently marks it as
     * internet-capable and validated. Brief handovers get a bounded recovery window.
     */
    private suspend fun awaitValidatedDefaultNetwork(
        manager: ConnectivityManager?,
        networkChanges: Channel<Unit>,
        onNetworkState: (String) -> Unit
    ): Network? {
        if (manager == null) {
            onNetworkState("Gestionnaire de connectivité indisponible.")
            return null
        }

        val unavailableStartedAt = SystemClock.elapsedRealtime()
        var waitingMessageSent = false

        while (true) {
            currentCoroutineContext().ensureActive()
            val active = validatedDefaultNetwork(manager)
            if (active != null) return active

            if (!waitingMessageSent) {
                onNetworkState("Aucun réseau Internet validé. Attente d'un rétablissement ou d'un basculement…")
                waitingMessageSent = true
            }

            val unavailableDurationMs = SystemClock.elapsedRealtime() - unavailableStartedAt
            if (GamingTrafficSessionPolicy.shouldStopForMissingNetwork(false, unavailableDurationMs)) {
                return null
            }
            val remaining = GamingTrafficSessionPolicy.NETWORK_RECOVERY_GRACE_MS - unavailableDurationMs

            // Callback events wake the check immediately. The short timeout also
            // handles devices where callback registration is unavailable.
            withTimeoutOrNull(minOf(remaining, NETWORK_RECHECK_INTERVAL_MS)) {
                networkChanges.receive()
            }
        }
    }

    private fun validatedDefaultNetwork(manager: ConnectivityManager?): Network? {
        val network = manager?.activeNetwork ?: return null
        val capabilities = manager.getNetworkCapabilities(network) ?: return null
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return network.takeIf { hasInternet && isValidated }
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

    private fun transportLabel(capabilities: NetworkCapabilities?): String? = when {
        capabilities == null -> null
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
        const val NETWORK_RECHECK_INTERVAL_MS = 250L
        const val EXPECTED_STATUS = 204
        const val PROBE_URL = "https://www.google.com/generate_204"
    }
}
