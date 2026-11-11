package com.novaping.boostlab

import android.Manifest
import android.content.pm.PackageManager
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

@Composable
internal fun OptimizerScreen(viewModel: OptimizerViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val nearbyWifiPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) viewModel.scanNearbyWifi()
        else viewModel.onNearbyWifiPermissionDenied()
    }
    val wifiPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) viewModel.refreshConnectedWifi()
        else viewModel.onWifiPermissionDenied()
    }

    LaunchedEffect(viewModel) {
        viewModel.startMonitoring()
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            viewModel.refreshConnectedWifi()
        }
    }
    DisposableEffect(viewModel) {
        onDispose { viewModel.stopMonitoring() }
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
                        "0.6.0",
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
                NetworkStatusRow("Wi-Fi", state.networkAvailability.wifi)
                NetworkStatusRow("Données mobiles", state.networkAvailability.cellular)
                NetworkStatusRow("Ethernet", state.networkAvailability.ethernet)
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
                    "Lance jusqu'à 20 sondes HTTPS sur le réseau Android par défaut. Le moteur suit les changements de réseau : il reprend sur le nouveau chemin disponible et s'arrête si aucun réseau Internet validé ne revient après 2,5 secondes. Les résultats décrivent la connexion de NOVA BOOST LAB, pas le ping ni les paquets de CODM.",
                    color = Muted, fontSize = 11.sp, lineHeight = 16.sp
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        if (state.state.analyzingTraffic) viewModel.cancelTrafficAnalysis()
                        else viewModel.runTrafficAnalysis()
                    },
                    enabled = !state.state.running && !state.state.scanningNearbyWifi,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (state.analyzingTraffic) "ARRÊTER L'ANALYSE" else "LANCER L'ANALYSE GAMING")
                }
                if (state.analyzingTraffic) {
                    LinearProgressIndicator(
                        state.progress = state.trafficProgress / 20f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(state.trafficMessage, color = Blue, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                state.trafficSummary?.let { summary ->
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = Border)
                    Spacer(Modifier.height(8.dp))
                    MetricLine("Sondes HTTPS réussies", "${summary.successfulProbes}/${summary.totalProbes} (${summary.successRatePercent} %)")
                    MetricLine("Sondes en échec", summary.failedProbes.toString())
                    MetricLine("Temps de réponse médian", summary.medianResponseMs?.let { "$it ms" } ?: "Indisponible")
                    MetricLine("Variation médiane des réponses", summary.medianResponseVariationMs?.let { "$it ms" } ?: "Données insuffisantes")
                    MetricLine(
                        "Événements onLost du réseau par défaut",
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
                            viewModel.refreshConnectedWifi()
                        } else {
                            wifiPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        }
                    },
                    enabled = !state.state.running && !state.state.analyzingTraffic,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (state.detectedWifiSsid == null) "DÉTECTER LE WI-FI CONNECTÉ" else "ACTUALISER LE WI-FI DÉTECTÉ")
                }
                Text(state.wifiDetectionMessage, color = if (state.detectedWifiSsid != null) Mint else Muted, fontSize = 11.sp, lineHeight = 16.sp)
                Spacer(Modifier.height(8.dp))
                if (state.detectedWifiSsid != null) {
                    Text("Réseau connecté : $state.detectedWifiSsid", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Text(
                        "Aucun nom de réseau exploitable pour le moment. L’historique restera en attente : aucun profil prédéfini ou nom inventé ne sera utilisé.",
                        color = Muted, fontSize = 11.sp, lineHeight = 16.sp
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (state.profileHistory.runCount == 0) {
                    Text("Aucune mesure enregistrée pour ce profil.", color = Muted, fontSize = 12.sp)
                } else {
                    Text(
                        "Tests enregistrés : ${state.profileHistory.runCount}",
                        color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Médiane de l'historique : ${state.profileHistory.medianMs} ms • dispersion médiane : ${state.profileHistory.jitterMs} ms",
                        color = Mint, fontSize = 12.sp
                    )
                    Text(
                        "Dernier test : ${state.profileHistory.lastMedianMs} ms • dispersion ${state.profileHistory.lastJitterMs} ms",
                        color = Muted, fontSize = 11.sp
                    )
                }
                Text(
                    "Historique local limité aux 12 dernières mesures par profil. Il reste sur cet appareil.",
                    color = Muted, fontSize = 10.sp
                )
                val archivedRuns = state.archivedRuns
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
                            viewModel.scanNearbyWifi()
                        } else {
                            nearbyWifiPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        }
                    },
                    enabled = !state.state.scanningNearbyWifi && !state.state.running && !state.state.analyzingTraffic,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (state.scanningNearbyWifi) "RECHERCHE EN COURS…" else "RECHERCHER LES RÉSEAUX AUTOUR")
                }
                if (state.scanningNearbyWifi) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Text(state.nearbyWifiMessage, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                if (state.nearbyWifiNetworks.isEmpty()) {
                    Text("Aucun réseau Wi-Fi visible retourné par Android.", color = Muted, fontSize = 11.sp)
                } else {
                    Spacer(Modifier.height(6.dp))
                    state.nearbyWifiNetworks.forEach { candidate ->
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
                    onClick = { viewModel.optimizeProcessPath() },
                    enabled = !state.state.running && !state.state.restoring && !state.state.analyzingTraffic,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.running) "OPTIMISATION EN COURS…" else "LANCER L'OPTIMISATION")
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    state.progress,
                    color = if (state.report?.applied == true) Mint else Blue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(state.message, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
            }

            state.report?.let { currentReport ->
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
                onClick = { viewModel.restoreDefaultPath() },
                enabled = !state.state.running && !state.state.restoring,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(13.dp)
            ) {
                Icon(Icons.Default.Restore, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("RESTAURER LE CHEMIN PAR DÉFAUT")
            }

            PanelCard {
                Text("INFRASTRUCTURE RELAY", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                Text(
                    "Ajoute l'adresse HTTPS du point de contrôle fourni par l'opérateur du relais. Le test vérifie uniquement sa disponibilité et son temps de réponse ; il ne transporte pas le trafic des autres applications.",
                    color = Muted, fontSize = 11.sp, lineHeight = 16.sp
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = state.state.relayName,
                    onValueChange = viewModel::updateRelayName,
                    label = { Text("Nom du relais") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(7.dp))
                OutlinedTextField(
                    value = state.state.relayHealthUrl,
                    onValueChange = viewModel::updateRelayHealthUrl,
                    label = { Text("Adresse HTTPS /health") },
                    placeholder = { Text("https://relay.example.com/health") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(9.dp))
                OutlinedButton(
                    onClick = { viewModel.checkRelayEndpoint() },
                    enabled = !state.state.relayProbeRunning,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (state.relayProbeRunning) "VÉRIFICATION…" else "VÉRIFIER LE POINT DE CONTRÔLE")
                }
                Text(state.relayProbeMessage, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                state.relayProbeResult?.let { result ->
                    Spacer(Modifier.height(6.dp))
                    MetricLine("État HTTPS", if (result.reachable) "Accessible" else "Inaccessible")
                    MetricLine("Code HTTP", result.httpStatus?.toString() ?: "Aucune réponse")
                    MetricLine("Temps de réponse", result.responseMs?.let { "$it ms" } ?: "Indisponible")
                }
                Text(
                    "Sécurité : HTTPS uniquement, sans identifiants intégrés à l'URL. Aucun mot de passe ni jeton n'est demandé.",
                    color = Muted, fontSize = 10.sp, lineHeight = 15.sp
                )
            }

            PanelCard {
                Text("TUNNEL & ROUTAGE ALTERNATIF", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                val readinessMessage = when (state.tunnelReadiness) {
                    TunnelReadiness.READY -> "Prérequis satisfaits. Le démarrage restera désactivé tant que le moteur de transport n'aura pas été intégré."
                    TunnelReadiness.BLOCKED_BY_EXISTING_VPN -> "Un VPN est déjà actif. NOVA BOOST LAB ne tentera pas de le contourner."
                    TunnelReadiness.WAITING_FOR_VALIDATED_NETWORK -> "Aucun réseau Internet validé n'est disponible."
                    TunnelReadiness.RELAY_HEALTH_CHECK_REQUIRED -> "Vérifie d'abord un point de contrôle HTTPS dans la section Infrastructure Relay."
                    TunnelReadiness.RELAY_PROTOCOL_NOT_IMPLEMENTED -> "Point de contrôle joignable, mais protocole de transport relais non implémenté."
                    TunnelReadiness.VPN_CONSENT_REQUIRED -> "L'autorisation VPN Android sera demandée uniquement lorsqu'un tunnel fonctionnel sera disponible."
                }
                Text(readinessMessage, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                Spacer(Modifier.height(8.dp))
                MetricLine("Réseau par défaut", if (state.hasValidatedDefaultNetwork) "Validé" else "Non validé")
                MetricLine("Point de contrôle", if (state.relayProbeResult?.reachable == true) "HTTPS joignable" else "Non vérifié")
                MetricLine("Transport relais", "Non implémenté")
                MetricLine("Tunnel VPN", "Inactif")
                Text(
                    "Aucun VPN n'est démarré et aucun trafic n'est redirigé dans cette version. Le routage restera fermé par défaut tant qu'un serveur relais compatible et son protocole ne seront pas disponibles.",
                    color = Muted, fontSize = 10.sp, lineHeight = 15.sp
                )
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
