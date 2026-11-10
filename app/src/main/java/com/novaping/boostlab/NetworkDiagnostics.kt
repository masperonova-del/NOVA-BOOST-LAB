package com.novaping.boostlab

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

internal const val PROBE_ATTEMPTS = 5

internal data class ProbeStats(
    val attempts: Int,
    val timesMs: List<Long>
) {
    val successes: Int get() = timesMs.size
    val minMs: Long? get() = timesMs.minOrNull()
    val maxMs: Long? get() = timesMs.maxOrNull()

    val medianMs: Long?
        get() {
            val sorted = timesMs.sorted()
            if (sorted.isEmpty()) return null
            val middle = sorted.size / 2
            return if (sorted.size % 2 == 1) sorted[middle]
            else (sorted[middle - 1] + sorted[middle]) / 2
        }
}

internal data class NetworkSnapshot(
    val label: String,
    val hasInternet: Boolean,
    val validated: Boolean
)

internal data class NetworkTestResult(
    val tcp: ProbeStats,
    val https: ProbeStats,
    val network: NetworkSnapshot,
    val completedAtMillis: Long
)

internal fun readNetworkSnapshot(context: Context): NetworkSnapshot {
    val manager = context.getSystemService(ConnectivityManager::class.java)
        ?: return NetworkSnapshot("État indisponible", false, false)
    val activeNetwork = manager.activeNetwork
        ?: return NetworkSnapshot("Aucun réseau actif", false, false)
    val capabilities = manager.getNetworkCapabilities(activeNetwork)
        ?: return NetworkSnapshot("Réseau non identifié", false, false)

    val label = when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN externe"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi‑Fi"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Données mobiles"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
        else -> "Autre réseau"
    }

    return NetworkSnapshot(
        label = label,
        hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
        validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    )
}

internal suspend fun runNetworkChecks(
    context: Context,
    onProgress: (String) -> Unit
): NetworkTestResult {
    val tcpTimes = mutableListOf<Long>()
    val httpsTimes = mutableListOf<Long>()

    repeat(PROBE_ATTEMPTS) { index ->
        onProgress("TCP Connect • essai ${index + 1}/$PROBE_ATTEMPTS")
        measureTcpConnect()?.let(tcpTimes::add)
        if (index < PROBE_ATTEMPTS - 1) delay(180)
    }

    repeat(PROBE_ATTEMPTS) { index ->
        onProgress("HTTPS/TLS • essai ${index + 1}/$PROBE_ATTEMPTS")
        measureHttpsRequest()?.let(httpsTimes::add)
        if (index < PROBE_ATTEMPTS - 1) delay(180)
    }

    return NetworkTestResult(
        tcp = ProbeStats(PROBE_ATTEMPTS, tcpTimes),
        https = ProbeStats(PROBE_ATTEMPTS, httpsTimes),
        network = readNetworkSnapshot(context),
        completedAtMillis = System.currentTimeMillis()
    )
}

private suspend fun measureTcpConnect(): Long? = withContext(Dispatchers.IO) {
    runCatching {
        Socket().use { socket ->
            val startedAt = SystemClock.elapsedRealtimeNanos()
            socket.connect(InetSocketAddress("connectivitycheck.gstatic.com", 443), 2_000)
            ((SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000L).coerceAtLeast(1L)
        }
    }.getOrNull()
}

private suspend fun measureHttpsRequest(): Long? = withContext(Dispatchers.IO) {
    runCatching {
        var connection: HttpURLConnection? = null
        try {
            val startedAt = SystemClock.elapsedRealtimeNanos()
            connection = URL("https://www.google.com/generate_204").openConnection() as HttpURLConnection
            connection.connectTimeout = 2_000
            connection.readTimeout = 2_000
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.useCaches = false

            val statusCode = connection.responseCode
            if (statusCode !in 200..299) throw IllegalStateException("Réponse HTTP $statusCode")

            ((SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000L).coerceAtLeast(1L)
        } finally {
            connection?.disconnect()
        }
    }.getOrNull()
}
