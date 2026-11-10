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
    val message: String,
    val changed: Boolean = false
)

/** Selects and verifies a network for NOVA BOOST LAB's process only. */
internal class NetworkPathOptimizer(context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    suspend fun optimizeProcessPath(onProgress: (String) -> Unit): OptimizationReport {
        val cm = manager ?: return failure("Gestionnaire réseau indisponible.")

        // A process binding takes precedence over Android's default active network.
        val processBound = ConnectivityManager.getBoundNetworkForProcess()
        val defaultNetwork = cm.activeNetwork
        val current = processBound ?: defaultNetwork

        // Do not silently route around an active VPN or interfere with its policy.
        val vpnIsActive = listOfNotNull(processBound, defaultNetwork).any { network ->
            cm.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
        if (vpnIsActive) {
            return failure("Un VPN est actif. Optimisation suspendue pour ne pas contourner son routage.")
        }

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
            return failure("Aucun chemin Wi-Fi, mobile ou Ethernet validé n'est disponible.")
        }

        val measured = mutableListOf<MeasuredNetworkPath>()
        candidates.forEachIndexed { index, pair ->
            val network = pair.first
            val label = pair.second
            onProgress("Mesure de $label (${index + 1}/${candidates.size})")
            val times = measureNetwork(network, PROBE_ATTEMPTS)
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

        if (!isStillValidated(cm, selected.network)) {
            return OptimizationReport(decision, measured.map { it.score }, false, "Le réseau sélectionné a changé. Aucune liaison appliquée.")
        }

        // If the selected path is already the process path, report retention—not a switch.
        if (selected.network == current) {
            return OptimizationReport(
                decision, measured.map { it.score }, true,
                "Chemin ${decision.selected.label} conservé : il est déjà le meilleur chemin mesuré. Aucun changement de réseau n'a été effectué.",
                changed = false
            )
        }

        onProgress("Application et vérification du chemin ${decision.selected.label}…")
        val bound = cm.bindProcessToNetwork(selected.network)
        if (!bound) {
            return OptimizationReport(decision, measured.map { it.score }, false, "Android a refusé la liaison réseau. Aucun changement confirmé.")
        }

        // Re-check both Android validation and real HTTPS reachability after binding.
        val postApplyTimes = if (isStillValidated(cm, selected.network)) {
            measureNetwork(selected.network, POST_APPLY_PROBES)
        } else {
            emptyList()
        }
        if (postApplyTimes.size < MIN_POST_APPLY_SUCCESSES) {
            cm.bindProcessToNetwork(null)
            return OptimizationReport(
                decision, measured.map { it.score }, false,
                "Le chemin sélectionné n'a pas passé la vérification après application. Liaison retirée ; Android reprend son chemin par défaut."
            )
        }

        return OptimizationReport(
            decision, measured.map { it.score }, true,
            "Chemin ${decision.selected.label} modifié puis vérifié (${postApplyTimes.size}/${POST_APPLY_PROBES} sondes réussies). ${decision.reason}",
            changed = true
        )
    }

    fun restoreDefaultPath(): Boolean = manager?.bindProcessToNetwork(null) ?: false

    private fun isStillValidated(cm: ConnectivityManager, network: Network): Boolean {
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    private suspend fun measureNetwork(network: Network, attempts: Int): List<Long> {
        val times = mutableListOf<Long>()
        repeat(attempts) {
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

    private fun failure(message: String) =
        OptimizationReport(null, emptyList(), false, message)

    private companion object {
        const val PROBE_ATTEMPTS = 3
        const val POST_APPLY_PROBES = 3
        const val MIN_POST_APPLY_SUCCESSES = 2
        const val TIMEOUT_MS = 2_500
        const val INTERVAL_MS = 120L
        const val PROBE_URL = "https://www.google.com/generate_204"
    }
}
