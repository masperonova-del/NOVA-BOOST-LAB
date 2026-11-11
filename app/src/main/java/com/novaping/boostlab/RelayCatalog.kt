package com.novaping.boostlab

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * A configured HTTPS health endpoint. It is metadata for availability checks only,
 * never a transport capable of forwarding application traffic.
 */
internal data class RelayEntry(
    val id: String,
    val name: String,
    val healthUrl: String,
    val protocol: String = "HTTPS_HEALTH_ONLY",
    val enabled: Boolean = true,
    val lastCheckedAtMs: Long? = null,
    val reachable: Boolean? = null,
    val responseMs: Long? = null,
    val httpStatus: Int? = null
)

internal object RelayCatalogPolicy {
    fun selectFastestHealthy(entries: List<RelayEntry>): RelayEntry? =
        entries.asSequence()
            .filter { it.enabled && it.reachable == true && (it.responseMs ?: 0L) > 0L }
            .minByOrNull { it.responseMs!! }

    fun upsert(entries: List<RelayEntry>, entry: RelayEntry): List<RelayEntry> {
        val withoutSameId = entries.filterNot { it.id == entry.id }
        val withoutSameUrl = withoutSameId.filterNot { it.healthUrl.equals(entry.healthUrl, ignoreCase = true) }
        return (withoutSameUrl + entry)
            .sortedBy { it.name.lowercase() }
            .take(MAX_ENTRIES)
    }

    const val MAX_ENTRIES = 20
}

internal class RelayCatalogRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun getAll(): List<RelayEntry> = decode(preferences.getString(KEY_ENTRIES, null))

    fun save(entry: RelayEntry) {
        val updated = RelayCatalogPolicy.upsert(getAll(), entry)
        preferences.edit().putString(KEY_ENTRIES, encode(updated)).apply()
    }

    fun remove(id: String) {
        preferences.edit().putString(
            KEY_ENTRIES,
            encode(getAll().filterNot { it.id == id })
        ).apply()
    }

    fun setEnabled(id: String, enabled: Boolean) {
        val updated = getAll().map { if (it.id == id) it.copy(enabled = enabled) else it }
        preferences.edit().putString(KEY_ENTRIES, encode(updated)).apply()
    }

    private fun encode(entries: List<RelayEntry>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(JSONObject().apply {
                put("id", entry.id)
                put("name", entry.name)
                put("healthUrl", entry.healthUrl)
                put("protocol", entry.protocol)
                put("enabled", entry.enabled)
                put("lastCheckedAtMs", entry.lastCheckedAtMs ?: JSONObject.NULL)
                put("reachable", entry.reachable ?: JSONObject.NULL)
                put("responseMs", entry.responseMs ?: JSONObject.NULL)
                put("httpStatus", entry.httpStatus ?: JSONObject.NULL)
            })
        }
        return array.toString()
    }

    private fun decode(raw: String?): List<RelayEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        RelayEntry(
                            id = item.getString("id"),
                            name = item.getString("name"),
                            healthUrl = item.getString("healthUrl"),
                            protocol = item.optString("protocol", "HTTPS_HEALTH_ONLY"),
                            enabled = item.optBoolean("enabled", true),
                            lastCheckedAtMs = item.nullableLong("lastCheckedAtMs"),
                            reachable = item.nullableBoolean("reachable"),
                            responseMs = item.nullableLong("responseMs"),
                            httpStatus = item.nullableLong("httpStatus")?.toInt()
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun JSONObject.nullableLong(key: String): Long? =
        if (isNull(key) || !has(key)) null else optLong(key).takeIf { it >= 0L }

    private fun JSONObject.nullableBoolean(key: String): Boolean? =
        if (isNull(key) || !has(key)) null else optBoolean(key)

    private companion object {
        const val PREFERENCES = "nova_boost_relay_catalog"
        const val KEY_ENTRIES = "entries"
    }
}
