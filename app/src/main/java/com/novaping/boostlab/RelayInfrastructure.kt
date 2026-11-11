package com.novaping.boostlab

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

internal data class RelayEndpoint(
    val name: String,
    val healthUrl: String
)

internal sealed class RelayValidation {
    data class Valid(val endpoint: RelayEndpoint) : RelayValidation()
    data class Invalid(val reason: String) : RelayValidation()
}

internal object RelayEndpointValidator {
    fun validate(name: String, rawUrl: String): RelayValidation {
        val cleanName = name.trim()
        if (cleanName.isEmpty() || cleanName.length > 40) {
            return RelayValidation.Invalid("Le nom doit contenir entre 1 et 40 caractères.")
        }
        val cleanUrl = rawUrl.trim()
        if (cleanUrl.isEmpty() || cleanUrl.length > 2_048) {
            return RelayValidation.Invalid("L'adresse HTTPS est vide ou trop longue.")
        }
        val uri = try {
            URI(cleanUrl)
        } catch (_: Exception) {
            return RelayValidation.Invalid("Adresse invalide.")
        }
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            return RelayValidation.Invalid("Seules les adresses HTTPS sont acceptées.")
        }
        if (uri.host.isNullOrBlank()) {
            return RelayValidation.Invalid("Le nom d'hôte est manquant.")
        }
        if (uri.rawUserInfo != null || uri.rawFragment != null || uri.rawQuery != null) {
            return RelayValidation.Invalid("Les identifiants, fragments et paramètres d'URL ne sont pas acceptés.")
        }
        if (uri.port !in -1..65_535 || uri.port == 0) {
            return RelayValidation.Invalid("Le port indiqué est invalide.")
        }
        return RelayValidation.Valid(
            RelayEndpoint(cleanName, uri.normalize().toASCIIString())
        )
    }
}

internal data class RelayProbeResult(
    val endpoint: RelayEndpoint,
    val reachable: Boolean,
    val responseMs: Long?,
    val httpStatus: Int?,
    val message: String
)

internal object RelaySelector {
    fun select(results: List<RelayProbeResult>): RelayProbeResult? =
        results.filter { it.reachable && (it.responseMs ?: 0L) > 0L }
            .minByOrNull { it.responseMs!! }
}

/**
 * Checks only an operator-provided HTTPS health URL. This is not a relay transport
 * and does not forward application or game traffic.
 */
internal class RelayEndpointProbe {
    suspend fun check(endpoint: RelayEndpoint): RelayProbeResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = URL(endpoint.healthUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            val startedAt = System.nanoTime()
            val status = connection.responseCode
            val elapsed = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(1L)
            val reachable = status in 200..299
            RelayProbeResult(
                endpoint = endpoint,
                reachable = reachable,
                responseMs = elapsed.takeIf { reachable },
                httpStatus = status,
                message = if (reachable) "Point de contrôle HTTPS joignable." else "Le point de contrôle a répondu avec HTTP $status."
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            RelayProbeResult(
                endpoint = endpoint,
                reachable = false,
                responseMs = null,
                httpStatus = null,
                message = error.message?.take(160) ?: "Point de contrôle inaccessible."
            )
        } finally {
            connection?.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 3_000
    }
}
