# Techniques pour forcer le service à fonctionner en arrière-plan

Ce projet implémente plusieurs techniques pour garantir que le service de localisation continue de fonctionner, même en arrière-plan et après un redémarrage du téléphone.

## ✅ Techniques implémentées

### 1. **Service Foreground avec Notification**
- Le service démarre en mode foreground avec `startForeground()`
- Une notification permanente est affichée (non supprimable par glissement)
- **Avantage** : Android ne tue presque jamais les services foreground
- **Fichier** : `LocationService.kt` - méthodes `createNotification()` et `createNotificationChannel()`

### 2. **START_STICKY**
- Le service retourne `START_STICKY` dans `onStartCommand()`
- **Avantage** : Si Android tue le service par manque de mémoire, il le redémarre automatiquement dès que possible
- **Fichier** : `LocationService.kt` - méthode `onStartCommand()`

### 3. **WakeLock (PARTIAL_WAKE_LOCK)**
- Acquisition d'un WakeLock partiel pour empêcher la mise en veille profonde
- **Avantage** : Le CPU continue de fonctionner même quand l'écran est éteint
- **Important** : Libéré proprement dans `onDestroy()` pour économiser la batterie
- **Fichier** : `LocationService.kt` - méthodes `acquireWakeLock()` et `onDestroy()`
- **Permission** : `WAKE_LOCK` dans `AndroidManifest.xml`

### 4. **BroadcastReceiver pour redémarrage automatique**
- Un `BroadcastReceiver` est notifié quand le service est détruit
- Il redémarre automatiquement le service
- **Fichier** : `ServiceRestarter.kt`
- **Déclaration** : Dans `AndroidManifest.xml`

### 5. **Démarrage au boot du téléphone**
- Un `BroadcastReceiver` écoute l'événement `BOOT_COMPLETED`
- Le service redémarre automatiquement après un redémarrage du téléphone
- **Fichier** : `BootReceiver.kt`
- **Permission** : `RECEIVE_BOOT_COMPLETED` dans `AndroidManifest.xml`

### 6. **Service avec attribut `stopWithTask="false"`**
- L'attribut `stopWithTask="false"` dans le manifest empêche le service de s'arrêter quand l'utilisateur ferme l'application
- **Déclaration** : Dans `AndroidManifest.xml` sur la balise `<service>`

## 📋 Permissions nécessaires

Toutes les permissions sont déclarées dans `AndroidManifest.xml` :

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION"/>
<uses-permission android:name="android.permission.WAKE_LOCK"/>
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<uses-permission android:name="android.permission.INTERNET"/>
```

## 🔧 Utilisation

### Démarrer le service
```kotlin
val serviceIntent = Intent(this, LocationService::class.java)
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    startForegroundService(serviceIntent)
} else {
    startService(serviceIntent)
}
```

### Arrêter le service
```kotlin
stopService(Intent(this, LocationService::class.java))
```

## ⚠️ Notes importantes

1. **Batterie** : Ces techniques consomment de la batterie. Le service utilise :
   - Un WakeLock partiel (CPU toujours actif)
   - Des mises à jour de localisation toutes les 5 minutes
   - Une connexion MQTT permanente

2. **Optimisation de batterie** : Sur certains téléphones (Xiaomi, Huawei, Samsung, etc.), vous devrez demander à l'utilisateur de désactiver manuellement l'optimisation de batterie pour votre application dans les paramètres du téléphone.

3. **Android 12+** : Les restrictions sont plus strictes. Assurez-vous que :
   - Le service est bien en foreground
   - La permission `FOREGROUND_SERVICE_LOCATION` est déclarée
   - L'attribut `foregroundServiceType="location"` est présent sur le service

4. **Test** : Pour tester le redémarrage automatique :
   - Démarrez le service
   - Allez dans les paramètres Android → Applications → HactaziaTracker → Forcer l'arrêt
   - Le service devrait redémarrer automatiquement

## 🎯 Résultat

Avec toutes ces techniques combinées, le service a une **très haute probabilité de continuer à fonctionner** en arrière-plan, même :
- Quand l'application est fermée
- Quand l'écran est éteint
- Après un redémarrage du téléphone
- Même si Android essaie de tuer le service par manque de mémoire

