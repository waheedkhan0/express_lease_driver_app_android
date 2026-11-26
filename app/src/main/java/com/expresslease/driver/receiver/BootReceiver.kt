package com.expresslease.driver.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.expresslease.driver.data.PrefsManager
import com.expresslease.driver.service.GpsTrackingService
import com.expresslease.driver.service.GpsWorker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Receives BOOT_COMPLETED broadcast and auto-restarts GPS tracking
 * if the driver was previously logged in and had an active bike selected.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var prefs: PrefsManager

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        Log.i("BootReceiver", "Boot completed — checking if tracking should resume")

        if (!prefs.isAuthenticated || prefs.activeBikeChassis == null) {
            Log.i("BootReceiver", "No session / no active bike — not starting tracking")
            return
        }

        Log.i("BootReceiver", "Resuming GPS tracking for chassis: ${prefs.activeBikeChassis}")

        // Start foreground service
        val serviceIntent = GpsTrackingService.startIntent(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        // Also schedule WorkManager fallback
        GpsWorker.schedule(context)
    }
}
