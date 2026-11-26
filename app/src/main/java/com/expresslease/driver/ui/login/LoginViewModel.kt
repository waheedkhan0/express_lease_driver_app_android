package com.expresslease.driver.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expresslease.driver.data.OdooApiService
import com.expresslease.driver.data.JsonRpcRequest
import com.expresslease.driver.data.LoginParams
import com.expresslease.driver.data.OdooRepository
import com.expresslease.driver.data.PrefsManager
import com.expresslease.driver.data.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

data class LoginUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val navigateToHome: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: OdooRepository,
    private val prefs: PrefsManager,
    private val okHttpClient: OkHttpClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun login(serverUrl: String, dbName: String, username: String, password: String) {
        if (serverUrl.isBlank() || dbName.isBlank() || username.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "All fields are required") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val formattedUrl = serverUrl.trim().trimEnd('/').let {
                if (!it.startsWith("http://") && !it.startsWith("https://")) "https://$it" else it
            }

            // We need to get the session cookie from the HTTP response headers.
            // We do this via a raw OkHttp call, then delegate the rest to repository.
            val sessionCookie = getSessionCookie(formattedUrl, dbName, username, password)
            if (sessionCookie == null) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to obtain session. Check credentials.") }
                return@launch
            }

            val result = repository.login(
                serverUrl = formattedUrl,
                dbName = dbName,
                username = username,
                password = password,
                cookie = sessionCookie,
            )

            when (result) {
                is Result.Success -> _uiState.update { it.copy(isLoading = false, navigateToHome = true) }
                is Result.Error -> _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
            }
        }
    }

    private suspend fun getSessionCookie(url: String, db: String, login: String, pass: String): String? {
        return try {
            val body = com.google.gson.Gson().toJson(
                mapOf(
                    "jsonrpc" to "2.0",
                    "method" to "call",
                    "params" to mapOf("db" to db, "login" to login, "password" to pass),
                )
            )
            val request = Request.Builder()
                .url("$url/web/session/authenticate")
                .post(okhttp3.RequestBody.create(okhttp3.MediaType.parse("application/json"), body))
                .build()

            val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                okHttpClient.newCall(request).execute()
            }

            val rawCookie = response.header("Set-Cookie") ?: return null
            val idx = rawCookie.indexOf("session_id=")
            if (idx == -1) return null
            val end = rawCookie.indexOf(";", idx)
            if (end == -1) rawCookie.substring(idx) else rawCookie.substring(idx, end)
        } catch (e: Exception) {
            null
        }
    }
}
