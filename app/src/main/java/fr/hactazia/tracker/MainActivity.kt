package fr.hactazia.tracker

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private lateinit var mqttPreferences: MqttPreferences

    private lateinit var tvTrackingStatus: TextView
    private lateinit var tvMqttStatus: TextView
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
            android.util.Log.d("MainActivity", "Received MQTT status: $isConnected")
            updateMqttStatus(isConnected)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mqttPreferences = MqttPreferences(this)

        // Initialiser les vues d'indicateurs
        tvTrackingStatus = findViewById(R.id.tvTrackingStatus)
        tvMqttStatus = findViewById(R.id.tvMqttStatus)
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
    }

    private fun saveConfigFromFields() {
        mqttPreferences.brokerUrl = findViewById<TextInputEditText>(R.id.etBrokerUrl).text.toString()
        mqttPreferences.username = findViewById<TextInputEditText>(R.id.etUsername).text.toString()
        mqttPreferences.password = findViewById<TextInputEditText>(R.id.etPassword).text.toString()

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
}
