package com.ruizlenato.karabau.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ruizlenato.karabau.data.local.SettingsDataStore
import com.ruizlenato.karabau.data.model.BookmarkItem
import com.ruizlenato.karabau.data.model.Settings
import com.ruizlenato.karabau.data.remote.ApiResult
import com.ruizlenato.karabau.data.remote.KarabauRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = false,
    val bookmarks: List<BookmarkItem> = emptyList(),
    val displayedBookmarks: List<BookmarkItem> = emptyList(),
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val profileName: String? = null,
    val profileImage: String? = null,
    val profileImageHeaders: Map<String, String> = emptyMap(),
    val errorMessage: String? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsDataStore = SettingsDataStore(application)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun loadSavedItems() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val settings = settingsDataStore.settingsFlow.first()
            val repository = KarabauRepository(settings)

            when (val userResult = repository.whoAmI()) {
                is ApiResult.Success -> {
                    val profileImage = resolveProfileImageUrl(
                        serverAddress = settings.address,
                        image = userResult.data.image
                    )
                    val profileImageHeaders = buildProfileImageHeaders(
                        settings = settings,
                        image = userResult.data.image
                    )

                    _uiState.update {
                        it.copy(
                            profileName = userResult.data.name,
                            profileImage = profileImage,
                            profileImageHeaders = profileImageHeaders
                        )
                    }
                }

                is ApiResult.Error -> Unit
                is ApiResult.NetworkError -> Unit
            }

            when (val result = repository.getBookmarks(archived = false, limit = 20)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            bookmarks = result.data,
                            displayedBookmarks = computeDisplayedBookmarks(
                                bookmarks = result.data,
                                query = it.searchQuery,
                                isSearchActive = it.isSearchActive
                            ),
                            errorMessage = null
                        )
                    }
                }

                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message
                        )
                    }
                }

                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                displayedBookmarks = computeDisplayedBookmarks(
                    bookmarks = state.bookmarks,
                    query = query,
                    isSearchActive = state.isSearchActive
                )
            )
        }
    }

    fun onSearchActiveChange(active: Boolean) {
        _uiState.update { state ->
            state.copy(
                isSearchActive = active,
                displayedBookmarks = computeDisplayedBookmarks(
                    bookmarks = state.bookmarks,
                    query = state.searchQuery,
                    isSearchActive = active
                )
            )
        }
    }
}

private fun computeDisplayedBookmarks(
    bookmarks: List<BookmarkItem>,
    query: String,
    isSearchActive: Boolean
): List<BookmarkItem> {
    return if (isSearchActive || query.isNotBlank()) {
        filterBookmarks(bookmarks, query)
    } else {
        bookmarks
    }
}

private fun resolveProfileImageUrl(serverAddress: String, image: String?): String? {
    if (image.isNullOrBlank()) return null
    if (isRemoteImage(image)) return image
    val normalizedBase = serverAddress.trimEnd('/')
    return "$normalizedBase/api/assets/$image"
}

private fun buildProfileImageHeaders(settings: Settings, image: String?): Map<String, String> {
    if (isRemoteImage(image)) return emptyMap()

    val headers = settings.customHeaders.toMutableMap()
    settings.apiKey?.takeIf { it.isNotBlank() }?.let { apiKey ->
        headers["Authorization"] = "Bearer $apiKey"
    }
    return headers
}

private fun isRemoteImage(image: String?): Boolean {
    if (image.isNullOrBlank()) return true
    return image.startsWith("http://") || image.startsWith("https://")
}

private fun filterBookmarks(bookmarks: List<BookmarkItem>, query: String): List<BookmarkItem> {
    val normalized = query.trim().lowercase()
    if (normalized.isBlank()) return bookmarks

    return bookmarks.filter { bookmark ->
        val title = bookmark.title.orEmpty()
        val subtitle = bookmark.subtitle.orEmpty()
        val link = bookmark.linkUrl.orEmpty()
        title.contains(normalized, ignoreCase = true) ||
            subtitle.contains(normalized, ignoreCase = true) ||
            link.contains(normalized, ignoreCase = true)
    }
}
