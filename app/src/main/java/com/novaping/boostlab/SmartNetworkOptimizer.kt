package com.novaping.boostlab

internal enum class OptimizerState {
    NO_INTERNET,
    NOT_VALIDATED,
    INSUFFICIENT_DATA,
    UNSTABLE,
    SLOW_RESPONSES,
    STABLE
}

internal enum class OptimizerAction {
    CHECK_CONNECTION,
    RETEST,
    COMPARE_NETWORKS,
    KEEP_CURRENT
}

internal data class OptimizerAssessment(
    val state: OptimizerState,
    val action: OptimizerAction,
    val title: String,
    val explanation: String,
    val actionLabel: String,
    val recommendation: String
)

/**
 * Advisory-only decision engine for version 0.3.
 *
 * It interprets the TCP and HTTPS samples already collected by 0.2.
 * It does not change Android connectivity, DNS, routing, VPN state or traffic.
 */
internal object SmartNetworkOptimizer {
    private const val MIN_SUCCESSFUL_PROBES_PER_KIND = 3
    private const val MIN_SUCCESS_RATIO = 0.80
    private const val MAX_RELATIVE_SPREAD = 0.50
    private const val SLOW_TCP_MEDIAN_MS = 300L
    private const val SLOW_HTTPS_MEDIAN_MS = 1_500L

    fun assess(result: NetworkTestResult): OptimizerAssessment {
        val network = result.network

        if (!network.hasInternet) {
            return OptimizerAssessment(
                state = OptimizerState.NO_INTERNET,
                action = OptimizerAction.CHECK_CONNECTION,
                title = "Pas d'accès Internet détecté",
                explanation = "Android indique que le réseau actif ne déclare pas de capacité Internet.",
                actionLabel = "Vérifier la connexion",
                recommendation = "Vérifie l'accès Wi-Fi ou les données mobiles, puis relance le diagnostic."
            )
        }

        if (!network.validated) {
            return OptimizerAssessment(
                state = OptimizerState.NOT_VALIDATED,
                action = OptimizerAction.CHECK_CONNECTION,
                title = "Internet non validé par Android",
                explanation = "Le réseau annonce une capacité Internet, mais Android ne l'a pas validée au moment de la mesure.",
                actionLabel = "Contrôler l'accès Internet",
                recommendation = "Vérifie qu'une page ou un service habituel fonctionne, puis relance les mesures."
            )
        }

        if (!hasEnoughValidSamples(result.tcp) || !hasEnoughValidSamples(result.https)) {
            return OptimizerAssessment(
                state = OptimizerState.INSUFFICIENT_DATA,
                action = OptimizerAction.RETEST,
                title = "Données insuffisantes",
                explanation = "Il faut au moins trois réponses valides dans chaque série TCP et HTTPS pour produire une recommandation.",
                actionLabel = "Relancer le diagnostic",
                recommendation = "Refais une série complète. Le moteur préfère demander une nouvelle mesure plutôt que conclure sur trop peu de données."
            )
        }

        val tcpSuccessRatio = result.tcp.successes.toDouble() / result.tcp.attempts.toDouble()
        val httpsSuccessRatio = result.https.successes.toDouble() / result.https.attempts.toDouble()
        val tcpSpread = relativeSpread(result.tcp)
        val httpsSpread = relativeSpread(result.https)

        if (
            tcpSuccessRatio < MIN_SUCCESS_RATIO ||
            httpsSuccessRatio < MIN_SUCCESS_RATIO ||
            tcpSpread > MAX_RELATIVE_SPREAD ||
            httpsSpread > MAX_RELATIVE_SPREAD
        ) {
            return OptimizerAssessment(
                state = OptimizerState.UNSTABLE,
                action = OptimizerAction.RETEST,
                title = "Connexion irrégulière",
                explanation = "Les séries contiennent trop d'échecs ou une dispersion supérieure au seuil initial de 50 %. Une mesure isolée ne suffit pas à choisir un autre réseau.",
                actionLabel = "Stabiliser puis retester",
                recommendation = "Garde le téléphone au même endroit et évite de changer de réseau pendant la prochaine série."
            )
        }

        val tcpMedian = result.tcp.medianMs ?: return insufficientData()
        val httpsMedian = result.https.medianMs ?: return insufficientData()

        if (tcpMedian >= SLOW_TCP_MEDIAN_MS || httpsMedian >= SLOW_HTTPS_MEDIAN_MS) {
            return OptimizerAssessment(
                state = OptimizerState.SLOW_RESPONSES,
                action = OptimizerAction.COMPARE_NETWORKS,
                title = "Réponses lentes sur les sondes",
                explanation = "La médiane TCP atteint 300 ms ou la médiane HTTPS atteint 1 500 ms. Ce sont des seuils heuristiques initiaux, pas des limites universelles ni le ping d'un jeu.",
                actionLabel = "Comparer un autre accès manuellement",
                recommendation = "Compare séparément le Wi-Fi et les données mobiles au même endroit, avec les mêmes sondes. NOVA ne bascule pas le réseau à ta place."
            )
        }

        return OptimizerAssessment(
            state = OptimizerState.STABLE,
            action = OptimizerAction.KEEP_CURRENT,
            title = "Profil mesuré cohérent",
            explanation = "Les deux séries ont suffisamment de réponses, une réussite d'au moins 80 % et une dispersion contenue sous le seuil initial.",
            actionLabel = "Conserver le réseau actuel",
            recommendation = "Aucune raison mesurée ne justifie de recommander un changement pour cette session. Cela ne garantit pas les performances en jeu."
        )
    }

    private fun hasEnoughValidSamples(stats: ProbeStats): Boolean =
        stats.attempts > 0 &&
            stats.successes >= MIN_SUCCESSFUL_PROBES_PER_KIND &&
            stats.successes <= stats.attempts &&
            stats.timesMs.all { it > 0L }

    private fun relativeSpread(stats: ProbeStats): Double {
        val median = stats.medianMs ?: return Double.POSITIVE_INFINITY
        if (median <= 0L) return Double.POSITIVE_INFINITY
        val minimum = stats.minMs ?: return Double.POSITIVE_INFINITY
        val maximum = stats.maxMs ?: return Double.POSITIVE_INFINITY
        return (maximum - minimum).toDouble() / median.toDouble()
    }

    private fun insufficientData() = OptimizerAssessment(
        state = OptimizerState.INSUFFICIENT_DATA,
        action = OptimizerAction.RETEST,
        title = "Données insuffisantes",
        explanation = "Les statistiques nécessaires ne sont pas disponibles pour cette session.",
        actionLabel = "Relancer le diagnostic",
        recommendation = "Refais une série complète avant de tirer une conclusion."
    )
}
