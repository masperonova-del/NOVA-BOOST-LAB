package com.novaping.boostlab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayInfrastructureTest {
    @Test
    fun acceptsHttpsHealthEndpoint() {
        val result = RelayEndpointValidator.validate("Relay Europe", "https://relay.example.com/health")
        assertTrue(result is RelayValidation.Valid)
        assertEquals(
            "https://relay.example.com/health",
            (result as RelayValidation.Valid).endpoint.healthUrl
        )
    }

    @Test
    fun rejectsPlainHttp() {
        val result = RelayEndpointValidator.validate("Relay", "http://relay.example.com/health")
        assertTrue(result is RelayValidation.Invalid)
    }

    @Test
    fun rejectsEmbeddedCredentials() {
        val result = RelayEndpointValidator.validate("Relay", "https://user:secret@relay.example.com/health")
        assertTrue(result is RelayValidation.Invalid)
    }

    @Test
    fun rejectsQueryAndFragment() {
        assertTrue(RelayEndpointValidator.validate("Relay", "https://relay.example.com/health?token=x") is RelayValidation.Invalid)
        assertTrue(RelayEndpointValidator.validate("Relay", "https://relay.example.com/health#status") is RelayValidation.Invalid)
    }

    @Test
    fun rejectsBlankOrOversizedName() {
        assertTrue(RelayEndpointValidator.validate(" ", "https://relay.example.com/health") is RelayValidation.Invalid)
        assertTrue(RelayEndpointValidator.validate("x".repeat(41), "https://relay.example.com/health") is RelayValidation.Invalid)
    }

    @Test
    fun selectsLowestLatencyReachableEndpointOnly() {
        val slow = result("slow", 180L, true)
        val fast = result("fast", 75L, true)
        val failed = result("failed", null, false)
        assertEquals("fast", RelaySelector.select(listOf(slow, failed, fast))?.endpoint?.name)
    }

    @Test
    fun returnsNoSelectionWhenNoEndpointIsReachable() {
        assertNull(RelaySelector.select(listOf(result("offline", null, false))))
    }

    private fun result(name: String, latency: Long?, reachable: Boolean) = RelayProbeResult(
        endpoint = RelayEndpoint(name, "https://$name.example.com/health"),
        reachable = reachable,
        responseMs = latency,
        httpStatus = if (reachable) 200 else null,
        message = "test"
    )
}
