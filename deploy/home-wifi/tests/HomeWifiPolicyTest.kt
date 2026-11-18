package org.amnezia.vpn

import org.amnezia.vpn.protocol.ProtocolState

fun main() {
    check(HomeWifiPolicy.effectiveState(ProtocolState.DISCONNECTING, false) == ProtocolState.DISCONNECTED)
    check(HomeWifiPolicy.effectiveState(ProtocolState.CONNECTED, false) == ProtocolState.DISCONNECTED)
    check(HomeWifiPolicy.effectiveState(ProtocolState.CONNECTING, false) == ProtocolState.CONNECTING)
    check(HomeWifiPolicy.effectiveState(ProtocolState.RECONNECTING, false) == ProtocolState.RECONNECTING)
    check(HomeWifiPolicy.effectiveState(ProtocolState.DISCONNECTING, true) == ProtocolState.DISCONNECTING)
    check(HomeWifiPolicy.effectiveState(ProtocolState.CONNECTED, true) == ProtocolState.CONNECTED)
    val home = setOf("Home", "Home-5G")
    check(HomeWifiPolicy.classify(home, emptyList()) == HomeWifiPolicy.Network.AWAY)
    check(HomeWifiPolicy.classify(home, listOf("Home")) == HomeWifiPolicy.Network.HOME)
    check(HomeWifiPolicy.classify(home, listOf("Cafe")) == HomeWifiPolicy.Network.AWAY)
    check(HomeWifiPolicy.classify(home, listOf(null)) == HomeWifiPolicy.Network.UNKNOWN)
    check(HomeWifiPolicy.classify(home, listOf("Cafe", null)) == HomeWifiPolicy.Network.UNKNOWN)
    check(HomeWifiPolicy.classify(home, listOf("Home-5G", null)) == HomeWifiPolicy.Network.HOME)
    check(HomeWifiPolicy.classify(emptySet(), emptyList()) == HomeWifiPolicy.Network.UNKNOWN)
    check(HomeWifiPolicy.normalizeSsid("\"Home\"") == "Home")
    check(HomeWifiPolicy.normalizeSsid("<unknown ssid>") == null)
    check(HomeWifiPolicy.normalizeSsid(null) == null)
    check(HomeWifiPolicy.normalizeSsid("0x") == null)
    check(HomeWifiPolicy.normalizeSsid(" Home ") == " Home ")
    println("PASS: network policy, SSID normalization and stale VPN state recovery")
}
