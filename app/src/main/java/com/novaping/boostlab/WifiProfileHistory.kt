package com.novaping.boostlab

import android.content.Context
import java.security.MessageDigest

internal data class WifiProfileSummary(
    val runCount: Int,
    val medianMs: Long?,
    val jitterMs: Long?,
    val lastMedianMs: Long?,
    val lastJitterMs: Long?
)

/** Local-only Wi-Fi history. SSIDs are converted to stable local hashes before being used as preference keys. */
internal class WifiProfileHistory(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun profileIdForSsid(ssid: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(ssid.toByteArray(Charsets.UTF_8))
        return "ssid_" + digest.take(12).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun record(profileId: String, score: NetworkPathScore) {
        if (score.label != "Wi-Fi" || score.successes < MIN_SUCCESSFUL_PROBES || score.medianMs <= 0L) return
        val samples = readSamples(profileId).toMutableList()
        samples += Sample(score.medianMs, score.jitterMs)
        while (samples.size > MAX_SAMPLES) samples.removeAt(0)
        val encoded = samples.joinToString(",") { "${it.medianMs}:${it.jitterMs}" }
        preferences.edit().putString(profileId, encoded).apply()
    }

    /** Counts old manually named entries without exposing or assigning them to real networks. */
    fun legacyArchiveRunCount(): Int =
        listOf("wifi_1", "wifi_2", "wifi_3").sumOf { readSamples(it).size }

    fun summary(profileId: String): WifiProfileSummary {
        val samples = readSamples(profileId)
        if (samples.isEmpty()) return WifiProfileSummary(0, null, null, null, null)
        return WifiProfileSummary(
            runCount = samples.size,
            medianMs = median(samples.map { it.medianMs }),
            jitterMs = median(samples.map { it.jitterMs }),
            lastMedianMs = samples.last().medianMs,
            lastJitterMs = samples.last().jitterMs
        )
    }

    private fun readSamples(profileId: String): List<Sample> =
        preferences.getString(profileId, null)
            ?.split(",")
            ?.mapNotNull { encoded ->
                val values = encoded.split(":")
                if (values.size != 2) null else {
                    val latency = values[0].toLongOrNull()
                    val jitter = values[1].toLongOrNull()
                    if (latency == null || jitter == null || latency <= 0L || jitter < 0L) null
                    else Sample(latency, jitter)
                }
            }
            .orEmpty()

    private fun median(values: List<Long>): Long {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle]
        else (sorted[middle - 1] + sorted[middle]) / 2L
    }

    private data class Sample(val medianMs: Long, val jitterMs: Long)

    private companion object {
        const val PREFERENCES_NAME = "nova_boost_wifi_profiles"
        const val MIN_SUCCESSFUL_PROBES = 5
        const val MAX_SAMPLES = 12
    }
}
