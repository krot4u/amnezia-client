package org.amnezia.vpn

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration.UI_MODE_NIGHT_MASK
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Menu
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.setPadding

class HomeWifiActivity : ComponentActivity() {
    private val settings by lazy { HomeWifiSettings(this) }
    private val handler = Handler(Looper.getMainLooper())
    private var statusUpdateRunnable: Runnable? = null
    private var isUpdatingUI = false

    // View references
    private lateinit var statusView: TextView
    private lateinit var currentSsidView: TextView
    private lateinit var automationToggle: Switch
    private lateinit var ssidsContainer: LinearLayout

    private val requestLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            // Background location requested via separate button to App Details Settings
            updateUI()
            Toast.makeText(this, "Разрешение на местоположение дано. Для фоновой работы откройте параметры приложения.", Toast.LENGTH_LONG).show()
        } else {
            updateUI()
        }
    }

    private val requestNotificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) Toast.makeText(this,
            "Без разрешения на уведомления Android не покажет статус и новые версии. Их можно проверить кнопкой «Проверить обновления».",
            Toast.LENGTH_LONG).show()
        tryEnableAutomation()
    }

    private val vpnPrepareResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            tryEnableAutomation()
        } else {
            settings.enabled = false
            updateUI()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(getActivityTheme())

        val rootScroll = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            // Title
            addView(TextView(this@HomeWifiActivity).apply {
                text = "Домашний Wi-Fi"
                textSize = 20f
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
                    bottomMargin = 8.dpToPx()
                }
            })

            // Status
            statusView = TextView(this@HomeWifiActivity).apply {
                text = "Статус: загрузка..."
                textSize = 14f
                setTextColor(getColor(android.R.color.darker_gray))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(16.dpToPx(), 8.dpToPx(), 16.dpToPx(), 24.dpToPx())
                }
            }
            addView(statusView)

            // Help: first connection
            addView(TextView(this@HomeWifiActivity).apply {
                text = "Совет: сначала импортируйте профиль и один раз подключитесь к VPN вручную."
                textSize = 12f
                setTextColor(getColor(android.R.color.darker_gray))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(16.dpToPx(), 0, 16.dpToPx(), 16.dpToPx())
                }
            })

            // Current SSID
            currentSsidView = TextView(this@HomeWifiActivity).apply {
                text = "Текущая сеть: —"
                textSize = 14f
                setTextColor(getColor(android.R.color.darker_gray))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(16.dpToPx(), 0, 16.dpToPx(), 24.dpToPx())
                }
            }
            addView(currentSsidView)

            // Home SSIDs title
            addView(TextView(this@HomeWifiActivity).apply {
                text = "Домашние сети:"
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(16.dpToPx(), 0, 16.dpToPx(), 12.dpToPx())
                }
            })

            // SSID list container
            ssidsContainer = LinearLayout(this@HomeWifiActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(16.dpToPx(), 0, 16.dpToPx(), 16.dpToPx())
                }
            }
            addView(ssidsContainer)

            // Add current network button
            addView(Button(this@HomeWifiActivity).apply {
                text = "Добавить текущую сеть"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    56.dpToPx()
                ).apply {
                    setMargins(16.dpToPx(), 0, 16.dpToPx(), 12.dpToPx())
                }
                setOnClickListener { addCurrentNetwork() }
            })

            // Add custom SSID button
            addView(Button(this@HomeWifiActivity).apply {
                text = "Добавить вручную"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    56.dpToPx()
                ).apply {
                    setMargins(16.dpToPx(), 0, 16.dpToPx(), 16.dpToPx())
                }
                setOnClickListener { showAddSsidDialog() }
            })

            // Automation toggle
            addView(FrameLayout(this@HomeWifiActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(16.dpToPx(), 0, 16.dpToPx(), 12.dpToPx())
                }
                setBackgroundResource(
                    if (isDarkTheme()) android.R.drawable.dialog_holo_dark_frame
                    else android.R.drawable.dialog_holo_light_frame
                )

                addView(LinearLayout(this@HomeWifiActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setPadding(8.dpToPx())

                    automationToggle = Switch(this@HomeWifiActivity).apply {
                        text = "Включить автоматизацию"
                        minHeight = 48.dpToPx()
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        setOnCheckedChangeListener { _, isChecked ->
                            if (isUpdatingUI) return@setOnCheckedChangeListener
                            if (isChecked) {
                                requestPermissionsAndEnable()
                            } else {
                                settings.enabled = false
                                HomeWifiService.stop(this@HomeWifiActivity)
                                updateUI()
                            }
                        }
                    }
                    addView(automationToggle)
                })
            })

            // Open app permissions button
            addView(Button(this@HomeWifiActivity).apply {
                text = "Разрешить работу в фоне"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    56.dpToPx()
                ).apply {
                    setMargins(16.dpToPx(), 0, 16.dpToPx(), 12.dpToPx())
                }
                setOnClickListener { openAppDetailsSettings() }
            })

            // Check location permission button
            addView(Button(this@HomeWifiActivity).apply {
                text = "Включить геолокацию"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    56.dpToPx()
                ).apply {
                    setMargins(16.dpToPx(), 0, 16.dpToPx(), 12.dpToPx())
                }
                setOnClickListener { openLocationSettings() }
            })

            // Update button
            addView(Button(this@HomeWifiActivity).apply {
                text = "Проверить обновления"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    56.dpToPx()
                ).apply {
                    setMargins(16.dpToPx(), 0, 16.dpToPx(), 24.dpToPx())
                }
                setOnClickListener {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(HomeWifiSettings.RELEASES_URL)))
                }
            })
        }

        rootScroll.addView(root)
        setContentView(rootScroll)

        // Apply window insets to avoid overlap with status/nav bar
        ViewCompat.setOnApplyWindowInsetsListener(rootScroll) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        updateUI()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(statusUpdateRunnable ?: Runnable {})
        statusUpdateRunnable = Runnable {
            if (!isDestroyed) {
                updateUI()
                handler.postDelayed(statusUpdateRunnable!!, 1000)
            }
        }
        handler.post(statusUpdateRunnable!!)
    }

    override fun onPause() {
        super.onPause()
        statusUpdateRunnable?.let { handler.removeCallbacks(it) }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.add("Домой")?.setOnMenuItemClickListener {
            finish()
            true
        }
        return true
    }

    private fun updateUI() {
        isUpdatingUI = true

        statusView.text = buildStatusString()
        currentSsidView.text = "Текущая сеть: ${HomeWifiSettings.currentSsid(this) ?: "—"}"
        automationToggle.isChecked = settings.enabled

        ssidsContainer.removeAllViews()
        settings.homeSsids.forEach { ssid ->
            ssidsContainer.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 8.dpToPx() }

                addView(TextView(this@HomeWifiActivity).apply {
                    text = ssid
                    layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    ).apply { marginEnd = 8.dpToPx() }
                })

                addView(Button(this@HomeWifiActivity).apply {
                    text = "Удалить"
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        48.dpToPx()
                    )
                    setOnClickListener {
                        settings.homeSsids = settings.homeSsids - ssid
                        updateUI()
                    }
                })
            })
        }

        isUpdatingUI = false
    }

    private fun addCurrentNetwork() {
        val currentSsid = HomeWifiSettings.currentSsid(this)
        if (currentSsid == null) {
            Toast.makeText(this, "Не удалось прочитать имя Wi-Fi. Проверьте подключение, геолокацию и разрешения.", Toast.LENGTH_LONG).show()
            return
        }
        val ssids = settings.homeSsids.toMutableSet()
        ssids.add(currentSsid)
        settings.homeSsids = ssids
        updateUI()
    }

    private fun showAddSsidDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = "Введите имя сети (SSID)"
        }
        val container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(16.dpToPx())
            addView(input)
        }
        AlertDialog.Builder(this, getDialogTheme())
            .setTitle("Добавить сеть")
            .setView(container)
            .setPositiveButton("Добавить") { _, _ ->
                val ssid = input.text.toString().trim()
                if (ssid.isNotEmpty()) {
                    val ssids = settings.homeSsids.toMutableSet()
                    ssids.add(ssid)
                    settings.homeSsids = ssids
                    updateUI()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun requestPermissionsAndEnable() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED -> {
                requestLocationLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            }
            !hasLocationPermission() -> {
                AlertDialog.Builder(this)
                    .setTitle("Распознавание Wi-Fi в фоне")
                    .setMessage("Откройте «Разрешения» → «Местоположение», выберите «Разрешить всегда» и включите точное местоположение. Затем вернитесь и включите автоматизацию.")
                    .setPositiveButton("Открыть настройки") { _, _ -> openAppDetailsSettings() }
                    .setNegativeButton("Отмена", null).show()
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED -> {
                requestNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            else -> tryEnableAutomation()
        }
        updateUI()
    }

    private fun tryEnableAutomation() {
        if (settings.homeSsids.isEmpty()) {
            Toast.makeText(this, "Добавьте домашние сети", Toast.LENGTH_SHORT).show()
            return
        }
        if (!HomeWifiSettings.locationEnabled(this)) {
            Toast.makeText(this, "Включите геолокацию в системных настройках", Toast.LENGTH_SHORT).show()
            return
        }
        if (!hasLocationPermission()) {
            Toast.makeText(this, "Дайте разрешение на доступ к местоположению", Toast.LENGTH_SHORT).show()
            return
        }

        val prepare = VpnService.prepare(this)
        if (prepare != null) {
            vpnPrepareResultLauncher.launch(prepare)
        } else {
            settings.enabled = true
            try {
                HomeWifiService.start(this)
            } catch (_: IllegalStateException) {
                settings.enabled = false
                Toast.makeText(this, "Android запретил запуск службы. Проверьте настройки фоновой работы.", Toast.LENGTH_LONG).show()
            } catch (_: SecurityException) {
                settings.enabled = false
                Toast.makeText(this, "Не хватает разрешения для запуска службы.", Toast.LENGTH_LONG).show()
            }
            updateUI()
        }
    }

    private fun hasLocationPermission(): Boolean {
        return HomeWifiSettings.hasLocationPermission(this)
    }

    private fun openAppDetailsSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    private fun openLocationSettings() {
        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }

    private fun buildStatusString(): String {
        return when {
            settings.homeSsids.isEmpty() -> "Статус: сети не добавлены"
            !HomeWifiSettings.locationEnabled(this) -> "Статус: геолокация выключена"
            !hasLocationPermission() -> "Статус: нет разрешения на доступ к местоположению"
            !settings.enabled -> "Статус: автоматизация выключена"
            else -> "Статус: ${settings.status}"
        }
    }

    private fun isDarkTheme(): Boolean =
        resources.configuration.uiMode and UI_MODE_NIGHT_MASK == UI_MODE_NIGHT_YES

    private fun getActivityTheme(): Int =
        if (isDarkTheme()) android.R.style.Theme_DeviceDefault
        else android.R.style.Theme_DeviceDefault_Light

    private fun getDialogTheme(): Int =
        if (isDarkTheme()) android.R.style.Theme_DeviceDefault_Dialog_Alert
        else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
