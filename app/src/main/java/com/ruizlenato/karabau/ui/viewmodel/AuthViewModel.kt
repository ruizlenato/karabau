package com.ruizlenato.karabau.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ruizlenato.karabau.data.local.SettingsDataStore
import com.ruizlenato.karabau.data.model.DEFAULT_SERVER_ADDRESS
import com.ruizlenato.karabau.data.model.ExchangeKeyResponse
import com.ruizlenato.karabau.data.model.Settings
import com.ruizlenato.karabau.data.model.isLoggedIn
import com.ruizlenato.karabau.data.remote.ApiResult
import com.ruizlenato.karabau.data.remote.KarabauRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "AuthViewModel"

enum class LoginType {
    PASSWORD,
    API_KEY
}

enum class AuthState {
    IDLE,
    LOADING,
    SUCCESS,
    ERROR
}

data class AuthUiState(
    val authState: AuthState = AuthState.IDLE,
    val loginType: LoginType = LoginType.PASSWORD,
    val email: String = "",
    val password: String = "",
    val apiKey: String = "",
    val errorMessage: String? = null,
    val isLoggedIn: Boolean = false,
    val serverAddress: String = DEFAULT_SERVER_ADDRESS
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsDataStore = SettingsDataStore(application)

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = settingsDataStore.settingsFlow.first()
            _uiState.update { currentState ->
                currentState.copy(
                    isLoggedIn = settings.isLoggedIn(),
                    serverAddress = settings.address
                )
            }
        }
    }

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, errorMessage = null) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    fun onApiKeyChange(apiKey: String) {
        _uiState.update { it.copy(apiKey = apiKey, errorMessage = null) }
    }

    fun onLoginTypeChange(loginType: LoginType) {
        _uiState.update { it.copy(loginType = loginType, errorMessage = null) }
    }

    fun onServerAddressChange(address: String) {
        _uiState.update { it.copy(serverAddress = address) }
        viewModelScope.launch {
            settingsDataStore.setServerAddress(address)
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null, authState = AuthState.IDLE) }
    }

    fun login() {
        val currentState = _uiState.value

        if (currentState.serverAddress.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Server address is required") }
            return
        }

        if (!currentState.serverAddress.startsWith("http://") &&
            !currentState.serverAddress.startsWith("https://")
        ) {
            _uiState.update { it.copy(errorMessage = "Server address must start with http:// or https://") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(authState = AuthState.LOADING) }

            val settings = settingsDataStore.settingsFlow.first()
            val repository = KarabauRepository(settings.copy(address = currentState.serverAddress))

            val result = when (currentState.loginType) {
                LoginType.PASSWORD -> {
                    if (currentState.email.isBlank() || currentState.password.isBlank()) {
                        _uiState.update {
                            it.copy(
                                authState = AuthState.ERROR,
                                errorMessage = "Email and password are required"
                            )
                        }
                        return@launch
                    }
                    repository.exchangeKey(currentState.email, currentState.password)
                }

                LoginType.API_KEY -> {
                    if (currentState.apiKey.isBlank()) {
                        _uiState.update {
                            it.copy(
                                authState = AuthState.ERROR,
                                errorMessage = "API key is required"
                            )
                        }
                        return@launch
                    }
                    repository.validateKey(currentState.apiKey)
                }
            }

            when (result) {
                is ApiResult.Success -> {
                    if (currentState.loginType == LoginType.PASSWORD) {
                        val data = result.data as ExchangeKeyResponse
                        val newSettings = settings.copy(
                            address = currentState.serverAddress,
                            apiKey = data.key,
                            apiKeyId = data.id
                        )
                        settingsDataStore.updateSettings(newSettings)
                    } else {
                        val newSettings = settings.copy(
                            address = currentState.serverAddress,
                            apiKey = currentState.apiKey
                        )
                        settingsDataStore.updateSettings(newSettings)
                    }
                    _uiState.update {
                        it.copy(
                            authState = AuthState.SUCCESS,
                            isLoggedIn = true
                        )
                    }
                }

                is ApiResult.Error -> {
                    val errorMsg = when (result.code) {
                        "UNAUTHORIZED" -> if (currentState.loginType == LoginType.PASSWORD)
                            "Wrong username or password" else "Invalid API key"

                        "FORBIDDEN" -> "Password authentication is disabled"
                        "RATE_LIMIT" -> "Too many requests. Please try again later."
                        else -> result.message
                    }
                    _uiState.update {
                        it.copy(
                            authState = AuthState.ERROR,
                            errorMessage = errorMsg
                        )
                    }
                }

                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            authState = AuthState.ERROR,
                            errorMessage = "Network error: ${result.message}"
                        )
                    }
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            val settings = settingsDataStore.settingsFlow.first()
            if (settings.apiKeyId != null) {
                val repository = KarabauRepository(settings)
                repository.revokeKey(settings.apiKeyId)
            }
            settingsDataStore.clearAuth()
            _uiState.update {
                AuthUiState(
                    serverAddress = settings.address,
                    isLoggedIn = false
                )
            }
        }
    }

    fun checkHealth() {
        viewModelScope.launch {
            val settings = settingsDataStore.settingsFlow.first()
            val repository = KarabauRepository(settings)
            val result = repository.healthCheck()
            when (result) {
                is ApiResult.Success -> Log.d(TAG, "Health check passed")
                is ApiResult.Error -> Log.e(TAG, "Health check failed: ${result.message}")
                is ApiResult.NetworkError -> Log.e(TAG, "Health check network error: ${result.message}")
            }
        }
    }
}
