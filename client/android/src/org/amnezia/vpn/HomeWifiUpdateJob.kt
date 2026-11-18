package org.amnezia.vpn

import android.app.job.JobScheduler
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Checks our releases only. Android's installer still asks the user to install the APK. */
class HomeWifiUpdateJob : JobService() {
    private val executor = Executors.newSingleThreadExecutor()
    private var task: Future<*>? = null

    override fun onStartJob(params: JobParameters): Boolean {
        task = executor.submit {
            try {
                val connection = URL("https://api.github.com/repos/krot4u/amnezia-client/releases?per_page=20")
                    .openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", "Amnezia-Home")
                val releases = try {
                    connection.inputStream.bufferedReader().use { JSONArray(it.readText()) }
                } finally { connection.disconnect() }
                for (i in 0 until releases.length()) {
                    val release = releases.getJSONObject(i)
                    val tag = release.getString("tag_name")
                    val code = tag.removePrefix("home-").toLongOrNull() ?: continue
                    if (release.optBoolean("draft") || release.optBoolean("prerelease")) continue
                    val current = packageManager.getPackageInfo(packageName, 0).longVersionCode
                    if (code > current) notifyUpdate(tag)
                    break
                }
            } catch (_: Exception) {
                // Offline or rate limited: retry in the next scheduled window; do not interrupt VPN.
            } finally { jobFinished(params, false) }
        }
        return true
    }

    private fun notifyUpdate(tag: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("home_updates", "Обновления Amnezia Home", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(this, 1,
            Intent(Intent.ACTION_VIEW, Uri.parse("${HomeWifiSettings.RELEASES_URL}/tag/$tag")),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(4102, Notification.Builder(this, "home_updates")
            .setSmallIcon(R.drawable.ic_amnezia_round).setContentTitle("Доступно обновление Amnezia Home")
            .setContentText("Нажмите, чтобы скачать новую сборку с домашним Wi-Fi")
            .setContentIntent(open).setAutoCancel(true).build())
    }

    override fun onStopJob(params: JobParameters): Boolean {
        task?.cancel(true)
        return true
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (scheduler.getPendingJob(4102) != null) return
            scheduler.schedule(JobInfo.Builder(4102,
                ComponentName(context, HomeWifiUpdateJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(24 * 60 * 60 * 1000L).setPersisted(true).build())
        }
    }
}
