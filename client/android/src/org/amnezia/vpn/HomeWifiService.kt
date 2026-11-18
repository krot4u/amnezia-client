package org.amnezia.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.net.wifi.WifiInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.amnezia.vpn.protocol.ProtocolState

/** Lives in the main process, independently of the protocol service which stops at home. */
class HomeWifiService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var settings: HomeWifiSettings
    private lateinit var connectivity: ConnectivityManager
    private val wifi = mutableMapOf<Network, String?>()
    private var registered = false
    private var state = VpnState.defaultState
    private var lastAttempt = -RETRY_MS
    private var lastNetwork = HomeWifiPolicy.Network.UNKNOWN
    private var displayedStatus: String? = null

    private val callback = object : ConnectivityManager.NetworkCallback(
        if (Build.VERSION.SDK_INT >= 31) FLAG_INCLUDE_LOCATION_INFO else 0
    ) {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            handler.post {
                wifi[network] = HomeWifiPolicy.normalizeSsid((caps.transportInfo as? WifiInfo)?.ssid)
                    ?: HomeWifiSettings.currentSsid(this@HomeWifiService)
                schedule()
            }
        }

        override fun onLost(network: Network) {
            handler.post { wifi.remove(network); schedule() }
        }
    }

    private val evaluate = Runnable { reconcile() }
    private val heartbeat = object : Runnable {
        override fun run() {
            reconcile()
            handler.postDelayed(this, RETRY_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        settings = HomeWifiSettings(this)
        connectivity = getSystemService(ConnectivityManager::class.java)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Домашний Wi-Fi", NotificationManager.IMPORTANCE_LOW))
        val notification = notification("Проверяем сеть…")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(NOTIFICATION, notification)
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        try {
            connectivity.registerNetworkCallback(request, callback)
            registered = true
            // Seed before the first callback; an unreadable SSID stays UNKNOWN.
            connectivity.allNetworks.forEach { network ->
                val caps = connectivity.getNetworkCapabilities(network)
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true &&
                    !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    wifi[network] = HomeWifiSettings.currentSsid(this)
                }
            }
        } catch (_: SecurityException) {
            updateStatus("Нет разрешения на чтение состояния сети")
            stopSelf()
            return
        }
        scope.launch {
            VpnStateStore.dataFlow().collect {
                state = it
                schedule()
            }
        }
        handler.postDelayed(heartbeat, SETTLE_MS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!settings.enabled) stopSelf() else schedule()
        return START_STICKY
    }

    private fun schedule() {
        handler.removeCallbacks(evaluate)
        handler.postDelayed(evaluate, SETTLE_MS)
    }

    private fun reconcile() {
        if (!settings.enabled) { stopSelf(); return }
        if (!HomeWifiSettings.hasLocationPermission(this) || !HomeWifiSettings.locationEnabled(this)) {
            updateStatus("Нужно геоположение: точное, «Разрешить всегда», и включённая служба")
            return
        }
        if (wifi.values.any { it == null }) {
            val ssid = HomeWifiSettings.currentSsid(this)
            if (ssid != null) wifi.keys.toList().forEach { wifi[it] = ssid }
        }
        val network = HomeWifiPolicy.classify(settings.homeSsids, wifi.values.toList())
        if (network != lastNetwork) {
            lastAttempt = -RETRY_MS
            lastNetwork = network
        }
        when (network) {
            HomeWifiPolicy.Network.UNKNOWN -> updateStatus("Не удалось определить Wi-Fi; состояние VPN сохранено")
            HomeWifiPolicy.Network.HOME -> {
                if (state.protocolState in setOf(ProtocolState.CONNECTED, ProtocolState.CONNECTING, ProtocolState.RECONNECTING)) {
                    updateStatus("Дома — отключаем VPN…")
                    sendBroadcast(Intent(ACTION_DISCONNECT).setPackage(packageName))
                } else if (state.protocolState == ProtocolState.DISCONNECTING) updateStatus("Дома — отключаем VPN…")
                else updateStatus("Дома — VPN выключен")
            }
            HomeWifiPolicy.Network.AWAY -> connectAway()
        }
    }

    private fun connectAway() {
        val proto = state.vpnProto
        if (proto == null || state.serverIndex < 0) {
            updateStatus("Сначала импортируйте профиль и один раз подключите VPN вручную")
            return
        }
        if (VpnService.prepare(this) != null) {
            updateStatus("Откройте Amnezia и разрешите VPN-подключение")
            return
        }
        val running = AmneziaVpnService.isRunning(this, proto.processName)
        if (running && state.protocolState == ProtocolState.CONNECTED) {
            updateStatus("Вне дома — VPN подключён")
            return
        }
        if (running && state.protocolState in setOf(ProtocolState.CONNECTING, ProtocolState.DISCONNECTING, ProtocolState.RECONNECTING)) {
            updateStatus("Вне дома — VPN переключается…")
            return
        }
        if (SystemClock.elapsedRealtime() - lastAttempt < RETRY_MS) return
        lastAttempt = SystemClock.elapsedRealtime()
        try {
            ContextCompat.startForegroundService(this, Intent(this, proto.serviceClass))
            updateStatus("Вне дома — подключаем VPN…")
        } catch (_: IllegalStateException) {
            updateStatus("Android ограничил запуск VPN; откройте Amnezia")
        } catch (_: SecurityException) {
            updateStatus("Нет разрешения на запуск VPN; откройте Amnezia")
        }
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, HomeWifiActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_amnezia_round)
            .setContentTitle("Amnezia Home").setContentText(text).setContentIntent(open)
            .setOngoing(true).setOnlyAlertOnce(true).build()
    }

    private fun updateStatus(text: String) {
        if (displayedStatus == text) return
        displayedStatus = text
        settings.status = text
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(text))
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (registered) connectivity.unregisterNetworkCallback(callback)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "home_wifi"
        private const val NOTIFICATION = 4101
        private const val SETTLE_MS = 5_000L
        private const val RETRY_MS = 60_000L

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, HomeWifiService::class.java))
        }

        fun stop(context: Context) {
            HomeWifiSettings(context).enabled = false
            HomeWifiSettings(context).status = "Автоматизация выключена"
            context.stopService(Intent(context, HomeWifiService::class.java))
        }
    }
}

class HomeWifiBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        HomeWifiUpdateJob.schedule(context)
        if (HomeWifiSettings(context).enabled) {
            try { HomeWifiService.start(context) }
            catch (_: IllegalStateException) { HomeWifiSettings(context).status = "Откройте настройки домашнего Wi-Fi для запуска" }
            catch (_: SecurityException) { HomeWifiSettings(context).status = "Android запретил автозапуск; откройте настройки домашнего Wi-Fi" }
        }
    }
}
