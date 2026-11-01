package com.ruizlenato.karabau.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ruizlenato.karabau.data.local.SettingsDataStore
import com.ruizlenato.karabau.data.model.Settings
import com.ruizlenato.karabau.data.model.isLoggedIn
import com.ruizlenato.karabau.data.remote.KarabauRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class MainUiState(
    val isLoading: Boolean = true,
    val isLoggedIn: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsDataStore = SettingsDataStore(application)

    companion object {
        private const val KEY_REVOKE_TIMEOUT_MS = 5000L
    }

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = settingsDataStore.settingsFlow.first()
            _uiState.value = MainUiState(
                isLoading = false,
                isLoggedIn = settings.isLoggedIn()
            )
        }
    }

    fun setLoggedIn(loggedIn: Boolean) {
        _uiState.value = _uiState.value.copy(isLoggedIn = loggedIn)
    }

    suspend fun clearAuth() {
        val settings = settingsDataStore.settingsFlow.first()
        revokeApiKeyIfOwned(settings)
        settingsDataStore.clearAuth()
        _uiState.value = _uiState.value.copy(isLoggedIn = false)
    }

    private suspend fun revokeApiKeyIfOwned(settings: Settings) {
        val apiKeyId = settings.apiKeyId ?: return
        withTimeoutOrNull(KEY_REVOKE_TIMEOUT_MS) {
            val repository = KarabauRepository()
            repository.configure(settings)
            repository.revokeKey(apiKeyId)
        }
    }
}
