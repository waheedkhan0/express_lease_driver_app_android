package com.expresslease.driver.data

import com.expresslease.driver.model.Bike
import com.google.gson.annotations.SerializedName
import retrofit2.http.*

// ── JSON-RPC envelope types ────────────────────────────────────────────────

data class JsonRpcRequest(
    @SerializedName("jsonrpc") val jsonrpc: String = "2.0",
    @SerializedName("method") val method: String = "call",
    @SerializedName("params") val params: Any,
)

data class LoginParams(
    @SerializedName("db") val db: String,
    @SerializedName("login") val login: String,
    @SerializedName("password") val password: String,
)

data class LocationParams(
    @SerializedName("chassis_number") val chassisNumber: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
)

// ── Response models ────────────────────────────────────────────────────────

data class JsonRpcResponse<T>(
    @SerializedName("result") val result: T?,
    @SerializedName("error") val error: JsonRpcError?,
)

data class JsonRpcError(
    @SerializedName("message") val message: String?,
    @SerializedName("data") val data: JsonRpcErrorData?,
)

data class JsonRpcErrorData(
    @SerializedName("message") val message: String?,
)

data class LoginResult(
    @SerializedName("uid") val uid: Int?,
    @SerializedName("name") val name: String?,
)

data class MyBikesResult(
    @SerializedName("status") val status: String?,
    @SerializedName("message") val message: String?,
    @SerializedName("driver_name") val driverName: String?,
    @SerializedName("bikes") val bikes: List<BikeDto>?,
)

data class BikeDto(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String?,
    @SerializedName("brand") val brand: String?,
    @SerializedName("model_name") val modelName: String?,
    @SerializedName("registration_number") val registrationNumber: Any?, // Odoo returns false for null
    @SerializedName("chassis_number") val chassisNumber: String?,
    @SerializedName("state") val state: Any?,
    @SerializedName("latitude") val latitude: Double?,
    @SerializedName("longitude") val longitude: Double?,
) {
    fun toBike() = Bike(
        id = id,
        name = name ?: "",
        brand = brand ?: "",
        modelName = modelName ?: "",
        registrationNumber = registrationNumber?.takeIf { it != false }?.toString(),
        chassisNumber = chassisNumber ?: "",
        state = state?.takeIf { it != false }?.toString(),
        latitude = latitude,
        longitude = longitude,
    )
}

data class UpdateLocationResult(
    @SerializedName("status") val status: String?,
    @SerializedName("message") val message: String?,
    @SerializedName("bike_id") val bikeId: Int?,
)

// ── Retrofit interface ─────────────────────────────────────────────────────

interface OdooApiService {

    @POST("/web/session/authenticate")
    suspend fun login(
        @Body body: JsonRpcRequest,
    ): JsonRpcResponse<LoginResult>

    @POST("/api/express_lease/my_bikes")
    suspend fun myBikes(
        @Header("Cookie") cookie: String,
        @Body body: JsonRpcRequest,
    ): JsonRpcResponse<MyBikesResult>

    @POST("/api/express_lease/update_location")
    suspend fun updateLocation(
        @Header("Cookie") cookie: String,
        @Body body: JsonRpcRequest,
    ): JsonRpcResponse<UpdateLocationResult>

    @POST("/web/session/destroy")
    suspend fun destroySession(
        @Header("Cookie") cookie: String,
        @Body body: JsonRpcRequest,
    ): JsonRpcResponse<Any>
}
