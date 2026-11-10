package com.novaping.boostlab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiSignalRankerTest {
    @Test
    fun sortsNetworksByStrongestSignalFirst() {
        val ranked = WifiSignalRanker.rank(listOf(
            network("Réseau faible", -82),
            network("Réseau fort", -42),
            network("Réseau moyen", -65)
        ))
        assertEquals(listOf("Réseau fort", "Réseau moyen", "Réseau faible"), ranked.map { it.ssid })
    }

    @Test
    fun keepsOnlyStrongestAccessPointForRepeatedSsid() {
        val ranked = WifiSignalRanker.rank(listOf(
            network("Maison", -72, 2412),
            network("Maison", -48, 5180),
            network("Invité", -60)
        ))
        assertEquals(2, ranked.size)
        assertEquals(-48, ranked.first { it.ssid == "Maison" }.signalDbm)
        assertEquals(5180, ranked.first { it.ssid == "Maison" }.frequencyMhz)
    }

    @Test
    fun excludesUnknownNamesAndInvalidMeasurements() {
        val ranked = WifiSignalRanker.rank(listOf(
            network("", -40),
            network("<unknown ssid>", -50),
            network("Signal invalide", -120),
            network("Fréquence invalide", -60, 0),
            network("Valide", -66)
        ))
        assertEquals(1, ranked.size)
        assertEquals("Valide", ranked.single().ssid)
    }

    @Test
    fun doesNotInventNetworksWhenScanIsEmpty() {
        assertTrue(WifiSignalRanker.rank(emptyList()).isEmpty())
    }

    @Test
    fun classifiesOweAsWpa3EnhancedOpenRatherThanPlainOpen() {
        assertEquals("WPA3 Enhanced Open", WifiSecurityClassifier.classify("[OWE][ESS]"))
    }

    @Test
    fun classifiesWpa3AndLegacyOpenNetworks() {
        assertEquals("WPA3", WifiSecurityClassifier.classify("[WPA2-PSK-SAE-CCMP][ESS]"))
        assertEquals("Ouvert", WifiSecurityClassifier.classify("[ESS]"))
    }

    @Test
    fun distinguishesUnknownSecurityCapabilities() {
        assertEquals("Autre / non identifié", WifiSecurityClassifier.classify("[VENDOR-SECURITY][ESS]"))
    }

    @Test
    fun reportsCachedRejectedAndTimedOutScansClearly() {
        assertTrue(WifiScanStatus.fromBroadcast(false).contains("cache"))
        assertTrue(WifiScanStatus.startRejected().contains("refusé"))
        assertTrue(WifiScanStatus.timedOut().contains("délai"))
    }

    private fun network(ssid: String, signal: Int, frequency: Int = 2437) =
        NearbyWifiNetwork(ssid, signal, frequency, "WPA/WPA2")
}
