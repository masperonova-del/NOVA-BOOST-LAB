package com.novaping.boostlab

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.system.measureTimeMillis

private val Navy = Color(0xFF081321)
private val Panel = Color(0xFF122238)
private val Blue = Color(0xFF55A7FF)
private val Green = Color(0xFF43D9A3)
private val Muted = Color(0xFF9DB0C8)

class MainActivity : ComponentActivity() {
    private var vpnStatus by mutableStateOf("INACTIF")
    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            startService(Intent(this, NovaVpnService::class.java))
            vpnStatus = "AUTORISATION ACCORDÉE"
        } else {
            vpnStatus = "AUTORISATION REFUSÉE"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background = Navy, surface = Panel, primary = Blue,
                secondary = Green, onBackground = Color.White, onSurface = Color.White
            )) {
                LabScreen(
                    vpnStatus = vpnStatus,
                    onVpn = {
                        val intent = VpnService.prepare(this)
                        if (intent != null) vpnPermission.launch(intent)
                        else {
                            startService(Intent(this, NovaVpnService::class.java))
                            vpnStatus = "AUTORISATION DÉJÀ ACCORDÉE"
                        }
                    },
                    onStopVpn = {
                        stopService(Intent(this, NovaVpnService::class.java))
                        vpnStatus = "INACTIF"
                    }
                )
            }
        }
    }
}

@Composable
private fun LabScreen(vpnStatus: String, onVpn: () -> Unit, onStopVpn: () -> Unit) {
    var running by remember { mutableStateOf(false) }
    var tcpMs by remember { mutableStateOf<Long?>(null) }
    var httpsMs by remember { mutableStateOf<Long?>(null) }
    var testMessage by remember { mutableStateOf("Aucun test effectué") }
    val scope = rememberCoroutineScope()

    Surface(Modifier.fillMaxSize(), color = Navy) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 24.dp, bottom = 28.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = Color(0xFF173B63), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Default.Bolt, null, tint = Blue, modifier = Modifier.padding(12.dp).size(28.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("NOVA BOOST LAB", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                        Text("NETWORK ROUTING EXPERIMENTAL", color = Muted, fontSize = 10.sp, letterSpacing = 1.2.sp)
                    }
                    Surface(color = Color(0xFF174A3C), shape = RoundedCornerShape(50)) {
                        Text("LAB", color = Green, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 11.sp)
                    }
                }
            }
            item {
                Text("MESURES RÉSEAU", color = Muted, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("TCP CONNECT", tcpMs?.let { "$it ms" } ?: "—", Modifier.weight(1f))
                    MetricCard("HTTPS/TLS", httpsMs?.let { "$it ms" } ?: "—", Modifier.weight(1f))
                }
                Text("Ces mesures évaluent l'accès aux services de test, pas le ping UDP de COD Mobile.", color = Muted, fontSize = 11.sp)
            }
            item {
                PanelCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Route, null, tint = Blue)
                        Spacer(Modifier.width(8.dp))
                        Text("COMPARATEUR DE ROUTES", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    RouteLine("Connexion directe", tcpMs?.let { "$it ms TCP" } ?: "En attente", Blue)
                    RouteLine("Relais NOVA", "Non configuré", Muted)
                    Text("Aucun relais n'est encore déployé : aucune route alternative réelle n'est simulée.", color = Muted, fontSize = 11.sp)
                    Button(
                        onClick = {
                            scope.launch {
                                running = true
                                testMessage = "Mesures en cours…"
                                val result = withContext(Dispatchers.IO) { runNetworkChecks() }
                                tcpMs = result.first
                                httpsMs = result.second
                                testMessage = if (result.first != null || result.second != null) "Test terminé" else "Échec des mesures — vérifier Internet"
                                running = false
                            }
                        },
                        enabled = !running,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (running) "TEST EN COURS…" else "LANCER LES TESTS")
                    }
                    Text(testMessage, color = Muted, fontSize = 12.sp)
                }
            }
            item {
                PanelCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, null, tint = Green)
                        Spacer(Modifier.width(8.dp))
                        Text("VPN CONTROL", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("État du VPN", color = Muted)
                        Text(vpnStatus, color = if (vpnStatus == "INACTIF") Muted else Green, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Text("Le service est un squelette de démonstration : il ne transporte pas encore le trafic et ne fournit aucun relais.", color = Muted, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onVpn, modifier = Modifier.weight(1f)) { Text("AUTORISER VPN") }
                        OutlinedButton(onClick = onStopVpn, modifier = Modifier.weight(1f)) { Text("ARRÊTER") }
                    }
                }
            }
            item {
                PanelCard {
                    Text("ÉTAPES SUIVANTES", fontWeight = FontWeight.Bold)
                    Text("1. Ajouter des tests de routes/hops compatibles avec le réseau.\n2. Déployer un relais NOVA réel.\n3. Implémenter le tunnel VPN complet et chiffré.\n4. Comparer les résultats directs et via relais.", color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
                }
            }
            item {
                Text("NOVA BOOST LAB 0.1.0 • APPLICATION DE TEST INDÉPENDANTE", color = Muted, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = Panel, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(15.dp)) {
            Text(label, color = Muted, fontSize = 10.sp)
            Spacer(Modifier.height(6.dp))
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PanelCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun RouteLine(name: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(50)))
        Spacer(Modifier.width(9.dp))
        Text(name, Modifier.weight(1f), fontSize = 13.sp)
        Text(value, color = color, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}

private fun runNetworkChecks(): Pair<Long?, Long?> {
    val tcp = runCatching {
        var elapsed = 0L
        Socket().use { socket ->
            elapsed = measureTimeMillis {
                socket.connect(InetSocketAddress("connectivitycheck.gstatic.com", 443), 3500)
            }
        }
        elapsed
    }.getOrNull()

    val https = runCatching {
        var elapsed = 0L
        val connection = java.net.URL("https://www.google.com/generate_204").openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 3500
        connection.readTimeout = 3500
        elapsed = measureTimeMillis {
            connection.getInputStream().use { it.readBytes() }
        }
        connection.disconnect()
        elapsed
    }.getOrNull()
    return tcp to https
}
