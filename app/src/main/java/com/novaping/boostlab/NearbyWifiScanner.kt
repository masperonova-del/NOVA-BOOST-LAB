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
            lateinit var receiver: BroadcastReceiver
            lateinit var finish: (WifiScanReport) -> Unit

            val timeout = Runnable {
                finish(WifiScanReport(
                    readResults(),
                    false,
                    "Aucune actualisation reçue dans le délai Android. Les résultats affichés peuvent être anciens."
                ))
            }

            fun unregister() {
                handler.removeCallbacks(timeout)
                if (receiverRegistered) {
                    runCatching { appContext.unregisterReceiver(receiver) }
                    receiverRegistered = false
                }
            }

            finish = { report ->
                unregister()
                if (continuation.isActive) continuation.resume(report)
            }

            receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val updated = intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) == true
                    val message = if (updated) {
                        "Recherche actualisée : réseaux Wi-Fi détectés à proximité."
                    } else {
                        "Android a renvoyé des résultats en cache ; ils peuvent ne pas refléter l'état actuel."
                    }
                    finish(WifiScanReport(readResults(), updated, message))
                }
            }

            try {
                val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    @Suppress("DEPRECATION")
                    appContext.registerReceiver(receiver, filter)
                }
                receiverRegistered = true
                val accepted = runCatching { wifiManager.startScan() }.getOrDefault(false)
                if (!accepted) {
                    finish(WifiScanReport(
                        readResults(),
                        false,
                        "Android a limité ou refusé une nouvelle recherche. Les résultats éventuellement affichés proviennent du cache."
                    ))
                } else {
                    handler.postDelayed(timeout, SCAN_TIMEOUT_MS)
                }
            } catch (error: Exception) {
                finish(WifiScanReport(
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
            val capabilities = result.capabilities.orEmpty().uppercase()
            val security = when {
                "SAE" in capabilities -> "WPA3"
                "EAP" in capabilities -> "Entreprise"
                "PSK" in capabilities -> "WPA/WPA2"
                "WEP" in capabilities -> "WEP"
                else -> "Ouvert / non identifié"
            }
            NearbyWifiNetwork(ssid, result.level, result.frequency, security)
        })
    }

    private companion object {
        const val SCAN_TIMEOUT_MS = 12_000L
    }
}
