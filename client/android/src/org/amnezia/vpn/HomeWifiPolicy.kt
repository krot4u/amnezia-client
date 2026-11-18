package org.amnezia.vpn

/** An unidentified Wi-Fi must never be treated as a trusted home network. */
object HomeWifiPolicy {
    enum class Network { HOME, AWAY, UNKNOWN }

    fun normalizeSsid(raw: String?): String? = raw?.removeSurrounding("\"")
        ?.takeUnless { it.isBlank() || it == "<unknown ssid>" || it == "0x" }

    fun classify(home: Set<String>, wifiSsids: List<String?>): Network = when {
        home.isEmpty() -> Network.UNKNOWN
        wifiSsids.any { it != null && it in home } -> Network.HOME
        wifiSsids.any { it == null } -> Network.UNKNOWN
        else -> Network.AWAY
    }
}
