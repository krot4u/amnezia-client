package org.amnezia.vpn

import org.amnezia.vpn.protocol.ProtocolState

/** An unidentified Wi-Fi must never be treated as a trusted home network. */
object HomeWifiPolicy {
    enum class Network { HOME, AWAY, UNKNOWN }

    fun effectiveState(stored: ProtocolState, ownVpnActive: Boolean): ProtocolState =
        if (!ownVpnActive && stored in setOf(ProtocolState.CONNECTED, ProtocolState.DISCONNECTING))
            ProtocolState.DISCONNECTED else stored

    fun normalizeSsid(raw: String?): String? = raw?.removeSurrounding("\"")
        ?.takeUnless { it.isBlank() || it == "<unknown ssid>" || it == "0x" }

    fun classify(home: Set<String>, wifiSsids: List<String?>): Network = when {
        home.isEmpty() -> Network.UNKNOWN
        wifiSsids.any { it != null && it in home } -> Network.HOME
        wifiSsids.any { it == null } -> Network.UNKNOWN
        else -> Network.AWAY
    }
}
