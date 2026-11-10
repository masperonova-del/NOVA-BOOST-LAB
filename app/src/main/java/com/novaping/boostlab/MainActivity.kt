package com.novaping.boostlab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import kotlinx.coroutines.launch

private val Navy = Color(0xFF07111F)
private val Panel = Color(0xFF101F32)
private val PanelRaised = Color(0xFF172A42)
private val Blue = Color(0xFF63B3FF)
private val Mint = Color(0xFF55E0B5)
private val Muted = Color(0xFF9CAFC5)
private val Border = Color(0xFF263B55)
private val Danger = Color(0xFFFF8C8C)

private val wifiProfiles = listOf(
    "wifi_1" to "Wi-Fi 1 — chambre (sans répéteur)",
    "wifi_2" to "Wi-Fi 2 — proche (signal faible)",
    "wifi_3" to "Wi-Fi 3 — chez ta sœur"
)

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
    var selectedWifiProfile by remember { mutableStateOf("wifi_1") }
    var profileMenuExpanded by remember { mutableStateOf(false) }
    var profileHistory by remember { mutableStateOf(historyStore.summary(selectedWifiProfile)) }
    var running by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("Prêt") }
    var message by remember {
        mutableStateOf("Compare les chemins réseau disponibles, puis applique le meilleur au trafic de NOVA BOOST LAB.")
    }
    var report by remember { mutableStateOf<OptimizationReport?>(null) }
    var networkAvailability by remember { mutableStateOf(optimizer.readNetworkAvailability()) }

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
                    Text("ACTIVE NETWORK OPTIMIZER", color = Muted, fontSize = 10.sp, letterSpacing = 1.4.sp)
                }
                Surface(color = Color(0xFF173B36), shape = CircleShape) {
                    Text(
                        "0.3.0",
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
                Text("HISTORIQUE DES TROIS WI-FI", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                Text(
                    "Sélectionne manuellement le profil correspondant au Wi-Fi auquel ton téléphone est connecté avant chaque test. Le profil est une étiquette locale : l'application ne lit pas le nom du réseau.",
                    color = Muted, fontSize = 11.sp, lineHeight = 16.sp
                )
                Spacer(Modifier.height(10.dp))
                Box {
                    OutlinedButton(
                        onClick = { profileMenuExpanded = true },
                        enabled = !running,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(wifiProfiles.first { it.first == selectedWifiProfile }.second)
                    }
                    DropdownMenu(
                        expanded = profileMenuExpanded,
                        onDismissRequest = { profileMenuExpanded = false }
                    ) {
                        wifiProfiles.forEach { (id, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    selectedWifiProfile = id
                                    profileHistory = historyStore.summary(id)
                                    profileMenuExpanded = false
                                }
                            )
                        }
                    }
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
                        scope.launch {
                            running = true
                            report = null
                            progress = "Recherche des chemins disponibles…"
                            try {
                                val result = optimizer.optimizeProcessPath { progress = it }
                                report = result
                                networkAvailability = optimizer.readNetworkAvailability()
                                result.measurements.firstOrNull { it.isCurrent && it.label == "Wi-Fi" }?.let { currentWifi ->
                                    historyStore.record(selectedWifiProfile, currentWifi)
                                    profileHistory = historyStore.summary(selectedWifiProfile)
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
                    enabled = !running && !restoring,
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
                "NOVA BOOST LAB • MOTEUR DE SÉLECTION DE CHEMIN",
                color = Muted, fontSize = 9.sp, modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
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
