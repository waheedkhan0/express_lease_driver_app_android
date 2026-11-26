package com.expresslease.driver.data

import android.content.Context
import android.util.Log
import com.expresslease.driver.data.local.AppDatabase
import com.expresslease.driver.data.local.PendingGpsPoint
import com.expresslease.driver.model.Bike
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
}

@Singleton
class OdooRepository @Inject constructor(
    private val api: OdooApiService,
    private val prefs: PrefsManager,
    private val db: AppDatabase,
    @ApplicationContext private val context: Context,
) {
    private val TAG = "OdooRepository"

    // ── Authentication ─────────────────────────────────────────────────────

    suspend fun login(
        serverUrl: String,
        dbName: String,
        username: String,
        password: String,
        rawHeaders: okhttp3.Headers? = null, // passed from interceptor
        cookie: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Persist settings
            prefs.serverUrl = serverUrl.trimEnd('/')
            prefs.dbName = dbName
            prefs.username = username
            prefs.password = password
            prefs.sessionCookie = cookie

            val response = api.login(
                JsonRpcRequest(params = LoginParams(dbName, username, password))
            )
            if (response.error != null) {
                val msg = response.error.data?.message ?: response.error.message ?: "Authentication failed"
                return@withContext Result.Error(msg)
            }
            val uid = response.result?.uid ?: return@withContext Result.Error("Invalid server response")
            val name = response.result.name?.takeIf { it.isNotBlank() } ?: username
            prefs.driverName = name
            Result.Success(name)
        } catch (e: Exception) {
            Log.e(TAG, "login error", e)
            Result.Error("Connection error: ${e.localizedMessage}")
        }
    }

    // ── Bikes ──────────────────────────────────────────────────────────────

    suspend fun fetchMyBikes(): Result<Pair<String, List<Bike>>> = withContext(Dispatchers.IO) {
        val cookie = prefs.sessionCookie ?: return@withContext Result.Error("Not authenticated")
        try {
            val response = api.myBikes(cookie, JsonRpcRequest(params = emptyMap<String, Any>()))
            if (response.error != null) {
                return@withContext Result.Error(response.error.message ?: "Failed to load bikes")
            }
            val result = response.result ?: return@withContext Result.Error("Empty response")
            if (result.status == "error") return@withContext Result.Error(result.message ?: "Error")
            val driverName = result.driverName?.takeIf { it.isNotBlank() } ?: prefs.driverName ?: ""
            prefs.driverName = driverName
            Result.Success(Pair(driverName, result.bikes?.map { it.toBike() } ?: emptyList()))
        } catch (e: Exception) {
            Log.e(TAG, "fetchMyBikes error", e)
            Result.Error("Failed to load bikes: ${e.localizedMessage}")
        }
    }

    // ── Location ───────────────────────────────────────────────────────────

    /**
     * Posts GPS coordinates to Odoo. If offline, queues to Room DB.
     * Returns true if synced immediately, false if queued.
     */
    suspend fun updateLocation(latitude: Double, longitude: Double): Boolean =
        withContext(Dispatchers.IO) {
            val cookie = prefs.sessionCookie ?: return@withContext false
            val chassis = prefs.activeBikeChassis ?: return@withContext false

            // First try to flush any pending offline queue
            flushPendingQueue()

            try {
                val response = api.updateLocation(
                    cookie,
                    JsonRpcRequest(params = LocationParams(chassis, latitude, longitude))
                )
                val success = response.result?.status == "success"
                if (success) {
                    prefs.lastLat = latitude.toFloat()
                    prefs.lastLng = longitude.toFloat()
                    prefs.lastSyncTime = java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
                }
                success
            } catch (e: Exception) {
                Log.w(TAG, "GPS sync failed — queuing offline point", e)
                // Queue for later retry
                db.pendingGpsPointDao().insert(
                    PendingGpsPoint(chassisNumber = chassis, latitude = latitude, longitude = longitude)
                )
                false
            }
        }

    private suspend fun flushPendingQueue() {
        val cookie = prefs.sessionCookie ?: return
        val pending = db.pendingGpsPointDao().getPending()
        for (point in pending) {
            try {
                val response = api.updateLocation(
                    cookie,
                    JsonRpcRequest(params = LocationParams(point.chassisNumber, point.latitude, point.longitude))
                )
                if (response.result?.status == "success") {
                    db.pendingGpsPointDao().delete(point)
                } else {
                    db.pendingGpsPointDao().incrementRetry(point.id)
                }
            } catch (e: Exception) {
                db.pendingGpsPointDao().incrementRetry(point.id)
            }
        }
        db.pendingGpsPointDao().purgeFailed()
    }

    suspend fun pendingQueueCount(): Int = db.pendingGpsPointDao().count()

    // ── Logout ─────────────────────────────────────────────────────────────

    suspend fun logout() = withContext(Dispatchers.IO) {
        val cookie = prefs.sessionCookie
        val serverUrl = prefs.serverUrl
        if (cookie != null && serverUrl != null) {
            try {
                api.destroySession(cookie, JsonRpcRequest(params = emptyMap<String, Any>()))
            } catch (e: Exception) { /* best effort */ }
        }
        prefs.clearSession()
    }
}
