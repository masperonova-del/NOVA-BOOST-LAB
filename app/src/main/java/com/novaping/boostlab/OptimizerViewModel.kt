package com.novaping.boostlab

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class OptimizerUiState(
    val nearbyWifiNetworks: List<NearbyWifiNetwork> = emptyList(),
    val nearbyWifiMessage: String = "Aucune recherche effectuée.",
    val scanningNearbyWifi: Boolean = false,
    val detectedWifiSsid: String? = null,
    val wifiDetectionMessage: String = "Le réseau Wi-Fi connecté sera détecté automatiquement.",
    val profileHistory: WifiProfileSummary = WifiProfileSummary(0, null, null, null, null),
    val archivedRuns: Int = 0,
    val running: Boolean = false,
    val restoring: Boolean = false,
    val progress: String = "Prêt",
    val message: String = "Compare les chemins réseau disponibles, puis applique le meilleur au trafic de NOVA BOOST LAB.",
    val report: OptimizationReport? = null,
    val networkAvailability: NetworkAvailability = NetworkAvailability(
        TransportAvailability(false, false),
        TransportAvailability(false, false),
        TransportAvailability(false, false)
    ),
    val hasValidatedDefaultNetwork: Boolean = false,
    val relayName: String = "Mon relais",
    val relayHealthUrl: String = "",
    val relayProbeRunning: Boolean = false,
    val relayProbeResult: RelayProbeResult? = null,
    val relayProbeMessage: String = "Ajoute l'adresse HTTPS du point de contrôle de ton relais.",
    val tunnelReadiness: TunnelReadiness = TunnelReadiness.WAITING_FOR_VALIDATED_NETWORK,
    val analyzingTraffic: Boolean = false,
    val trafficProgress: Int = 0,
    val trafficMessage: String = "Aucune session d'analyse effectuée.",
    val trafficSummary: GamingTrafficSummary? = null
)

internal class OptimizerViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val optimizer = NetworkPathOptimizer(appContext)
    private val historyStore = WifiProfileHistory(appContext)
    private val nearbyWifiScanner = NearbyWifiScanner(appContext)
    private val trafficAnalyzer = GamingTrafficAnalyzer(appContext)
    private val relayProbe = RelayEndpointProbe()
    private val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val _uiState = MutableStateFlow(
        OptimizerUiState(
            profileHistory = historyStore.summary(""),
            archivedRuns = historyStore.legacyArchiveRunCount(),
            networkAvailability = optimizer.readNetworkAvailability()
        )
    )
    val uiState = _uiState.asStateFlow()

    private var activeWifiProfileId: String? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var nearbyScanJob: Job? = null
    private var optimizationJob: Job? = null
    private var trafficJob: Job? = null
    private var relayJob: Job? = null

    fun startMonitoring() {
        val manager = connectivityManager ?: return refreshNetworkSnapshot()
        if (networkCallback != null) {
            refreshNetworkSnapshot()
            return
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = onNetworkChanged()
            override fun onLost(network: Network) = onNetworkChanged()
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = onNetworkChanged()
        }
        try {
            manager.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                callback,
                Handler(Looper.getMainLooper())
            )
            networkCallback = callback
        } catch (_: Exception) {
            // The initial snapshot remains available if live monitoring is restricted.
        }
        refreshNetworkSnapshot()
    }

    fun stopMonitoring() {
        val callback = networkCallback ?: return
        networkCallback = null
        try {
            connectivityManager?.unregisterNetworkCallback(callback)
        } catch (_: Exception) {
            // ViewModel cleanup must remain safe if Android already removed the callback.
        }
    }

    override fun onCleared() {
        stopMonitoring()
        super.onCleared()
    }

    private fun onNetworkChanged() {
        refreshNetworkSnapshot()
        if (hasFineLocationPermission()) refreshConnectedWifi()
    }

    private fun refreshNetworkSnapshot() {
        val manager = connectivityManager
        val defaultNetwork = manager?.activeNetwork
        val capabilities = defaultNetwork?.let { manager.getNetworkCapabilities(it) }
        val validated = capabilities?.let {
            it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } == true
        val current = _uiState.value
        val readiness = TunnelReadinessEvaluator.evaluate(
            TunnelPrerequisites(
                validatedNetworkAvailable = validated,
                relayHealthVerified = current.relayProbeResult?.reachable == true,
                relayProtocolCompatible = false,
                vpnConsentGranted = false,
                anotherVpnActive = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            )
        )
        _uiState.update {
            it.copy(
                networkAvailability = optimizer.readNetworkAvailability(),
                hasValidatedDefaultNetwork = validated,
                tunnelReadiness = readiness
            )
        }
    }

    fun refreshConnectedWifi() {
        if (!hasFineLocationPermission()) {
            onWifiPermissionDenied()
            return
        }
        @Suppress("DEPRECATION")
        val rawSsid = runCatching { wifiManager?.connectionInfo?.ssid }.getOrNull()
        val ssid = rawSsid?.takeIf {
            it.isNotBlank() && it != WifiManager.UNKNOWN_SSID && it != "<unknown ssid>"
        }?.removeSurrounding("\"")
        if (ssid != null) {
            val profileId = historyStore.profileIdForSsid(ssid)
            activeWifiProfileId = profileId
            _uiState.update {
                it.copy(
                    detectedWifiSsid = ssid,
                    profileHistory = historyStore.summary(profileId),
                    wifiDetectionMessage = "Réseau détecté : $ssid. Historique associé automatiquement."
                )
            }
        } else {
            activeWifiProfileId = null
            _uiState.update {
                it.copy(
                    detectedWifiSsid = null,
                    profileHistory = historyStore.summary(""),
                    wifiDetectionMessage = "Nom Wi-Fi indisponible. Aucune mesure ne sera attribuée à un profil arbitraire. Vérifie les autorisations et les services de localisation Android."
                )
            }
        }
    }

    fun onWifiPermissionDenied() {
        activeWifiProfileId = null
        _uiState.update {
            it.copy(
                detectedWifiSsid = null,
                profileHistory = historyStore.summary(""),
                wifiDetectionMessage = "Autorisation refusée. L’historique par réseau nécessite que le nom du Wi-Fi soit accessible à Android."
            )
        }
    }

    fun onNearbyWifiPermissionDenied() {
        _uiState.update {
            it.copy(nearbyWifiMessage = "Autorisation refusée : Android ne permet pas d'afficher les réseaux Wi-Fi à proximité.")
        }
    }

    fun scanNearbyWifi() {
        if (nearbyScanJob?.isActive == true) return
        nearbyScanJob = viewModelScope.launch {
            _uiState.update { it.copy(scanningNearbyWifi = true) }
            try {
                val scan = nearbyWifiScanner.scan()
                _uiState.update {
                    it.copy(nearbyWifiNetworks = scan.networks, nearbyWifiMessage = scan.message)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(nearbyWifiMessage = "Scan impossible : ${error.message ?: "erreur inconnue"}.")
                }
            } finally {
                _uiState.update { it.copy(scanningNearbyWifi = false) }
                nearbyScanJob = null
            }
        }
    }

    fun runTrafficAnalysis() {
        if (trafficJob?.isActive == true) return
        trafficJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    analyzingTraffic = true,
                    trafficProgress = 0,
                    trafficSummary = null,
                    trafficMessage = "Préparation de la session…"
                )
            }
            try {
                val result = trafficAnalyzer.runSession(
                    onProgress = { completed, _ ->
                        _uiState.update { it.copy(trafficProgress = completed) }
                    },
                    onNetworkState = { status ->
                        _uiState.update { it.copy(trafficMessage = status) }
                    }
                )
                _uiState.update {
                    it.copy(
                        trafficSummary = result,
                        trafficMessage = if (result.interruptedForNoValidatedNetwork) {
                            "Analyse arrêtée : aucun réseau validé. ${result.totalProbes}/20 sondes effectuées en ${result.durationMs / 1000L} s."
                        } else {
                            "Session terminée : ${result.totalProbes}/20 sondes en ${result.durationMs / 1000L} s."
                        }
                    )
                }
            } catch (cancelled: CancellationException) {
                _uiState.update { it.copy(trafficMessage = "Analyse annulée.") }
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(trafficMessage = "Analyse interrompue : ${error.message ?: "erreur inconnue"}.")
                }
            } finally {
                _uiState.update { it.copy(analyzingTraffic = false) }
                trafficJob = null
            }
        }
    }

    fun cancelTrafficAnalysis() {
        trafficJob?.cancel()
    }

    fun optimizeProcessPath() {
        if (optimizationJob?.isActive == true) return
        if (hasFineLocationPermission()) refreshConnectedWifi() else onWifiPermissionDenied()
        optimizationJob = viewModelScope.launch {
            _uiState.update {
                it.copy(running = true, report = null, progress = "Recherche des chemins disponibles…")
            }
            try {
                val profileIdForThisRun = activeWifiProfileId
                val result = optimizer.optimizeProcessPath { status ->
                    _uiState.update { it.copy(progress = status) }
                }
                result.measurements.firstOrNull { it.isCurrent && it.label == "Wi-Fi" }?.let { currentWifi ->
                    if (profileIdForThisRun != null) {
                        historyStore.record(profileIdForThisRun, currentWifi)
                        _uiState.update { it.copy(profileHistory = historyStore.summary(profileIdForThisRun)) }
                    } else {
                        _uiState.update {
                            it.copy(wifiDetectionMessage = "Mesure effectuée, mais historique non enregistré : Android n’a pas fourni le nom du réseau.")
                        }
                    }
                }
                _uiState.update {
                    it.copy(
                        report = result,
                        networkAvailability = optimizer.readNetworkAvailability(),
                        message = result.message,
                        progress = when {
                            !result.applied -> "ÉCHEC — AUCUN CHANGEMENT CONFIRMÉ"
                            result.changed -> "CHEMIN MODIFIÉ ET VÉRIFIÉ"
                            else -> "CHEMIN COURANT CONSERVÉ ET VÉRIFIÉ"
                        }
                    )
                }
                refreshNetworkSnapshot()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        message = "L'optimisation n'a pas pu être terminée : ${error.message ?: "erreur inconnue"}",
                        progress = "Échec"
                    )
                }
            } finally {
                _uiState.update { it.copy(running = false) }
                optimizationJob = null
            }
        }
    }

    fun restoreDefaultPath() {
        if (_uiState.value.restoring) return
        viewModelScope.launch {
            _uiState.update { it.copy(restoring = true) }
            try {
                val restored = withContext(Dispatchers.IO) { optimizer.restoreDefaultPath() }
                _uiState.update {
                    it.copy(
                        networkAvailability = optimizer.readNetworkAvailability(),
                        message = if (restored) {
                            "Liaison personnalisée retirée. Android reprend le chemin réseau par défaut pour NOVA BOOST LAB."
                        } else {
                            "Android n'a pas confirmé la restauration du chemin par défaut."
                        },
                        progress = "Restauration"
                    )
                }
                refreshNetworkSnapshot()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(message = "Restauration impossible : ${error.message ?: "erreur inconnue"}") }
            } finally {
                _uiState.update { it.copy(restoring = false) }
            }
        }
    }

    fun updateRelayName(value: String) {
        _uiState.update { it.copy(relayName = value) }
    }

    fun updateRelayHealthUrl(value: String) {
        _uiState.update {
            it.copy(
                relayHealthUrl = value,
                relayProbeResult = null,
                relayProbeMessage = "Adresse modifiée : relance le contrôle HTTPS."
            )
        }
        refreshTunnelReadiness()
    }

    fun checkRelayEndpoint() {
        if (relayJob?.isActive == true) return
        val current = _uiState.value
        when (val validation = RelayEndpointValidator.validate(current.relayName, current.relayHealthUrl)) {
            is RelayValidation.Invalid -> {
                _uiState.update {
                    it.copy(relayProbeResult = null, relayProbeMessage = validation.reason)
                }
                refreshTunnelReadiness()
            }
            is RelayValidation.Valid -> {
                relayJob = viewModelScope.launch {
                    _uiState.update {
                        it.copy(
                            relayProbeRunning = true,
                            relayProbeResult = null,
                            relayProbeMessage = "Vérification HTTPS en cours…"
                        )
                    }
                    refreshTunnelReadiness()
                    try {
                        val result = relayProbe.check(validation.endpoint)
                        _uiState.update { it.copy(relayProbeResult = result, relayProbeMessage = result.message) }
                        refreshTunnelReadiness()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        _uiState.update {
                            it.copy(relayProbeMessage = "Vérification impossible : ${error.message ?: "erreur inconnue"}")
                        }
                    } finally {
                        _uiState.update { it.copy(relayProbeRunning = false) }
                        relayJob = null
                    }
                }
            }
        }
    }

    private fun refreshTunnelReadiness() {
        val manager = connectivityManager
        val network = manager?.activeNetwork
        val capabilities = network?.let { manager.getNetworkCapabilities(it) }
        val current = _uiState.value
        val validated = capabilities?.let {
            it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } == true
        val readiness = TunnelReadinessEvaluator.evaluate(
            TunnelPrerequisites(
                validatedNetworkAvailable = validated,
                relayHealthVerified = current.relayProbeResult?.reachable == true,
                relayProtocolCompatible = false,
                vpnConsentGranted = false,
                anotherVpnActive = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            )
        )
        _uiState.update { it.copy(tunnelReadiness = readiness, hasValidatedDefaultNetwork = validated) }
    }

    private fun hasFineLocationPermission(): Boolean =
        appContext.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
}
