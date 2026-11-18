package org.amnezia.vpn

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Settings are only read in the main process; VPN credentials stay in upstream's encrypted store. */
class HomeWifiSettings(context: Context) {
    private val prefs = context.getSharedPreferences("home_wifi", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) { prefs.edit().putBoolean("enabled", value).apply() }

    var homeSsids: Set<String>
        get() = prefs.getStringSet("ssids", emptySet())!!.toSet()
        set(value) { prefs.edit().putStringSet("ssids", value.filter(String::isNotEmpty).toSet()).apply() }

    var status: String
        get() = prefs.getString("status", "Автоматизация выключена")!!
        set(value) { prefs.edit().putString("status", value).apply() }

    companion object {
        const val RELEASES_URL = "https://github.com/krot4u/amnezia-client/releases"

        fun hasLocationPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                (Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(context,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED)

        fun locationEnabled(context: Context): Boolean =
            context.getSystemService(LocationManager::class.java).isLocationEnabled

        @Suppress("DEPRECATION", "MissingPermission")
        fun currentSsid(context: Context): String? {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                !locationEnabled(context)) return null
            val info = context.applicationContext.getSystemService(WifiManager::class.java).connectionInfo
            return HomeWifiPolicy.normalizeSsid(info?.ssid)
        }
    }
}
