package fr.hactazia.tracker

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.Button
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText
import kotlin.math.max
import java.text.DateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {

    private lateinit var mqttPreferences: MqttPreferences

    private lateinit var tvTrackingStatus: TextView
    private lateinit var tvMqttStatus: TextView
    private lateinit var tvLastSend: TextView
    private lateinit var tvQueueStatus: TextView
    private lateinit var logsTable: TableLayout
    private lateinit var queueTable: TableLayout
    private lateinit var trackingIndicator: android.view.View
    private lateinit var mqttIndicator: android.view.View

    private val trackingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val isActive = intent?.getBooleanExtra(LocationService.EXTRA_IS_ACTIVE, false) ?: false
            android.util.Log.d("MainActivity", "Received tracking status: $isActive")
            updateTrackingStatus(isActive)
        }
    }

    private val mqttReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val isConnected = intent?.getBooleanExtra(LocationService.EXTRA_IS_CONNECTED, false) ?: false
            val lastSentAt = intent?.getLongExtra(LocationService.EXTRA_LAST_SENT_AT, 0L) ?: 0L
            val queueSize = intent?.getIntExtra(LocationService.EXTRA_QUEUE_SIZE, 0) ?: 0
            val logEntries = intent?.getStringArrayListExtra(LocationService.EXTRA_LOG_ENTRIES).orEmpty()
            val queueItems = intent?.getStringArrayListExtra(LocationService.EXTRA_QUEUE_ITEMS).orEmpty()
            android.util.Log.d("MainActivity", "Received MQTT status: connected=$isConnected queue=$queueSize lastSentAt=$lastSentAt")
            updateMqttStatus(isConnected)
            updateMqttDetails(lastSentAt, queueSize)
            renderLogs(logEntries)
            renderQueue(queueItems)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mqttPreferences = MqttPreferences(this)

        // Initialiser les vues d'indicateurs
        tvTrackingStatus = findViewById(R.id.tvTrackingStatus)
        tvMqttStatus = findViewById(R.id.tvMqttStatus)
        tvLastSend = findViewById(R.id.tvLastSend)
        tvQueueStatus = findViewById(R.id.tvQueueStatus)
        logsTable = findViewById(R.id.logsTable)
        queueTable = findViewById(R.id.queueTable)
        trackingIndicator = findViewById(R.id.trackingIndicator)
        mqttIndicator = findViewById(R.id.mqttIndicator)

        // Demander l'exemption de l'optimisation de batterie
        if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this)) {
            BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(this)
        }

        // Demander toutes les permissions nécessaires
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        )

        ActivityCompat.requestPermissions(this, permissions, 1)

        // Initialiser les champs avec les valeurs sauvegardées
        loadConfigToFields()

        findViewById<Button>(R.id.btnSaveConfig).setOnClickListener {
            saveConfigFromFields()
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            mqttPreferences.isServiceEnabled = true
            val serviceIntent = Intent(this, LocationService::class.java)
            startService(serviceIntent)
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            mqttPreferences.isServiceEnabled = false
            stopService(Intent(this, LocationService::class.java))
        }
    }

    private fun loadConfigToFields() {
        findViewById<TextInputEditText>(R.id.etBrokerUrl).setText(mqttPreferences.brokerUrl)
        findViewById<TextInputEditText>(R.id.etUsername).setText(mqttPreferences.username)
        findViewById<TextInputEditText>(R.id.etPassword).setText(mqttPreferences.password)
        findViewById<TextInputEditText>(R.id.etLocationMaxInterval).setText((mqttPreferences.locationMaxIntervalMs / 1000L).toString())
        findViewById<TextInputEditText>(R.id.etLocationMinInterval).setText((mqttPreferences.locationMinIntervalMs / 1000L).toString())
        findViewById<TextInputEditText>(R.id.etLocationMinDistance).setText(mqttPreferences.locationMinDistanceM.toString())
    }

    private fun saveConfigFromFields() {
        mqttPreferences.brokerUrl = findViewById<TextInputEditText>(R.id.etBrokerUrl).text.toString()
        mqttPreferences.username = findViewById<TextInputEditText>(R.id.etUsername).text.toString()
        mqttPreferences.password = findViewById<TextInputEditText>(R.id.etPassword).text.toString()

        val maxIntervalSeconds = findViewById<TextInputEditText>(R.id.etLocationMaxInterval).text.toString().toLongOrNull() ?: 1800L
        val minIntervalSeconds = findViewById<TextInputEditText>(R.id.etLocationMinInterval).text.toString().toLongOrNull() ?: 30L
        val minDistanceMeters = findViewById<TextInputEditText>(R.id.etLocationMinDistance).text.toString().toFloatOrNull() ?: 30f

        val sanitizedMinIntervalMs = max(1_000L, minIntervalSeconds * 1000L)
        val sanitizedMaxIntervalMs = max(sanitizedMinIntervalMs, maxIntervalSeconds * 1000L)
        val sanitizedMinDistance = max(0f, minDistanceMeters)

        mqttPreferences.locationMaxIntervalMs = sanitizedMaxIntervalMs
        mqttPreferences.locationMinIntervalMs = sanitizedMinIntervalMs
        mqttPreferences.locationMinDistanceM = sanitizedMinDistance

        findViewById<TextInputEditText>(R.id.etLocationMaxInterval).setText((sanitizedMaxIntervalMs / 1000L).toString())
        findViewById<TextInputEditText>(R.id.etLocationMinInterval).setText((sanitizedMinIntervalMs / 1000L).toString())
        findViewById<TextInputEditText>(R.id.etLocationMinDistance).setText(sanitizedMinDistance.toString())

        Toast.makeText(this, R.string.config_saved, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        // Enregistrer les BroadcastReceivers
        registerReceiver(trackingReceiver, IntentFilter(LocationService.ACTION_TRACKING_STATUS), RECEIVER_NOT_EXPORTED)
        registerReceiver(mqttReceiver, IntentFilter(LocationService.ACTION_MQTT_STATUS), RECEIVER_NOT_EXPORTED)

        // Demander l'état actuel du service après un court délai pour s'assurer que les receivers sont bien enregistrés
        tvTrackingStatus.postDelayed({
            val intent = Intent(LocationService.ACTION_REQUEST_STATUS).apply {
                setPackage(packageName) // Rendre le broadcast explicite
            }
            sendBroadcast(intent)
        }, 100)
    }

    override fun onPause() {
        super.onPause()
        // Désenregistrer les BroadcastReceivers
        try {
            unregisterReceiver(trackingReceiver)
            unregisterReceiver(mqttReceiver)
        } catch (_: IllegalArgumentException) {
            // Receiver pas encore enregistré
        }
    }

    private fun updateTrackingStatus(isActive: Boolean) {
        if (isActive) {
            tvTrackingStatus.text = getString(R.string.status_active)
            trackingIndicator.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_green_dark)
        } else {
            tvTrackingStatus.text = getString(R.string.status_inactive)
            trackingIndicator.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_red_dark)
        }
    }

    private fun updateMqttStatus(isConnected: Boolean) {
        if (isConnected) {
            tvMqttStatus.text = getString(R.string.status_connected)
            mqttIndicator.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_green_dark)
        } else {
            tvMqttStatus.text = getString(R.string.status_disconnected)
            mqttIndicator.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_red_dark)
        }
    }

    private fun updateMqttDetails(lastSentAt: Long, queueSize: Int) {
        if (lastSentAt <= 0L) {
            tvLastSend.text = getString(R.string.last_send_never)
        } else {
            val formatted = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(lastSentAt))
            tvLastSend.text = getString(R.string.last_send_at, formatted)
        }

        tvQueueStatus.text = getString(R.string.queue_status_count, queueSize)
    }

    private fun renderLogs(entries: List<String>) {
        renderTable(
            table = logsTable,
            rows = entries.reversed(),
            emptyMessage = getString(R.string.logs_empty)
        )
    }

    private fun renderQueue(entries: List<String>) {
        renderTable(
            table = queueTable,
            rows = entries,
            emptyMessage = getString(R.string.queue_empty)
        )
    }

    private fun renderTable(table: TableLayout, rows: List<String>, emptyMessage: String) {
        table.removeAllViews()

        if (rows.isEmpty()) {
            table.addView(createTableRow("-", emptyMessage, true))
            return
        }

        rows.forEachIndexed { index, value ->
            table.addView(createTableRow((index + 1).toString(), value, false))
        }
    }

    private fun createTableRow(indexLabel: String, value: String, isEmptyState: Boolean): TableRow {
        val row = TableRow(this).apply {
            layoutParams = TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val indexView = TextView(this).apply {
            text = indexLabel
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.TOP
            setPadding(0, 8, 16, 8)
            layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 0.12f)
        }

        val valueView = TextView(this).apply {
            text = value
            typeface = Typeface.MONOSPACE
            setPadding(0, 8, 0, 8)
            layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 0.88f)
        }

        if (isEmptyState) {
            valueView.setTypeface(valueView.typeface, Typeface.ITALIC)
        }

        row.addView(indexView)
        row.addView(valueView)
        return row
    }
}
