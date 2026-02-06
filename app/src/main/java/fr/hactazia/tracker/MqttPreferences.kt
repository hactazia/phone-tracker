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

        private const val DEFAULT_BROKER_URL = "tcp://mqtt.hactazia.fr:1883"
        private const val DEFAULT_USERNAME = ""
        private const val DEFAULT_PASSWORD = ""
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

}

