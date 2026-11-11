package com.novaping.boostlab

internal data class TunnelPrerequisites(
    val validatedNetworkAvailable: Boolean,
    val relayHealthVerified: Boolean,
    val relayProtocolCompatible: Boolean,
    val vpnConsentGranted: Boolean,
    val anotherVpnActive: Boolean
)

internal enum class TunnelReadiness {
    READY,
    BLOCKED_BY_EXISTING_VPN,
    WAITING_FOR_VALIDATED_NETWORK,
    RELAY_HEALTH_CHECK_REQUIRED,
    RELAY_PROTOCOL_NOT_IMPLEMENTED,
    VPN_CONSENT_REQUIRED
}

internal object TunnelReadinessEvaluator {
    fun evaluate(prerequisites: TunnelPrerequisites): TunnelReadiness = when {
        prerequisites.anotherVpnActive -> TunnelReadiness.BLOCKED_BY_EXISTING_VPN
        !prerequisites.validatedNetworkAvailable -> TunnelReadiness.WAITING_FOR_VALIDATED_NETWORK
        !prerequisites.relayHealthVerified -> TunnelReadiness.RELAY_HEALTH_CHECK_REQUIRED
        !prerequisites.relayProtocolCompatible -> TunnelReadiness.RELAY_PROTOCOL_NOT_IMPLEMENTED
        !prerequisites.vpnConsentGranted -> TunnelReadiness.VPN_CONSENT_REQUIRED
        else -> TunnelReadiness.READY
    }
}

internal enum class TunnelRouteAction {
    USE_TUNNEL,
    RESTORE_ANDROID_DEFAULT
}

/**
 * Fail-closed routing policy. A route is eligible only after the tunnel is established
 * and its upstream relay remains healthy; otherwise the app must restore Android's
 * default path. This policy does not create or start a VPN tunnel.
 */
internal object TunnelRouteSafetyPolicy {
    fun decide(
        tunnelEstablished: Boolean,
        upstreamRelayHealthy: Boolean,
        validatedNetworkAvailable: Boolean
    ): TunnelRouteAction {
        return if (tunnelEstablished && upstreamRelayHealthy && validatedNetworkAvailable) {
            TunnelRouteAction.USE_TUNNEL
        } else {
            TunnelRouteAction.RESTORE_ANDROID_DEFAULT
        }
    }
}
