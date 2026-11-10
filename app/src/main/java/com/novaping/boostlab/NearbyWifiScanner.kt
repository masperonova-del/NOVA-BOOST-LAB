package com.novaping.boostlab

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

internal data class NearbyWifiNetwork(
    val ssid: String,
    val signalDbm: Int,
    val frequencyMhz: Int,
    val security: String
)

internal data class WifiScanReport(
    val networks: List<NearbyWifiNetwork>,
    val freshResults: Boolean,
    val message: String
)

internal object WifiSecurityClassifier {
    fun classify(capabilities: String): String {
        val flags = capabilities.uppercase()
        return when {
            "SAE" in flags -> "WPA3"
            "EAP" in flags -> "Entreprise"
            "PSK" in flags -> "WPA/WPA2"
            "WEP" in flags -> "WEP"
            "OWE" in flags -> "WPA3 Enhanced Open"
            "ESS" in flags || flags.isBlank() -> "Ouvert"
            else -> "Autre / non identifié"
        }
    }
}

internal object WifiScanStatus {
    fun fromBroadcast(resultsUpdated: Boolean): String =
        if (resultsUpdated) {
            "Recherche actualisée : réseaux Wi-Fi détectés à proximité."
        } else {
            "Android a renvoyé des résultats en cache ; ils peuvent ne pas refléter l'état actuel."
        }

    fun startRejected(): String =
        "Android a limité ou refusé une nouvelle recherche. Les résultats éventuellement affichés proviennent du cache."

    fun timedOut(): String =
        WifiScanStatus.timedOut()
}

internal object WifiSignalRanker {
    fun rank(networks: List<NearbyWifiNetwork>): List<NearbyWifiNetwork> {
        return networks
            .filter { it.ssid.isNotBlank() && it.ssid != "<unknown ssid>" && it.signalDbm in -100..0 && it.frequencyMhz > 0 }
            .groupBy { it.ssid }
            .mapNotNull { (_, sameSsid) -> sameSsid.maxByOrNull { it.signalDbm } }
            .sortedByDescending { it.signalDbm }
    }
}

/** Reads nearby Wi-Fi scan results only; it does not connect or test Internet access. */
internal class NearbyWifiScanner(context: Context) {
    private val appContext = context.applicationContext
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val handler = Handler(Looper.getMainLooper())

    suspend fun scan(): WifiScanReport = withContext(Dispatchers.Main.immediate) {
        if (appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return@withContext WifiScanReport(emptyList(), false, "Autorisation de localisation précise requise par Android pour afficher les résultats Wi-Fi.")
        }
        if (!wifiManager.isWifiEnabled) {
            return@withContext WifiScanReport(emptyList(), false, "Active le Wi-Fi pour rechercher les réseaux à proximité.")
        }
        val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !locationManager.isLocationEnabled) {
            return@withContext WifiScanReport(emptyList(), false, "Active les services de localisation Android : le scan Wi-Fi en dépend sur cet appareil.")
        }

        suspendCancellableCoroutine { continuation ->
            var receiverRegistered = false
            var receiver: BroadcastReceiver? = null
            var timeoutRunnable: Runnable? = null

            fun unregister() {
                timeoutRunnable?.let(handler::removeCallbacks)
                if (receiverRegistered) {
                    receiver?.let { registered -> runCatching { appContext.unregisterReceiver(registered) } }
                    receiverRegistered = false
                }
            }

            fun complete(report: WifiScanReport) {
                unregister()
                if (continuation.isActive) continuation.resume(report)
            }

            timeoutRunnable = Runnable {
                complete(WifiScanReport(
                    readResults(),
                    false,
                    "Aucune actualisation reçue dans le délai Android. Les résultats affichés peuvent être anciens."
                ))
            }

            receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val updated = intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) == true
                    complete(WifiScanReport(readResults(), updated, WifiScanStatus.fromBroadcast(updated)))
                }
            }

            try {
                val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                val scanReceiver = receiver ?: error("Récepteur Wi-Fi indisponible")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    appContext.registerReceiver(scanReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    @Suppress("DEPRECATION")
                    appContext.registerReceiver(scanReceiver, filter)
                }
                receiverRegistered = true
                val accepted = runCatching { wifiManager.startScan() }.getOrDefault(false)
                if (!accepted) {
                    complete(WifiScanReport(
                        readResults(),
                        false,
                        WifiScanStatus.startRejected()
                    ))
                } else {
                    timeoutRunnable?.let { handler.postDelayed(it, SCAN_TIMEOUT_MS) }
                }
            } catch (error: Exception) {
                complete(WifiScanReport(
                    emptyList(),
                    false,
                    "Le scan Wi-Fi n'a pas pu démarrer : ${error.message ?: "erreur Android"}."
                ))
            }

            continuation.invokeOnCancellation { unregister() }
        }
    }

    @Suppress("DEPRECATION")
    private fun readResults(): List<NearbyWifiNetwork> {
        val results = runCatching { wifiManager.scanResults }.getOrDefault(emptyList())
        return WifiSignalRanker.rank(results.mapNotNull { result ->
            val ssid = result.SSID?.trim().orEmpty()
            if (ssid.isBlank() || ssid == "<unknown ssid>") return@mapNotNull null
            val security = WifiSecurityClassifier.classify(result.capabilities.orEmpty())
            NearbyWifiNetwork(ssid, result.level, result.frequency, security)
        })
    }

    private companion object {
        const val SCAN_TIMEOUT_MS = 12_000L
    }
}
