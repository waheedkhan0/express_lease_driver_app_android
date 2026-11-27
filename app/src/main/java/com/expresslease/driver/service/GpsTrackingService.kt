package com.expresslease.driver.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.expresslease.driver.R
import com.expresslease.driver.data.OdooRepository
import com.expresslease.driver.data.PrefsManager
import com.expresslease.driver.ui.home.HomeActivity
import com.google.android.gms.location.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class GpsTrackingService : Service() {

    companion object {
        private const val TAG = "GpsTrackingService"
        const val CHANNEL_ID = "express_lease_tracking_channel"
        const val NOTIFICATION_ID = 888
        const val ACTION_START = "com.expresslease.driver.ACTION_START_TRACKING"

        // GPS update interval: 30 seconds for active tracking
        private const val LOCATION_INTERVAL_MS = 30_000L
        private const val LOCATION_FASTEST_INTERVAL_MS = 15_000L

        fun startIntent(context: Context) = Intent(context, GpsTrackingService::class.java)
            .also { it.action = ACTION_START }
    }

    @Inject lateinit var repository: OdooRepository
    @Inject lateinit var prefs: PrefsManager

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationCallback()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startTracking()
        return START_STICKY // Auto-restart if killed by OS; cannot be stopped by user for compliance
    }

    private fun startTracking() {
        startForeground(NOTIFICATION_ID, buildNotification("Initializing GPS tracking..."))
        requestLocationUpdates()
        Log.i(TAG, "GPS Tracking Service started")
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                handleLocation(location)
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                if (!availability.isLocationAvailable) {
                    updateNotification("GPS signal unavailable — retrying...")
                }
            }
        }
    }

    private fun handleLocation(location: Location) {
        val chassis = prefs.activeBikeChassis ?: run {
            updateNotification("No bike assigned — tap to open app")
            return
        }
        val bikeName = prefs.activeBikeName ?: chassis

        serviceScope.launch {
            val success = repository.updateLocation(location.latitude, location.longitude)
            val pendingCount = repository.pendingQueueCount()
            val timeStr = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))

            val message = when {
                success && pendingCount == 0 -> "Synced at $timeStr · ${formatCoords(location)}"
                success && pendingCount > 0 -> "Synced at $timeStr · $pendingCount queued points"
                else -> "Offline — point queued ($pendingCount pending)"
            }

            withContext(Dispatchers.Main) {
                updateNotification(message, bikeName)
            }
        }
    }

    @Suppress("MissingPermission")
    private fun requestLocationUpdates() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_INTERVAL_MS,
        ).apply {
            setMinUpdateIntervalMillis(LOCATION_FASTEST_INTERVAL_MS)
            setWaitForAccurateLocation(false)
            setMaxUpdateDelayMillis(LOCATION_INTERVAL_MS * 2)
        }.build()

        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission not granted", e)
            updateNotification("Location permission required — tap to fix")
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ExpressLease::GpsWakeLock"
        ).also { it.acquire(10 * 60 * 1000L /* 10 min */ ) }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "GPS Location Tracking",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Persistent notification for vehicle location tracking"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(
        content: String,
        subTitle: String? = null,
    ): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, HomeActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(subTitle?.let { "Tracking: $it" } ?: "EXPRESS LEASE — Live Tracking")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification_gps)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(content: String, bikeTitle: String? = prefs.activeBikeName) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(content, bikeTitle))
    }

    private fun formatCoords(loc: Location) =
        "%.5f, %.5f".format(loc.latitude, loc.longitude)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        serviceScope.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
    }
}
