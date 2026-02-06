package fr.hactazia.tracker

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import info.mqtt.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.json.JSONObject

class LocationService : Service() {

    companion object {
        const val ACTION_TRACKING_STATUS = "fr.hactazia.tracker.TRACKING_STATUS"
        const val ACTION_MQTT_STATUS = "fr.hactazia.tracker.MQTT_STATUS"
        const val ACTION_REQUEST_STATUS = "fr.hactazia.tracker.REQUEST_STATUS"
        const val EXTRA_IS_ACTIVE = "is_active"
        const val EXTRA_IS_CONNECTED = "is_connected"
    }

    private lateinit var locationClient: FusedLocationProviderClient
    private lateinit var mqttClient: MqttAndroidClient
    private lateinit var mqttPreferences: MqttPreferences
    private var isTracking = false
    private var isMqttConnected = false

    private val statusRequestReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action == ACTION_REQUEST_STATUS) {
                broadcastTrackingStatus(isTracking)
                broadcastMqttStatus(isMqttConnected)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        mqttPreferences = MqttPreferences(this)

        // Vérifier si le service est activé
        if (!mqttPreferences.isServiceEnabled) {
            Log.d("LocationService", "Service is disabled, stopping")
            stopSelf()
            return
        }

        locationClient = LocationServices.getFusedLocationProviderClient(this)

        registerReceiver(
            statusRequestReceiver,
            IntentFilter(ACTION_REQUEST_STATUS),
            RECEIVER_NOT_EXPORTED
        )

        setupMqtt()
        startLocation()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun setupMqtt() {
        mqttClient = MqttAndroidClient(
            this,
            mqttPreferences.brokerUrl,
            mqttPreferences.username
        )

        // Configurer le callback pour recevoir les messages
        mqttClient.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {
                Log.e("MQTT", "Connection lost", cause)
                isMqttConnected = false
                broadcastMqttStatus(false)
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                Log.d("MQTT", "Message received on topic: $topic")
                if (topic == "tracker/${mqttPreferences.username}/request") {
                    Log.d("MQTT", "Location request received, sending current position")
                    sendCurrentLocation()
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                // Message delivery complete
            }
        })

        val options = MqttConnectOptions().apply {
            userName = mqttPreferences.username
            password = mqttPreferences.password.toCharArray()
            isAutomaticReconnect = true
            isCleanSession = false
        }

        mqttClient.connect(options, null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                Log.d("MQTT", "Connected")
                isMqttConnected = true
                broadcastMqttStatus(true)

                subscribeToRequestTopic()
                sendCurrentLocation()
            }
            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                Log.e("MQTT", "Connection failed", exception)
                isMqttConnected = false
                broadcastMqttStatus(false)
            }
        })
    }

    private fun subscribeToRequestTopic() {
        mqttClient.subscribe("tracker/${mqttPreferences.username}/request", 1, null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                Log.d("MQTT", "Subscribed to tracker/${mqttPreferences.username}/request")
            }
            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                Log.e("MQTT", "Failed to subscribe to tracker/${mqttPreferences.username}/request", exception)
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun sendCurrentLocation() {
        locationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                Log.d("LocationService", "Sending current location after MQTT connection")
                publishLocation(location)
            } else {
                Log.d("LocationService", "No current location available")
            }
        }.addOnFailureListener { exception ->
            Log.e("LocationService", "Failed to get current location", exception)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocation() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            1_800_000L // 30 minutes
        )
            .setMinUpdateIntervalMillis(30_000L) // 30 secondes si mouvement détecté
            .setMinUpdateDistanceMeters(30f)
            .build()

        locationClient.requestLocationUpdates(
            request,
            callback,
            Looper.getMainLooper()
        )

        isTracking = true
        broadcastTrackingStatus(true)
    }

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            publishLocation(loc)
        }
    }

    private fun publishLocation(loc: Location) {
        if (!mqttClient.isConnected) {
            Log.w("LocationService", "MQTT not connected, skipping location publish")
            return
        }

        val payload = JSONObject().apply {
            put("pvd", loc.provider)
            put("lat", loc.latitude)
            put("lng", loc.longitude)
            put("acc", loc.accuracy)
            put("tts", System.currentTimeMillis())
        }

        Log.d("LocationService", "Publishing location: $payload to topic tracker/${mqttPreferences.username}")
        mqttClient.publish(
            "tracker/${mqttPreferences.username}",
            payload.toString().toByteArray(),
            1,
            false,
            null,
            object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d("MQTT", "Location published")
                }
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e("MQTT", "Failed to publish location", exception)
                }
            }
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()

        try {
            unregisterReceiver(statusRequestReceiver)
        } catch (_: IllegalArgumentException) {
            // Receiver déjà désenregistré
        }

        locationClient.removeLocationUpdates(callback)
        isTracking = false
        broadcastTrackingStatus(false)

        // Déconnecter MQTT
        if (mqttClient.isConnected)
            mqttClient.disconnect()
        isMqttConnected = false
        broadcastMqttStatus(false)

        // Ne redémarrer que si le service est activé
        if (mqttPreferences.isServiceEnabled) {
            val restartIntent = Intent(this, ServiceRestarter::class.java)
            sendBroadcast(restartIntent)
        }
    }

    private fun broadcastTrackingStatus(isActive: Boolean) {
        val intent = Intent(ACTION_TRACKING_STATUS).apply {
            putExtra(EXTRA_IS_ACTIVE, isActive)
            setPackage(packageName)
        }
        sendBroadcast(intent)
        Log.d("LocationService", "Broadcast tracking status: $isActive")
    }

    private fun broadcastMqttStatus(isConnected: Boolean) {
        val intent = Intent(ACTION_MQTT_STATUS).apply {
            putExtra(EXTRA_IS_CONNECTED, isConnected)
            setPackage(packageName)
        }
        sendBroadcast(intent)
        Log.d("LocationService", "Broadcast MQTT status: $isConnected")
    }
}
