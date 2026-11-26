package com.expresslease.driver.model

data class Bike(
    val id: Int,
    val name: String,
    val brand: String,
    val modelName: String,
    val registrationNumber: String?,
    val chassisNumber: String,
    val state: String?,
    val latitude: Double?,
    val longitude: Double?,
) {
    val displayName: String
        get() = "$brand $modelName${if (!registrationNumber.isNullOrBlank()) " — $registrationNumber" else ""}"
}
