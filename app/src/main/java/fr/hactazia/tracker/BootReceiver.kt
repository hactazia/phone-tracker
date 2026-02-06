package fr.hactazia.tracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val mqttPreferences = MqttPreferences(context)
            if (mqttPreferences.isServiceEnabled) {
                val serviceIntent = Intent(context, LocationService::class.java)
                context.startForegroundService(serviceIntent)
                android.util.Log.d("BootReceiver", "Service redémarré après boot")
            } else {
                android.util.Log.d("BootReceiver", "Service non redémarré car désactivé")
            }
        }
    }
}



