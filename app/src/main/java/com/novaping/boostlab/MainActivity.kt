package com.novaping.boostlab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                LabScreen()
            }
        }
    }
}

@Composable
private fun LabScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var network by remember { mutableStateOf(readNetworkSnapshot(context)) }
    var result by remember { mutableStateOf<NetworkTestResult?>(null) }
    var isRunning by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("Prêt à mesurer") }
    var message by remember { mutableStateOf("Lance un diagnostic pour obtenir tes premières mesures.") }

    Surface(modifier = Modifier.fillMaxSize(), color = Navy) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 24.dp, bottom = 30.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = PanelRaised, shape = RoundedCornerShape(16.dp)) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "NOVA",
                            tint = Blue,
                            modifier = Modifier.padding(13.dp).size(30.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("NOVA BOOST LAB", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        Text("NETWORK DIAGNOSTICS", color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
                    }
                    Surface(color = Color(0xFF173B36), shape = CircleShape) {
                        Text(
                            "0.2.0",
                            color = Mint,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Ta connexion, mesurée clairement.", fontSize = 25.sp, fontWeight = FontWeight.Bold, lineHeight = 31.sp)
                    Text(
                        "Un diagnostic en plusieurs essais pour mieux comprendre la réactivité et la stabilité de ton accès Internet.",
                        color = Muted,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }
            }

            item {
                PanelCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(10.dp).background(
                                if (network.validated) Mint else if (network.hasInternet) Blue else Danger,
                                CircleShape
                            )
                        )
                        Spacer(Modifier.width(9.dp))
                        Text("ÉTAT DU RÉSEAU", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text(
                            when {
                                network.validated -> "Internet validé"
                                network.hasInternet -> "Validation absente"
                                else -> "À vérifier"
                            },
                            color = when {
                                network.validated -> Mint
                                network.hasInternet -> Blue
                                else -> Danger
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(11.dp))
                    Text(network.label, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "État communiqué par Android. Il ne garantit pas l'accès à chaque serveur.",
                        color = Muted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            item {
                PanelCard {
                    Text("DIAGNOSTIC RÉSEAU", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "5 essais TCP et 5 essais HTTPS. Les résultats affichent la médiane, les extrêmes et les réponses reçues.",
                        color = Muted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                isRunning = true
                                result = null
                                message = "Mesures en cours. Garde l'application ouverte."
                                progress = "Préparation du diagnostic…"
                                network = readNetworkSnapshot(context)
                                try {
                                    val measured = runNetworkChecks(context) { step -> progress = step }
                                    result = measured
                                    network = measured.network
                                    val total = measured.tcp.successes + measured.https.successes
                                    message = "Diagnostic terminé : $total réponse(s) sur 10."
                                    progress = "Terminé"
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    message = "Le diagnostic n'a pas pu se terminer."
                                    progress = error.message ?: "Erreur inconnue"
                                } finally {
                                    isRunning = false
                                }
                            }
                        },
                        enabled = !isRunning,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(13.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(7.dp))
                        Text(if (isRunning) "MESURES EN COURS…" else "LANCER LE DIAGNOSTIC")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(progress, color = if (isRunning) Blue else Muted, fontSize = 11.sp)
                    Text(message, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }

            item {
                Text("RÉSULTATS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("TCP CONNECT", result?.tcp, Modifier.weight(1f))
                    MetricCard("HTTPS / TLS", result?.https, Modifier.weight(1f))
                }
                result?.let {
                    Spacer(Modifier.height(7.dp))
                    Text(
                        "Dernier diagnostic : " + SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                            .format(Date(it.completedAtMillis)),
                        color = Muted,
                        fontSize = 10.sp
                    )
                }
            }

            item {
                PanelCard {
                    Text("COMPRENDRE LES MESURES", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(9.dp))
                    ExplanationRow("TCP Connect", "Temps nécessaire pour établir une connexion TCP avec connectivitycheck.gstatic.com.")
                    HorizontalDivider(color = Border, modifier = Modifier.padding(vertical = 9.dp))
                    ExplanationRow("HTTPS / TLS", "Durée jusqu'à la réception de la réponse HTTP du serveur de test. DNS, TCP, TLS et serveur peuvent tous influencer ce temps.")
                    HorizontalDivider(color = Border, modifier = Modifier.padding(vertical = 9.dp))
                    ExplanationRow("Médiane", "Valeur centrale des essais réussis. Elle est moins sensible qu'une moyenne à un essai exceptionnellement lent.")
                }
            }

            item {
                Surface(color = PanelRaised, shape = RoundedCornerShape(15.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("LIMITES IMPORTANTES", color = Blue, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text(
                            "Ces mesures ne sont pas le ping UDP de Call of Duty Mobile. Elles ne prouvent pas qu'une connexion est accélérée. Aucun VPN, relais distant ou changement de routage n'est activé dans cette version.",
                            color = Muted,
                            fontSize = 11.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            item {
                Text(
                    "NOVA BOOST LAB 0.2.0 • DIAGNOSTIC UNIQUEMENT",
                    color = Muted,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, stats: ProbeStats?, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Panel,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        Column(Modifier.padding(13.dp)) {
            Text(label, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(9.dp))
            Text(
                stats?.medianMs?.let(::formatMillis) ?: "—",
                color = Color.White,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold
            )
            Text("MÉDIANE", color = Muted, fontSize = 9.sp)
            Spacer(Modifier.height(11.dp))
            HorizontalDivider(color = Border)
            Spacer(Modifier.height(8.dp))
            Text(
                if (stats == null) "Aucun test" else "${stats.successes}/${stats.attempts} réponses",
                color = if (stats != null && stats.successes == stats.attempts) Mint else Muted,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                if (stats == null) "Min —  •  Max —" else "Min ${formatMillis(stats.minMs)}  •  Max ${formatMillis(stats.maxMs)}",
                color = Muted,
                fontSize = 9.sp
            )
        }
    }
}

@Composable
private fun PanelCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = Panel,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun ExplanationRow(title: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        Text(detail, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
    }
}

private fun formatMillis(value: Long?): String = when {
    value == null -> "—"
    value < 1L -> "<1 ms"
    else -> "$value ms"
}
