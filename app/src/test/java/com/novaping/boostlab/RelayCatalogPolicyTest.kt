package com.novaping.boostlab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelayCatalogPolicyTest {
    @Test
    fun selectsFastestEnabledHealthyHealthEndpoint() {
        val selected = RelayCatalogPolicy.selectFastestHealthy(
            listOf(
                entry("disabled", 20L, enabled = false),
                entry("slow", 180L),
                entry("fast", 75L),
                entry("offline", null, reachable = false)
            )
        )
        assertEquals("fast", selected?.id)
    }

    @Test
    fun doesNotSelectUnverifiedOrUnreachableEndpoints() {
        assertNull(
            RelayCatalogPolicy.selectFastestHealthy(
                listOf(entry("unknown", null, reachable = null), entry("failed", null, reachable = false))
            )
        )
    }

    @Test
    fun upsertReplacesSameUrlAndKeepsCatalogUnique() {
        val original = entry("old", 120L)
        val replacement = entry("new", 80L).copy(healthUrl = original.healthUrl)
        val result = RelayCatalogPolicy.upsert(listOf(original), replacement)
        assertEquals(1, result.size)
        assertEquals("new", result.single().id)
    }

    @Test
    fun catalogNeverExceedsLimitAndKeepsNewEntry() {
        val existing = (1..RelayCatalogPolicy.MAX_ENTRIES).map { entry("relay-${it.toString().padStart(2, '0')}", 100L) }
        val added = entry("zz-new", 50L)
        val result = RelayCatalogPolicy.upsert(existing, added)
        assertEquals(RelayCatalogPolicy.MAX_ENTRIES, result.size)
        assertEquals(1, result.count { it.id == added.id })
    }

    private fun entry(
        id: String,
        latency: Long?,
        enabled: Boolean = true,
        reachable: Boolean? = latency != null
    ) = RelayEntry(
        id = id,
        name = id,
        healthUrl = "https://$id.example.com/health",
        enabled = enabled,
        reachable = reachable,
        responseMs = latency
    )
}
