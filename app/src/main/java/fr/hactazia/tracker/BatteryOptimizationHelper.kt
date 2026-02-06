package fr.hactazia.tracker

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Utilitaires pour gérer l'optimisation de batterie
 * Sur certains téléphones (Xiaomi, Huawei, etc.), il est nécessaire de désactiver
 * l'optimisation de batterie pour que le service continue de fonctionner en arrière-plan
 */
object BatteryOptimizationHelper {

    /**
     * Vérifie si l'application est exemptée de l'optimisation de batterie
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Demande à l'utilisateur d'exempter l'application de l'optimisation de batterie
     */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (!isIgnoringBatteryOptimizations(context)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        }
    }

    /**
     * Ouvre la page des paramètres de batterie de l'application
     */
    fun openBatterySettings(context: Context) {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        context.startActivity(intent)
    }
}


