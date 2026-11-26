package com.expresslease.driver.service

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.expresslease.driver.data.OdooRepository
import com.expresslease.driver.data.PrefsManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * WorkManager fallback worker for Doze mode.
 * Runs every 15 minutes (Android's minimum interval) when the device is in Doze/App Standby.
 * Supplements the ForegroundService which may be killed during Doze.
 */
@HiltWorker
class GpsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: OdooRepository,
    private val prefs: PrefsManager,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        if (!prefs.isAuthenticated || prefs.activeBikeChassis == null) {
            return Result.success()
        }

        return try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(applicationContext)
            val locationTask = fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            val location = Tasks.await(locationTask, 30, TimeUnit.SECONDS)

            if (location != null) {
                repository.updateLocation(location.latitude, location.longitude)
            }

            Result.success()
        } catch (e: SecurityException) {
            Log.w("GpsWorker", "Location permission denied", e)
            Result.failure()
        } catch (e: Exception) {
            Log.w("GpsWorker", "GPS worker failed: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "express_lease_gps_fallback"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<GpsWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
