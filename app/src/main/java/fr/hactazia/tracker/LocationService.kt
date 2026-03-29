package fr.hactazia.tracker

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.location.Location
import android.os.Handler
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
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

class LocationService : Service() {

    companion object {
        private const val TAG = "LocationService"
        private const val LOCATION_BUFFER_PREFS = "location_buffer_prefs"
        private const val LOCATION_BUFFER_KEY = "location_buffer"
        private const val MAX_BUFFER_SIZE = 1000
        private const val MAX_LOG_ENTRIES = 50
        private const val MAX_QUEUE_SNAPSHOT_SIZE = 50
        private const val MAX_RECONNECT_DELAY_MS = 60_000L
        const val ACTION_TRACKING_STATUS = "fr.hactazia.tracker.TRACKING_STATUS"
        const val ACTION_MQTT_STATUS = "fr.hactazia.tracker.MQTT_STATUS"
        const val ACTION_REQUEST_STATUS = "fr.hactazia.tracker.REQUEST_STATUS"
        const val EXTRA_IS_ACTIVE = "is_active"
        const val EXTRA_IS_CONNECTED = "is_connected"
        const val EXTRA_LAST_SENT_AT = "last_sent_at"
        const val EXTRA_QUEUE_SIZE = "queue_size"
        const val EXTRA_LOG_ENTRIES = "log_entries"
        const val EXTRA_QUEUE_ITEMS = "queue_items"
    }

    private lateinit var locationClient: FusedLocationProviderClient
    private lateinit var mqttClient: MqttAndroidClient
    private lateinit var mqttPreferences: MqttPreferences
    private lateinit var locationBufferPrefs: SharedPreferences
    private var isTracking = false
    private var isMqttConnected = false
    private var isMqttConnecting = false
    private var isReconnectScheduled = false
    private var reconnectAttempt = 0
    private var lastSentAt = 0L
    private var isFlushingBufferedLocations = false
    private val bufferedLocations = ArrayDeque<String>()
    private val logEntries = ArrayDeque<String>()
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private val periodicLocationHandler = Handler(Looper.getMainLooper())
    private val logDateFormat = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())

    private val reconnectRunnable = Runnable {
        isReconnectScheduled = false
        connectMqtt()
    }

    private val periodicLocationRunnable = object : Runnable {
        override fun run() {
            capturePeriodicLocation()
            periodicLocationHandler.postDelayed(this, mqttPreferences.locationMaxIntervalMs)
        }
    }

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
        locationBufferPrefs = getSharedPreferences(LOCATION_BUFFER_PREFS, MODE_PRIVATE)
        loadBufferedLocations()

        // Vérifier si le service est activé
        if (!mqttPreferences.isServiceEnabled) {
            logDebug("Service is disabled, stopping")
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
        mqttClient.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                logInfo("MQTT connected (reconnect=$reconnect, uri=$serverURI)")
                reconnectAttempt = 0
                isMqttConnecting = false
                isReconnectScheduled = false
                reconnectHandler.removeCallbacks(reconnectRunnable)
                isMqttConnected = true
                broadcastMqttStatus(true)
                subscribeToRequestTopic()
                flushBufferedLocations()
                sendCurrentLocation()
            }

            override fun connectionLost(cause: Throwable?) {
                logError("MQTT connection lost", cause)
                isMqttConnecting = false
                isMqttConnected = false
                broadcastMqttStatus(false)
                scheduleReconnect("connection lost")
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                logDebug("MQTT message received on topic: $topic")
                if (topic == "tracker/${mqttPreferences.username}/request") {
                    logDebug("Location request received, sending current position")
                    sendCurrentLocation()
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                logVerbose("MQTT delivery complete")
            }
        })

        val options = MqttConnectOptions().apply {
            userName = mqttPreferences.username
            password = mqttPreferences.password.toCharArray()
            isAutomaticReconnect = true
            isCleanSession = false
        }

        connectMqtt(options)
    }

    private fun connectMqtt(existingOptions: MqttConnectOptions? = null) {
        if (mqttClient.isConnected || isMqttConnecting) {
            logDebug("Skipping connect, connected=${mqttClient.isConnected}, connecting=$isMqttConnecting")
            return
        }

        val options = existingOptions ?: MqttConnectOptions().apply {
            userName = mqttPreferences.username
            password = mqttPreferences.password.toCharArray()
            isAutomaticReconnect = true
            isCleanSession = false
        }

        isMqttConnecting = true
        logInfo("Connecting to MQTT broker ${mqttPreferences.brokerUrl} (attempt=${reconnectAttempt + 1})")
        mqttClient.connect(options, null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                logDebug("MQTT connect request sent successfully")
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                isMqttConnecting = false
                isMqttConnected = false
                logError("MQTT connection failed", exception)
                broadcastMqttStatus(false)
                scheduleReconnect("initial connect failure")
            }
        })
    }

    private fun scheduleReconnect(reason: String) {
        if (!mqttPreferences.isServiceEnabled) {
            logDebug("Reconnect skipped, service disabled")
            return
        }

        if (isReconnectScheduled || mqttClient.isConnected || isMqttConnecting) {
            return
        }

        val delay = min(MAX_RECONNECT_DELAY_MS, (1_000L shl min(reconnectAttempt, 6)))
        reconnectAttempt += 1
        isReconnectScheduled = true
        logWarn("Scheduling MQTT reconnect in ${delay}ms (reason=$reason, attempt=$reconnectAttempt)")
        reconnectHandler.postDelayed(reconnectRunnable, delay)
    }

    private fun subscribeToRequestTopic() {
        mqttClient.subscribe("tracker/${mqttPreferences.username}/request", 1, null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                logInfo("Subscribed to tracker/${mqttPreferences.username}/request")
            }
            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                logError("Failed to subscribe to tracker/${mqttPreferences.username}/request", exception)
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun sendCurrentLocation() {
        locationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                logDebug("Sending current location after MQTT connection")
                publishLocation(location)
            } else {
                logDebug("No current location available")
            }
        }.addOnFailureListener { exception ->
            logError("Failed to get current location", exception)
        }
    }

    @SuppressLint("MissingPermission")
    private fun capturePeriodicLocation() {
        locationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                logDebug("Periodic location capture triggered")
                publishLocation(location)
            } else {
                logWarn("Periodic location capture skipped, no cached location available")
            }
        }.addOnFailureListener { exception ->
            logError("Failed to capture periodic location", exception)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocation() {
        val maxIntervalMs = mqttPreferences.locationMaxIntervalMs
        val minIntervalMs = mqttPreferences.locationMinIntervalMs
        val minDistanceM = mqttPreferences.locationMinDistanceM

        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            maxIntervalMs
        )
            .setMinUpdateIntervalMillis(minIntervalMs)
            .setMinUpdateDistanceMeters(minDistanceM)
            .build()

        logInfo("Location request configured: maxInterval=${maxIntervalMs}ms minInterval=${minIntervalMs}ms minDistance=${minDistanceM}m")

        locationClient.requestLocationUpdates(
            request,
            callback,
            Looper.getMainLooper()
        )

        periodicLocationHandler.removeCallbacks(periodicLocationRunnable)
        periodicLocationHandler.postDelayed(periodicLocationRunnable, maxIntervalMs)

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
        val payload = JSONObject().apply {
            put("pvd", loc.provider)
            put("lat", loc.latitude)
            put("lng", loc.longitude)
            put("acc", loc.accuracy)
            put("tts", System.currentTimeMillis())
        }.toString()

        if (!mqttClient.isConnected) {
            logWarn("MQTT not connected, enqueue location")
            bufferLocation(payload)
            return
        }

        // Preserve chronological order when buffered items are pending.
        if (bufferedLocations.isNotEmpty()) {
            bufferLocation(payload)
            flushBufferedLocations()
            return
        }

        publishPayload(payload)
    }

    private fun publishPayload(payload: String) {
        logDebug("Publishing location to tracker/${mqttPreferences.username}")
        mqttClient.publish(
            "tracker/${mqttPreferences.username}",
            payload.toByteArray(),
            1,
            false,
            null,
            object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    lastSentAt = System.currentTimeMillis()
                    logInfo("Location published successfully at $lastSentAt")
                    broadcastMqttStatus(isMqttConnected)
                }
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    logError("Failed to publish location, enqueue payload", exception)
                    bufferLocation(payload)
                }
            }
        )
    }

    private fun loadBufferedLocations() {
        bufferedLocations.clear()

        val rawBuffer = locationBufferPrefs.getString(LOCATION_BUFFER_KEY, null) ?: return
        runCatching {
            val array = JSONArray(rawBuffer)
            for (index in 0 until array.length()) {
                val payload = array.optString(index, "")
                if (payload.isNotBlank()) {
                    bufferedLocations.addLast(payload)
                }
            }
            logInfo("Loaded ${bufferedLocations.size} buffered locations from storage")
            broadcastMqttStatus(isMqttConnected)
        }.onFailure {
            logError("Failed to load buffered locations", it)
        }
    }

    private fun persistBufferedLocations() {
        val array = JSONArray()
        bufferedLocations.forEach { payload ->
            array.put(payload)
        }
        locationBufferPrefs.edit().putString(LOCATION_BUFFER_KEY, array.toString()).apply()
    }

    private fun bufferLocation(payload: String) {
        val previousSize = bufferedLocations.size
        if (bufferedLocations.size >= MAX_BUFFER_SIZE) {
            bufferedLocations.removeFirst()
            logWarn("Location queue full, dropping oldest entry")
        }
        bufferedLocations.addLast(payload)
        persistBufferedLocations()
        logInfo("Queued location (size: $previousSize -> ${bufferedLocations.size})")
        broadcastMqttStatus(isMqttConnected)
    }

    private fun flushBufferedLocations() {
        if (!mqttClient.isConnected || isFlushingBufferedLocations || bufferedLocations.isEmpty()) {
            return
        }
        logInfo("Flushing ${bufferedLocations.size} queued location(s)")
        isFlushingBufferedLocations = true
        flushNextBufferedLocation()
    }

    private fun flushNextBufferedLocation() {
        val nextPayload = bufferedLocations.firstOrNull()
        if (nextPayload == null) {
            isFlushingBufferedLocations = false
            logInfo("Queue flush complete")
            return
        }

        mqttClient.publish(
            "tracker/${mqttPreferences.username}",
            nextPayload.toByteArray(),
            1,
            false,
            null,
            object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    bufferedLocations.removeFirst()
                    persistBufferedLocations()
                    lastSentAt = System.currentTimeMillis()
                    logInfo("Queued location sent, remaining=${bufferedLocations.size}")
                    broadcastMqttStatus(isMqttConnected)
                    flushNextBufferedLocation()
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    logError("Failed to flush queued location", exception)
                    isFlushingBufferedLocations = false
                    scheduleReconnect("flush failure")
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
        reconnectHandler.removeCallbacks(reconnectRunnable)
        periodicLocationHandler.removeCallbacks(periodicLocationRunnable)

        // Déconnecter MQTT
        if (mqttClient.isConnected)
            mqttClient.disconnect()
        mqttClient.unregisterResources()
        isMqttConnected = false
        broadcastMqttStatus(false)

        // Ne redémarrer que si le service est activé
        if (mqttPreferences.isServiceEnabled) {
            val restartIntent = Intent(this, ServiceRestarter::class.java)
            sendBroadcast(restartIntent)
        }
    }

    private fun logVerbose(message: String) {
        Log.v(TAG, message)
        appendLogEntry("VERBOSE", message)
    }

    private fun logDebug(message: String) {
        Log.d(TAG, message)
        appendLogEntry("DEBUG", message)
    }

    private fun logInfo(message: String) {
        Log.i(TAG, message)
        appendLogEntry("INFO", message)
    }

    private fun logWarn(message: String) {
        Log.w(TAG, message)
        appendLogEntry("WARN", message)
    }

    private fun logError(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
        val details = throwable?.message?.takeIf { it.isNotBlank() }?.let { "$message | $it" } ?: message
        appendLogEntry("ERROR", details)
    }

    private fun appendLogEntry(level: String, message: String) {
        val timestamp = logDateFormat.format(Date())
        if (logEntries.size >= MAX_LOG_ENTRIES) {
            logEntries.removeFirst()
        }
        logEntries.addLast("$timestamp [$level] $message")
    }

    private fun broadcastTrackingStatus(isActive: Boolean) {
        val intent = Intent(ACTION_TRACKING_STATUS).apply {
            putExtra(EXTRA_IS_ACTIVE, isActive)
            setPackage(packageName)
        }
        sendBroadcast(intent)
        logDebug("Broadcast tracking status: $isActive")
    }

    private fun broadcastMqttStatus(isConnected: Boolean) {
        val intent = Intent(ACTION_MQTT_STATUS).apply {
            putExtra(EXTRA_IS_CONNECTED, isConnected)
            putExtra(EXTRA_LAST_SENT_AT, lastSentAt)
            putExtra(EXTRA_QUEUE_SIZE, bufferedLocations.size)
            putStringArrayListExtra(EXTRA_LOG_ENTRIES, ArrayList(logEntries))
            putStringArrayListExtra(EXTRA_QUEUE_ITEMS, ArrayList(bufferedLocations.take(MAX_QUEUE_SNAPSHOT_SIZE)))
            setPackage(packageName)
        }
        sendBroadcast(intent)
        logDebug("Broadcast MQTT status: connected=$isConnected queue=${bufferedLocations.size} lastSentAt=$lastSentAt")
    }
}
