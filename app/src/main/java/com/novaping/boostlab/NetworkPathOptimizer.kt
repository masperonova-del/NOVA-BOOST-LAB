package com.novaping.boostlab

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

internal data class NetworkPathScore(
    val networkId: String,
    val label: String,
    val medianMs: Long,
    val successes: Int,
    val attempts: Int,
    val isCurrent: Boolean
)

internal data class NetworkPathDecision(val selected: NetworkPathScore, val reason: String)

internal object NetworkPathSelector {
    private const val MIN_SUCCESSFUL_PROBES = 2
    private const val MIN_IMPROVEMENT = 0.15

    fun select(candidates: List<NetworkPathScore>): NetworkPathDecision? {
        val eligible = candidates.filter {
            it.medianMs > 0L && it.attempts >= 1 &&
                it.successes >= MIN_SUCCESSFUL_PROBES && it.successes <= it.attempts
        }
        if (eligible.isEmpty()) return null

        val fastest = eligible.minByOrNull { it.medianMs } ?: return null
        val current = eligible.firstOrNull { it.isCurrent }
        if (current == null) {
            return NetworkPathDecision(fastest, "Aucun chemin courant mesurable : sélection du meilleur chemin validé.")
        }
        if (fastest.networkId == current.networkId) {
            return NetworkPathDecision(current, "Le chemin courant est déjà le meilleur parmi les chemins mesurés.")
        }
        val requiredGain = current.medianMs * (1.0 - MIN_IMPROVEMENT)
        if (fastest.medianMs.toDouble() > requiredGain) {
            return NetworkPathDecision(current, "L'autre chemin n'apporte pas au moins 15 % de gain mesuré : conservation du chemin courant.")
        }
        return NetworkPathDecision(fastest, "Le chemin sélectionné présente au moins 15 % de gain sur la médiane des sondes.")
    }
}

internal data class MeasuredNetworkPath(val network: Network, val score: NetworkPathScore)
internal data class OptimizationReport(
    val decision: NetworkPathDecision?,
    val measurements: List<NetworkPathScore>,
    val applied: Boolean,
    val message: String
)

/** Actively binds NOVA BOOST LAB's own process to the best measured validated network. */
internal class NetworkPathOptimizer(context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    suspend fun optimizeProcessPath(onProgress: (String) -> Unit): OptimizationReport {
        val cm = manager ?: return OptimizationReport(null, emptyList(), false, "Gestionnaire réseau indisponible.")
        val current = cm.activeNetwork
        val candidates = cm.allNetworks.mapNotNull { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@mapNotNull null
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return@mapNotNull null
            val label = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Données mobiles"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> return@mapNotNull null
            }
            network to label
        }
        if (candidates.isEmpty()) {
            return OptimizationReport(null, emptyList(), false, "Aucun chemin Wi-Fi, mobile ou Ethernet validé n'est disponible.")
        }

        val measured = mutableListOf<MeasuredNetworkPath>()
        candidates.forEachIndexed { index, pair ->
            val network = pair.first
            val label = pair.second
            onProgress("Mesure de $label (${index + 1}/${candidates.size})")
            val times = measureNetwork(network)
            val score = NetworkPathScore(
                networkId = network.toString(),
                label = label,
                medianMs = median(times) ?: 0L,
                successes = times.size,
                attempts = PROBE_ATTEMPTS,
                isCurrent = network == current
            )
            measured += MeasuredNetworkPath(network, score)
        }

        val decision = NetworkPathSelector.select(measured.map { it.score })
            ?: return OptimizationReport(null, measured.map { it.score }, false, "Aucun chemin n'a obtenu assez de réponses.")
        val selected = measured.firstOrNull { it.score.networkId == decision.selected.networkId }
            ?: return OptimizationReport(decision, measured.map { it.score }, false, "Chemin sélectionné introuvable.")
        val latest = cm.getNetworkCapabilities(selected.network)
        val stillValidated = latest?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            latest.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        if (!stillValidated) {
            return OptimizationReport(decision, measured.map { it.score }, false, "Le réseau sélectionné a changé. Aucune liaison appliquée.")
        }

        val applied = cm.bindProcessToNetwork(selected.network)
        val message = if (applied) {
            "Chemin ${decision.selected.label} appliqué au processus NOVA BOOST LAB. ${decision.reason}"
        } else {
            "Android a refusé la liaison réseau. Le chemin par défaut est conservé."
        }
        return OptimizationReport(decision, measured.map { it.score }, applied, message)
    }

    fun restoreDefaultPath(): Boolean = manager?.bindProcessToNetwork(null) ?: false

    private suspend fun measureNetwork(network: Network): List<Long> {
        val times = mutableListOf<Long>()
        repeat(PROBE_ATTEMPTS) {
            val duration = withContext(Dispatchers.IO) {
                runCatching {
                    val connection = network.openConnection(URL(PROBE_URL)) as HttpURLConnection
                    try {
                        connection.connectTimeout = TIMEOUT_MS
                        connection.readTimeout = TIMEOUT_MS
                        connection.requestMethod = "GET"
                        connection.instanceFollowRedirects = false
                        connection.useCaches = false
                        val start = System.nanoTime()
                        val status = connection.responseCode
                        val elapsed = (System.nanoTime() - start) / 1_000_000L
                        if (status in 200..299) elapsed.coerceAtLeast(1L) else null
                    } finally {
                        connection.disconnect()
                    }
                }.getOrNull()
            }
            if (duration != null) times += duration
            delay(INTERVAL_MS)
        }
        return times
    }

    private fun median(values: List<Long>): Long? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2L
    }

    private companion object {
        const val PROBE_ATTEMPTS = 3
        const val TIMEOUT_MS = 2_500
        const val INTERVAL_MS = 120L
        const val PROBE_URL = "https://www.google.com/generate_204"
    }
}
