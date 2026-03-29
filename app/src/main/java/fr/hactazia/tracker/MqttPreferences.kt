package fr.hactazia.tracker

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class MqttPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("mqtt_config", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_BROKER_URL = "broker_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_LOCATION_MAX_INTERVAL_MS = "location_max_interval_ms"
        private const val KEY_LOCATION_MIN_INTERVAL_MS = "location_min_interval_ms"
        private const val KEY_LOCATION_MIN_DISTANCE_M = "location_min_distance_m"

        private const val DEFAULT_BROKER_URL = "tcp://mqtt.hactazia.fr:1883"
        private const val DEFAULT_USERNAME = ""
        private const val DEFAULT_PASSWORD = ""
        private const val DEFAULT_LOCATION_MAX_INTERVAL_MS = 1_800_000L
        private const val DEFAULT_LOCATION_MIN_INTERVAL_MS = 30_000L
        private const val DEFAULT_LOCATION_MIN_DISTANCE_M = 30f
    }

    var brokerUrl: String
        get() = prefs.getString(KEY_BROKER_URL, DEFAULT_BROKER_URL) ?: DEFAULT_BROKER_URL
        set(value) = prefs.edit { putString(KEY_BROKER_URL, value) }

    var username: String
        get() = prefs.getString(KEY_USERNAME, DEFAULT_USERNAME) ?: DEFAULT_USERNAME
        set(value) = prefs.edit { putString(KEY_USERNAME, value) }

    var password: String
        get() = prefs.getString(KEY_PASSWORD, DEFAULT_PASSWORD) ?: DEFAULT_PASSWORD
        set(value) = prefs.edit { putString(KEY_PASSWORD, value) }

    var isServiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_SERVICE_ENABLED, value) }

    var locationMaxIntervalMs: Long
        get() = prefs.getLong(KEY_LOCATION_MAX_INTERVAL_MS, DEFAULT_LOCATION_MAX_INTERVAL_MS)
        set(value) = prefs.edit { putLong(KEY_LOCATION_MAX_INTERVAL_MS, value) }

    var locationMinIntervalMs: Long
        get() = prefs.getLong(KEY_LOCATION_MIN_INTERVAL_MS, DEFAULT_LOCATION_MIN_INTERVAL_MS)
        set(value) = prefs.edit { putLong(KEY_LOCATION_MIN_INTERVAL_MS, value) }

    var locationMinDistanceM: Float
        get() = prefs.getFloat(KEY_LOCATION_MIN_DISTANCE_M, DEFAULT_LOCATION_MIN_DISTANCE_M)
        set(value) = prefs.edit { putFloat(KEY_LOCATION_MIN_DISTANCE_M, value) }

}

