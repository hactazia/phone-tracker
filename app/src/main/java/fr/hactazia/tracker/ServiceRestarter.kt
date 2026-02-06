package fr.hactazia.tracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ServiceRestarter : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = MqttPreferences(context)

        if (prefs.isServiceEnabled) {
            Log.d("ServiceRestarter", "Service enabled, restarting LocationService")
            val serviceIntent = Intent(context, LocationService::class.java)
            context.startService(serviceIntent)
        } else {
            Log.d("ServiceRestarter", "Service disabled, not restarting")
        }
    }
}

