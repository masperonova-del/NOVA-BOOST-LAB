package com.novaping.boostlab

import org.junit.Assert.assertEquals
import org.junit.Test

class TunnelRoutingTest {
    @Test
    fun blocksWhenAnotherVpnIsAlreadyActive() {
        assertEquals(
            TunnelReadiness.BLOCKED_BY_EXISTING_VPN,
            TunnelReadinessEvaluator.evaluate(prerequisites(anotherVpnActive = true))
        )
    }

    @Test
    fun waitsForValidatedNetwork() {
        assertEquals(
            TunnelReadiness.WAITING_FOR_VALIDATED_NETWORK,
            TunnelReadinessEvaluator.evaluate(prerequisites(validatedNetworkAvailable = false))
        )
    }

    @Test
    fun requiresRelayHealthCheck() {
        assertEquals(
            TunnelReadiness.RELAY_HEALTH_CHECK_REQUIRED,
            TunnelReadinessEvaluator.evaluate(prerequisites(relayHealthVerified = false))
        )
    }

    @Test
    fun healthCheckAloneDoesNotPretendRelayProtocolExists() {
        assertEquals(
            TunnelReadiness.RELAY_PROTOCOL_NOT_IMPLEMENTED,
            TunnelReadinessEvaluator.evaluate(prerequisites(relayProtocolCompatible = false))
        )
    }

    @Test
    fun requiresAndroidVpnConsentAfterAllTechnicalPrerequisites() {
        assertEquals(
            TunnelReadiness.VPN_CONSENT_REQUIRED,
            TunnelReadinessEvaluator.evaluate(prerequisites(vpnConsentGranted = false))
        )
    }

    @Test
    fun readyOnlyWhenEveryPrerequisiteIsSatisfied() {
        assertEquals(
            TunnelReadiness.READY,
            TunnelReadinessEvaluator.evaluate(prerequisites())
        )
    }

    @Test
    fun restoresDefaultRouteWhenTunnelIsNotEstablished() {
        assertEquals(
            TunnelRouteAction.RESTORE_ANDROID_DEFAULT,
            TunnelRouteSafetyPolicy.decide(true, true, false)
        )
        assertEquals(
            TunnelRouteAction.RESTORE_ANDROID_DEFAULT,
            TunnelRouteSafetyPolicy.decide(false, true, true)
        )
    }

    @Test
    fun restoresDefaultRouteWhenRelayUpstreamFails() {
        assertEquals(
            TunnelRouteAction.RESTORE_ANDROID_DEFAULT,
            TunnelRouteSafetyPolicy.decide(true, false, true)
        )
    }

    @Test
    fun usesTunnelOnlyWhenTunnelRelayAndNetworkAreHealthy() {
        assertEquals(
            TunnelRouteAction.USE_TUNNEL,
            TunnelRouteSafetyPolicy.decide(true, true, true)
        )
    }

    private fun prerequisites(
        validatedNetworkAvailable: Boolean = true,
        relayHealthVerified: Boolean = true,
        relayProtocolCompatible: Boolean = true,
        vpnConsentGranted: Boolean = true,
        anotherVpnActive: Boolean = false
    ) = TunnelPrerequisites(
        validatedNetworkAvailable,
        relayHealthVerified,
        relayProtocolCompatible,
        vpnConsentGranted,
        anotherVpnActive
    )
}
