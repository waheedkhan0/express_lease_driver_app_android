package com.expresslease.driver.ui.home

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expresslease.driver.data.OdooRepository
import com.expresslease.driver.data.PrefsManager
import com.expresslease.driver.data.Result
import com.expresslease.driver.model.Bike
import com.expresslease.driver.service.GpsTrackingService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val driverName: String = "Driver",
    val assignedBike: Bike? = null,
    val isUnassigned: Boolean = false,
    val isLoading: Boolean = false,
    val isTrackingActive: Boolean = false,
    val lastLat: Double? = null,
    val lastLng: Double? = null,
    val lastSyncTime: String? = null,
    val lastSyncSuccess: Boolean = false,
    val pendingCount: Int = 0,
    val statusMessage: String? = null,
    val permissionWarning: String? = null,
    val navigateToLogin: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: OdooRepository,
    private val prefs: PrefsManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadFromPrefs()
        loadBikes()
    }

    private fun loadFromPrefs() {
        _uiState.update {
            it.copy(
                driverName = prefs.driverName ?: "Driver",
                lastLat = prefs.lastLat.toDouble().takeIf { v -> v != 0.0 },
                lastLng = prefs.lastLng.toDouble().takeIf { v -> v != 0.0 },
                lastSyncTime = prefs.lastSyncTime,
            )
        }
    }

    fun loadBikes() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = repository.fetchMyBikes()) {
                is Result.Success -> {
                    val (driverName, bikes) = result.data
                    val bike = bikes.firstOrNull()

                    if (bike != null) {
                        prefs.activeBikeChassis = bike.chassisNumber
                        prefs.activeBikeName = bike.displayName
                        prefs.activeBikeId = bike.id
                    } else {
                        prefs.activeBikeChassis = null
                        prefs.activeBikeName = null
                        prefs.activeBikeId = -1
                    }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            driverName = driverName.ifBlank { prefs.driverName ?: "Driver" },
                            assignedBike = bike,
                            isUnassigned = bike == null,
                            statusMessage = if (bike != null) {
                                "Assigned bike: ${bike.displayName}"
                            } else {
                                "No motorbike assigned to your account"
                            },
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isLoading = false, statusMessage = result.message) }
                }
            }
        }
    }

    fun setTrackingActive(active: Boolean) {
        _uiState.update { it.copy(isTrackingActive = active) }
    }

    fun setPermissionWarning(msg: String?) {
        _uiState.update { it.copy(permissionWarning = msg) }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _uiState.update { it.copy(navigateToLogin = true) }
        }
    }
}
