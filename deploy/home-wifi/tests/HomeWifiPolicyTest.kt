package org.amnezia.vpn

fun main() {
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
    println("PASS: home, away, unreadable Wi-Fi, multiple APs, empty settings and SSID normalization")
}
