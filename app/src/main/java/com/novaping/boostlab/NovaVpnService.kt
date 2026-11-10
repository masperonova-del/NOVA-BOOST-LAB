package com.novaping.boostlab

import android.net.VpnService
import android.content.Intent

/**
 * Scaffold only. A real implementation must establish a TUN interface,
 * implement packet forwarding, encryption, DNS handling and a remote relay.
 * This service intentionally does NOT establish a tunnel or reroute traffic.
 */
class NovaVpnService : VpnService() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
