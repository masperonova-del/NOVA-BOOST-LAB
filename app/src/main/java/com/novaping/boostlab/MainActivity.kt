package com.novaping.boostlab

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val Navy = Color(0xFF07111F)
private val Panel = Color(0xFF101F32)
private val PanelRaised = Color(0xFF172A42)
private val Blue = Color(0xFF63B3FF)
private val Mint = Color(0xFF55E0B5)
private val Muted = Color(0xFF9CAFC5)
private val Border = Color(0xFF263B55)
private val Danger = Color(0xFFFF8C8C)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Navy,
                    surface = Panel,
                    surfaceVariant = PanelRaised,
                    primary = Blue,
                    secondary = Mint,
                    onBackground = Color.White,
                    onSurface = Color.White,
                    onSurfaceVariant = Muted
                )
            ) {
                OptimizerScreen()
            }
        }
    }
}

@Composable
private fun OptimizerScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val optimizer = remember(context) { NetworkPathOptimizer(context.applicationContext) }
    val historyStore = remember(context) { WifiProfileHistory(context.applicationContext) }
    val nearbyWifiScanner = remember(context) { NearbyWifiScanner(context.applicationContext) }
    var nearbyWifiNetworks by remember { mutableStateOf<List<NearbyWifiNetwork>>(emptyList()) }
    var nearbyWifiMessage by remember { mutableStateOf("Aucune recherche effectuée.") }
    var scanningNearbyWifi by remember { mutableStateOf(false) }
    val scanNearbyWifi: () -> Unit = {
        scope.launch {
            scanningNearbyWifi = true
            try {
                val scan = nearbyWifiScanner.scan()
                nearbyWifiNetworks = scan.networks
                nearbyWifiMessage = scan.message
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                nearbyWifiMessage = "Scan impossible : ${error.message ?: "erreur inconnue"}."
            } finally {
                scanningNearbyWifi = false
            }
        }
    }
    val nearbyWifiPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) scanNearbyWifi()
        else nearbyWifiMessage = "Autorisation refusée : Android ne permet pas d'afficher les réseaux Wi-Fi à proximité."
    }
    var detectedWifiSsid by remember { mutableStateOf<String?>(null) }
    var wifiDetectionMessage by remember { mutableStateOf("Le réseau Wi-Fi connecté sera détecté automatiquement.") }
    var activeWifiProfileId by remember { mutableStateOf<String?>(null) }
    var profileHistory by remember { mutableStateOf(historyStore.summary("")) }
    val refreshConnectedWifi: () -> Unit = {
        @Suppress("DEPRECATION")
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val rawSsid = wifiManager.connectionInfo?.ssid
        val ssid = rawSsid?.takeIf { it.isNotBlank() && it != WifiManager.UNKNOWN_SSID && it != "<unknown ssid>" }
            ?.removeSurrounding("\"")
        if (ssid != null) {
            detectedWifiSsid = ssid
            val detectedProfileId = historyStore.profileIdForSsid(ssid)
            activeWifiProfileId = detectedProfileId
            profileHistory = historyStore.summary(detectedProfileId)
            wifiDetectionMessage = "Réseau détecté : $ssid. Historique associé automatiquement."
        } else {
            detectedWifiSsid = null
            activeWifiProfileId = null
            profileHistory = historyStore.summary("")
            wifiDetectionMessage = "Nom Wi-Fi indisponible. Aucune mesure ne sera attribuée à un profil arbitraire. Vérifie les autorisations et les services de localisation Android."
        }
    }
    val wifiPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) refreshConnectedWifi()
        else { activeWifiProfileId = null; profileHistory = historyStore.summary(""); wifiDetectionMessage = "Autorisation refusée. L’historique par réseau nécessite que le nom du Wi-Fi soit accessible à Android." }
    }
    var running by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("Prêt") }
    var message by remember {
        mutableStateOf("Compare les chemins réseau disponibles, puis applique le meilleur au trafic de NOVA BOOST LAB.")
    }
    var report by remember { mutableStateOf<OptimizationReport?>(null) }
    var networkAvailability by remember { mutableStateOf(optimizer.readNetworkAvailability()) }
    val trafficAnalyzer = remember(context) { GamingTrafficAnalyzer(context.applicationContext) }
    var analyzingTraffic by remember { mutableStateOf(false) }
    var trafficJob by remember { mutableStateOf<Job?>(null) }
    var trafficProgress by remember { mutableIntStateOf(0) }
    var trafficMessage by remember { mutableStateOf("Aucune session d'analyse effectuée.") }
    var trafficSummary by remember { mutableStateOf<GamingTrafficSummary?>(null) }

    LaunchedEffect(Unit) {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            refreshConnectedWifi()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Navy) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = PanelRaised, shape = RoundedCornerShape(16.dp)) {
                    Icon(
                        Icons.Default.Bolt,
                        contentDescription = "NOVA BOOST LAB",
                        tint = Blue,
                        modifier = Modifier.padding(13.dp).size(32.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("NOVA BOOST LAB", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text("NETWORK & GAMING ANALYZER", color = Muted, fontSize = 10.sp, letterSpacing = 1.2.sp)
                }
                Surface(color = Color(0xFF173B36), shape = CircleShape) {
                    Text(
                        "0.4.0",
                        color = Mint,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Choisir un meilleur chemin.", fontSize = 25.sp, fontWeight = FontWeight.Bold, lineHeight = 31.sp)
                Text(
                    "Le moteur mesure les réseaux validés disponibles, compare leurs réponses et peut appliquer le chemin retenu au processus NOVA BOOST LAB.",
                    color = Muted, fontSize = 13.sp, lineHeight = 19.sp
                )
            }

            PanelCard {
                Text("ÉTAT DES RÉSEAUX", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                NetworkStatusRow("Wi-Fi", networkAvailability.wifi)
                NetworkStatusRow("Données mobiles", networkAvailability.cellular)
                NetworkStatusRow("Ethernet", networkAvailability.ethernet)
                Text(
                    "Un réseau détecté n'est pas forcément connecté à Internet. Seuls les réseaux validés par Android sont testés par l'optimiseur.",
                    color = Muted, fontSize = 10.sp, lineHeight = 15.sp
                )
            }


            PanelCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("GAMING TRAFFIC ANALYZER", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("20 SONDES", color = Blue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    "Lance une session de 20 sondes HTTPS espacées d'environ 1,5 seconde sur le réseau Android par défaut. L'analyse observe les temps de réponse, leur variation et les événements de perte du réseau signalés par Android.",
                    color = Muted, fontSize = 11.sp, lineHeight = 16.sp
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        if (analyzingTraffic) {
                            trafficJob?.cancel()
                        } else {
                            trafficJob = scope.launch {
                                analyzingTraffic = true
                                trafficProgress = 0
                                trafficSummary = null
                                trafficMessage = "Préparation de la session…"
                                try {
                                    val result = trafficAnalyzer.runSession { completed, total ->
                                        trafficProgress = completed
                                        trafficMessage = "Sonde $completed/$total"
                                    }
                                    trafficSummary = result
                                    trafficMessage = "Session terminée en ${result.durationMs / 1000L} s."
                                } catch (cancelled: CancellationException) {
                                    trafficMessage = "Analyse annulée."
                                    throw cancelled
                                } catch (error: Exception) {
                                    trafficMessage = "Analyse interrompue : ${error.message ?: "erreur inconnue"}."
                                } finally {
                                    analyzingTraffic = false
                                    trafficJob = null
                                }
                            }
                        }
                    },
                    enabled = !running && !scanningNearbyWifi,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (analyzingTraffic) "ARRÊTER L'ANALYSE" else "LANCER L'ANALYSE GAMING")
                }
                if (analyzingTraffic) {
                    LinearProgressIndicator(
                        progress = trafficProgress / 20f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(trafficMessage, color = Blue, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                trafficSummary?.let { summary ->
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = Border)
                    Spacer(Modifier.height(8.dp))
                    MetricLine("Sondes HTTPS réussies", "${summary.successfulProbes}/${summary.totalProbes} (${summary.successRatePercent} %)")
                    MetricLine("Sondes en échec", summary.failedProbes.toString())
                    MetricLine("Temps de réponse médian", summary.medianResponseMs?.let { "$it ms" } ?: "Indisponible")
                    MetricLine("Variation médiane des réponses", summary.medianResponseVariationMs?.let { "$it ms" } ?: "Données insuffisantes")
                    MetricLine(
                        "Événements onLost réseau par défaut",
                        if (summary.networkEventMonitoringAvailable) summary.defaultNetworkLossEvents.toString() else "Surveillance indisponible"
                    )
                    MetricLine("Réseaux observés", summary.observedTransports.joinToString().ifBlank { "Aucun" })
                    Text(
                        "Interprétation : les temps et leur variation proviennent de sondes HTTPS vers un serveur externe. Les échecs sont des échecs de sondes, pas une mesure des pertes de paquets de CODM. Cette analyse ne lit ni ne modifie le trafic d'autres applications.",
                        color = Muted, fontSize = 10.sp, lineHeight = 15.sp
                    )
                }
            }

            PanelCard {
                Text("HISTORIQUE WI-FI", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                Text(
                    "Android peut exiger l’autorisation de localisation et l’activation des services de localisation pour révéler le nom du Wi-Fi. NOVA BOOST LAB ne lit pas les coordonnées GPS ; les identifiants hachés et les mesures restent sur cet appareil.",
                    color = Muted, fontSize = 11.sp, lineHeight = 16.sp
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                            refreshConnectedWifi()
                        } else {
                            wifiPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        }
                    },
                    enabled = !running && !analyzingTraffic,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (detectedWifiSsid == null) "DÉTECTER LE WI-FI CONNECTÉ" else "ACTUALISER LE WI-FI DÉTECTÉ")
                }
                Text(wifiDetectionMessage, color = if (detectedWifiSsid != null) Mint else Muted, fontSize = 11.sp, lineHeight = 16.sp)
                Spacer(Modifier.height(8.dp))
                if (detectedWifiSsid != null) {
                    Text("Réseau connecté : $detectedWifiSsid", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Text(
                        "Aucun nom de réseau exploitable pour le moment. L’historique restera en attente : aucun profil prédéfini ou nom inventé ne sera utilisé.",
                        color = Muted, fontSize = 11.sp, lineHeight = 16.sp
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (profileHistory.runCount == 0) {
                    Text("Aucune mesure enregistrée pour ce profil.", color = Muted, fontSize = 12.sp)
                } else {
                    Text(
                        "Tests enregistrés : ${profileHistory.runCount}",
                        color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Médiane de l'historique : ${profileHistory.medianMs} ms • dispersion médiane : ${profileHistory.jitterMs} ms",
                        color = Mint, fontSize = 12.sp
                    )
                    Text(
                        "Dernier test : ${profileHistory.lastMedianMs} ms • dispersion ${profileHistory.lastJitterMs} ms",
                        color = Muted, fontSize = 11.sp
                    )
                }
                Text(
                    "Historique local limité aux 12 dernières mesures par profil. Il reste sur cet appareil.",
                    color = Muted, fontSize = 10.sp
                )
                val archivedRuns = historyStore.legacyArchiveRunCount()
                if (archivedRuns > 0) {
                    Spacer(Modifier.height(12.dp))
                    Divider(color = Border)
                    Spacer(Modifier.height(8.dp))
                    Text("ANCIENNES MESURES CONSERVÉES", color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "$archivedRuns anciennes mesures manuelles sont conservées localement. Elles ne sont associées à aucun réseau précis et ne sont pas mélangées aux nouveaux historiques automatiques.",
                        color = Muted, fontSize = 10.sp, lineHeight = 15.sp
                    )
                }
            }

            PanelCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("RÉSEAUX WI-FI À PROXIMITÉ", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("SCAN", color = Blue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    "Recherche les points d’accès visibles autour du téléphone. Le classement repose uniquement sur la puissance du signal radio, pas sur le débit, le ping ou la qualité Internet.",
                    color = Muted, fontSize = 11.sp, lineHeight = 16.sp
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                            scanNearbyWifi()
                        } else {
                            nearbyWifiPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        }
                    },
                    enabled = !scanningNearbyWifi && !running && !analyzingTraffic,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (scanningNearbyWifi) "RECHERCHE EN COURS…" else "RECHERCHER LES RÉSEAUX AUTOUR")
                }
                if (scanningNearbyWifi) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Text(nearbyWifiMessage, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                if (nearbyWifiNetworks.isEmpty()) {
                    Text("Aucun réseau Wi-Fi visible retourné par Android.", color = Muted, fontSize = 11.sp)
                } else {
                    Spacer(Modifier.height(6.dp))
                    nearbyWifiNetworks.forEach { candidate ->
                        NearbyWifiRow(candidate)
                        HorizontalDivider(color = Border)
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    "Les résultats dépendent des restrictions de scan Android et peuvent être mis en cache. NOVA BOOST LAB ne tente pas de se connecter à ces réseaux.",
                    color = Muted, fontSize = 10.sp, lineHeight = 15.sp
                )
            }

            PanelCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = Blue)
                    Spacer(Modifier.width(8.dp))
                    Text("OPTIMISATION ACTIVE", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Sept sondes HTTPS par réseau candidat. Le moteur exige au moins 5 réponses sur 7 et compare un score combinant la médiane et la dispersion. Un chemin alternatif doit être au moins 15 % meilleur.",
                    color = Muted, fontSize = 12.sp, lineHeight = 18.sp
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                            refreshConnectedWifi()
                        }
                        val profileIdForThisRun = activeWifiProfileId
                        scope.launch {
                            running = true
                            report = null
                            progress = "Recherche des chemins disponibles…"
                            try {
                                val result = optimizer.optimizeProcessPath { progress = it }
                                report = result
                                networkAvailability = optimizer.readNetworkAvailability()
                                result.measurements.firstOrNull { it.isCurrent && it.label == "Wi-Fi" }?.let { currentWifi ->
                                    if (profileIdForThisRun != null) {
                                        historyStore.record(profileIdForThisRun, currentWifi)
                                        profileHistory = historyStore.summary(profileIdForThisRun)
                                    } else {
                                        wifiDetectionMessage = "Mesure effectuée, mais historique non enregistré : Android n’a pas fourni le nom du réseau."
                                    }
                                }
                                message = result.message
                                progress = when {
                                    !result.applied -> "ÉCHEC — AUCUN CHANGEMENT CONFIRMÉ"
                                    result.changed -> "CHEMIN MODIFIÉ ET VÉRIFIÉ"
                                    else -> "CHEMIN COURANT CONSERVÉ ET VÉRIFIÉ"
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                message = "L'optimisation n'a pas pu être terminée : ${error.message ?: "erreur inconnue"}"
                                progress = "Échec"
                            } finally {
                                running = false
                            }
                        }
                    },
                    enabled = !running && !restoring && !analyzingTraffic,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (running) "OPTIMISATION EN COURS…" else "LANCER L'OPTIMISATION")
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    progress,
                    color = if (report?.applied == true) Mint else Blue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(message, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
            }

            report?.let { currentReport ->
                PanelCard {
                    Text("CHEMINS MESURÉS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    if (currentReport.measurements.isEmpty()) {
                        Text("Aucun chemin exploitable détecté.", color = Muted, fontSize = 12.sp)
                    } else {
                        currentReport.measurements.forEach { path ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        path.label + if (path.isCurrent) " • actuel" else " • alternatif",
                                        color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "${path.successes}/${path.attempts} réponses • dispersion médiane ${path.jitterMs} ms • score ${path.stabilityAdjustedMs} ms",
                                        color = Muted, fontSize = 10.sp
                                    )
                                }
                                Text(
                                    if (path.medianMs > 0L) "${path.medianMs} ms" else "Échec",
                                    color = if (path.successes >= 5) Mint else Danger,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            HorizontalDivider(color = Border)
                        }
                    }
                    currentReport.decision?.let {
                        Spacer(Modifier.height(9.dp))
                        Text("CHEMIN RETENU : ${it.selected.label}", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(it.reason, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    restoring = true
                    val restored = optimizer.restoreDefaultPath()
                    networkAvailability = optimizer.readNetworkAvailability()
                    message = if (restored) {
                        "Liaison personnalisée retirée. Android reprend le chemin réseau par défaut pour NOVA BOOST LAB."
                    } else {
                        "Android n'a pas confirmé la restauration du chemin par défaut."
                    }
                    progress = "Restauration"
                    restoring = false
                },
                enabled = !running && !restoring,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(13.dp)
            ) {
                Icon(Icons.Default.Restore, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("RESTAURER LE CHEMIN PAR DÉFAUT")
            }

            PanelCard {
                Text("PÉRIMÈTRE RÉEL", color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                Text(
                    "Android applique cette liaison au processus NOVA BOOST LAB uniquement. Elle ne modifie pas le routage de Call of Duty Mobile ni celui des autres applications. Les réseaux disponibles et validés simultanément sont comparés ; les autres Wi-Fi sont comparés via leur historique manuel. Aucun réseau mobile n'est activé de force.",
                    color = Muted, fontSize = 11.sp, lineHeight = 17.sp
                )
            }

            Text(
                "NOVA BOOST LAB • ANALYSE RÉSEAU ET SÉLECTION DE CHEMIN",
                color = Muted, fontSize = 9.sp, modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun NearbyWifiRow(network: NearbyWifiNetwork) {
    val signalLabel = when {
        network.signalDbm >= -50 -> "Excellent"
        network.signalDbm >= -60 -> "Très bon"
        network.signalDbm >= -67 -> "Bon"
        network.signalDbm >= -75 -> "Moyen"
        else -> "Faible"
    }
    val band = when (network.frequencyMhz) {
        in 2400..2500 -> "2,4 GHz"
        in 4900..5900 -> "5 GHz"
        in 5925..7125 -> "6 GHz"
        else -> "${network.frequencyMhz} MHz"
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(network.ssid, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("$band • ${network.security}", color = Muted, fontSize = 10.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("$signalLabel • ${network.signalDbm} dBm", color = Blue, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}


@Composable
private fun MetricLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), color = Muted, fontSize = 11.sp)
        Text(value, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun NetworkStatusRow(label: String, status: TransportAvailability) {
    val stateText = when {
        !status.detected -> "Non détecté"
        status.validated -> "Internet validé"
        else -> "Détecté — Internet non validé"
    }
    val stateColor = if (status.validated) Mint else if (status.detected) Blue else Muted
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 12.sp)
        Text(stateText, color = stateColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PanelCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = Panel,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}
