package com.expresslease.driver.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrefsManager @Inject constructor(@ApplicationContext context: Context) {

    companion object {
        const val KEY_SERVER_URL = "odoo_server_url"
        const val KEY_DB_NAME = "odoo_db_name"
        const val KEY_USERNAME = "odoo_username"
        const val KEY_PASSWORD = "odoo_password"
        const val KEY_SESSION_COOKIE = "odoo_session_cookie"
        const val KEY_DRIVER_NAME = "odoo_driver_name"
        const val KEY_ACTIVE_BIKE_CHASSIS = "odoo_active_bike_chassis"
        const val KEY_ACTIVE_BIKE_NAME = "odoo_active_bike_name"
        const val KEY_ACTIVE_BIKE_ID = "odoo_active_bike_id"
        const val KEY_LAST_SYNC_TIME = "odoo_last_sync_time"
        const val KEY_LAST_LAT = "odoo_last_lat"
        const val KEY_LAST_LNG = "odoo_last_lng"

        private const val PREFS_FILE_NAME = "express_lease_secure_prefs"
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var serverUrl: String?
        get() = prefs.getString(KEY_SERVER_URL, null)
        set(v) = prefs.edit().putString(KEY_SERVER_URL, v).apply()

    var dbName: String?
        get() = prefs.getString(KEY_DB_NAME, null)
        set(v) = prefs.edit().putString(KEY_DB_NAME, v).apply()

    var username: String?
        get() = prefs.getString(KEY_USERNAME, null)
        set(v) = prefs.edit().putString(KEY_USERNAME, v).apply()

    var password: String?
        get() = prefs.getString(KEY_PASSWORD, null)
        set(v) = prefs.edit().putString(KEY_PASSWORD, v).apply()

    var sessionCookie: String?
        get() = prefs.getString(KEY_SESSION_COOKIE, null)
        set(v) = prefs.edit().putString(KEY_SESSION_COOKIE, v).apply()

    var driverName: String?
        get() = prefs.getString(KEY_DRIVER_NAME, null)
        set(v) = prefs.edit().putString(KEY_DRIVER_NAME, v).apply()

    var activeBikeChassis: String?
        get() = prefs.getString(KEY_ACTIVE_BIKE_CHASSIS, null)
        set(v) = prefs.edit().putString(KEY_ACTIVE_BIKE_CHASSIS, v).apply()

    var activeBikeName: String?
        get() = prefs.getString(KEY_ACTIVE_BIKE_NAME, null)
        set(v) = prefs.edit().putString(KEY_ACTIVE_BIKE_NAME, v).apply()

    var activeBikeId: Int
        get() = prefs.getInt(KEY_ACTIVE_BIKE_ID, -1)
        set(v) = prefs.edit().putInt(KEY_ACTIVE_BIKE_ID, v).apply()

    var lastSyncTime: String?
        get() = prefs.getString(KEY_LAST_SYNC_TIME, null)
        set(v) = prefs.edit().putString(KEY_LAST_SYNC_TIME, v).apply()

    var lastLat: Float
        get() = prefs.getFloat(KEY_LAST_LAT, 0f)
        set(v) = prefs.edit().putFloat(KEY_LAST_LAT, v).apply()

    var lastLng: Float
        get() = prefs.getFloat(KEY_LAST_LNG, 0f)
        set(v) = prefs.edit().putFloat(KEY_LAST_LNG, v).apply()

    val isAuthenticated: Boolean
        get() = !sessionCookie.isNullOrBlank() && !serverUrl.isNullOrBlank()

    fun clearSession() {
        prefs.edit()
            .remove(KEY_SESSION_COOKIE)
            .remove(KEY_DRIVER_NAME)
            .remove(KEY_ACTIVE_BIKE_CHASSIS)
            .remove(KEY_ACTIVE_BIKE_NAME)
            .remove(KEY_ACTIVE_BIKE_ID)
            .remove(KEY_LAST_SYNC_TIME)
            .remove(KEY_LAST_LAT)
            .remove(KEY_LAST_LNG)
            .apply()
    }
}
