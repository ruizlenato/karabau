package com.ruizlenato.karabau.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ruizlenato.karabau.data.local.SettingsDataStore
import com.ruizlenato.karabau.data.model.BookmarkItem
import com.ruizlenato.karabau.data.model.Settings
import com.ruizlenato.karabau.data.model.TagItem
import com.ruizlenato.karabau.data.remote.ApiResult
import com.ruizlenato.karabau.data.remote.KarabauRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isTagsLoading: Boolean = false,
    val isTagsRefreshing: Boolean = false,
    val bookmarks: List<BookmarkItem> = emptyList(),
    val displayedBookmarks: List<BookmarkItem> = emptyList(),
    val tags: List<TagItem> = emptyList(),
    val tagsErrorMessage: String? = null,
    val selectedTag: TagItem? = null,
    val selectedTagDetails: TagItem? = null,
    val isTagBookmarksLoading: Boolean = false,
    val tagBookmarks: List<BookmarkItem> = emptyList(),
    val tagBookmarksErrorMessage: String? = null,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val profileName: String? = null,
    val profileImage: String? = null,
    val profileImageHeaders: Map<String, String> = emptyMap(),
    val errorMessage: String? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsDataStore = SettingsDataStore(application)
    private val repository = KarabauRepository()

    private var cachedProfileHeadersKey: Triple<String, String?, String>? = null
    private var cachedProfileHeadersMap: Map<String, String> = emptyMap()

    private var searchDebounceJob: Job? = null

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun loadSavedItems() {
        loadSavedItemsInternal(isRefresh = false)
    }

    fun refreshSavedItems() {
        loadSavedItemsInternal(isRefresh = true)
    }

    private fun loadSavedItemsInternal(isRefresh: Boolean) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = if (isRefresh) it.isLoading else true,
                    isRefreshing = isRefresh,
                    errorMessage = null
                )
            }

            val settings = settingsDataStore.settingsFlow.first()
            repository.configure(settings)

            val (userResult, bookmarksResult) = coroutineScope {
                val userDeferred = async { repository.whoAmI() }
                val bookmarksDeferred = async { repository.getBookmarks(archived = false, limit = 20) }
                userDeferred.await() to bookmarksDeferred.await()
            }

            when (userResult) {
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

                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            profileName = null,
                            profileImage = null,
                            profileImageHeaders = emptyMap()
                        )
                    }
                }

                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            profileName = null,
                            profileImage = null,
                            profileImageHeaders = emptyMap()
                        )
                    }
                }
            }

            when (bookmarksResult) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            bookmarks = bookmarksResult.data,
                            displayedBookmarks = computeDisplayedBookmarks(
                                bookmarks = bookmarksResult.data,
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
                            isRefreshing = false,
                            errorMessage = if (isRefresh && it.bookmarks.isNotEmpty()) null else bookmarksResult.message
                        )
                    }
                }

                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = if (isRefresh && it.bookmarks.isNotEmpty()) null else bookmarksResult.message
                        )
                    }
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { state ->
            state.copy(searchQuery = query)
        }
        searchDebounceJob?.cancel()
        searchDebounceJob = viewModelScope.launch {
            delay(300)
            _uiState.update { state ->
                state.copy(
                    displayedBookmarks = computeDisplayedBookmarks(
                        bookmarks = state.bookmarks,
                        query = query,
                        isSearchActive = state.isSearchActive
                    )
                )
            }
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

    fun loadTags() {
        loadTagsInternal(isRefresh = false)
    }

    fun refreshTags() {
        loadTagsInternal(isRefresh = true)
    }

    private fun loadTagsInternal(isRefresh: Boolean) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isTagsLoading = if (isRefresh) it.isTagsLoading else true,
                    isTagsRefreshing = isRefresh,
                    tagsErrorMessage = null
                )
            }

            val settings = settingsDataStore.settingsFlow.first()
            repository.configure(settings)

            when (
                val result = repository.getTags(
                    limit = 50,
                    sortBy = "usage",
                    page = 0
                )
            ) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isTagsLoading = false,
                            isTagsRefreshing = false,
                            tags = result.data,
                            tagsErrorMessage = null
                        )
                    }
                }

                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isTagsLoading = false,
                            isTagsRefreshing = false,
                            tagsErrorMessage = if (isRefresh && it.tags.isNotEmpty()) null else result.message
                        )
                    }
                }

                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isTagsLoading = false,
                            isTagsRefreshing = false,
                            tagsErrorMessage = if (isRefresh && it.tags.isNotEmpty()) null else result.message
                        )
                    }
                }
            }
        }
    }

    fun openTag(tag: TagItem) {
        _uiState.update {
            it.copy(
                selectedTag = tag,
                selectedTagDetails = null,
                isTagBookmarksLoading = true,
                tagBookmarks = emptyList(),
                tagBookmarksErrorMessage = null
            )
        }
        loadSelectedTagContent()
    }

    fun closeTagDetail() {
        _uiState.update {
            it.copy(
                selectedTag = null,
                selectedTagDetails = null,
                isTagBookmarksLoading = false,
                tagBookmarks = emptyList(),
                tagBookmarksErrorMessage = null
            )
        }
    }

    fun refreshTagBookmarks() {
        if (_uiState.value.selectedTag != null) {
            loadSelectedTagContent()
        }
    }

    private fun loadSelectedTagContent() {
        val selectedTag = _uiState.value.selectedTag ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isTagBookmarksLoading = true,
                    tagBookmarksErrorMessage = null
                )
            }

            val settings = settingsDataStore.settingsFlow.first()
            repository.configure(settings)

            val (tagResult, bookmarksResult) = coroutineScope {
                val tagDeferred = async { repository.getTag(selectedTag.id) }
                val bookmarksDeferred = async {
                    repository.getAllBookmarksByTag(
                        archived = null,
                        tagId = selectedTag.id,
                        limit = 20
                    )
                }
                tagDeferred.await() to bookmarksDeferred.await()
            }

            when (tagResult) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(selectedTagDetails = tagResult.data)
                    }
                }

                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isTagBookmarksLoading = false,
                            tagBookmarksErrorMessage = tagResult.message
                        )
                    }
                }

                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isTagBookmarksLoading = false,
                            tagBookmarksErrorMessage = tagResult.message
                        )
                    }
                }
            }

            when (bookmarksResult) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isTagBookmarksLoading = false,
                            tagBookmarks = bookmarksResult.data,
                            tagBookmarksErrorMessage = null
                        )
                    }
                }

                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isTagBookmarksLoading = false,
                            tagBookmarksErrorMessage = bookmarksResult.message
                        )
                    }
                }

                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isTagBookmarksLoading = false,
                            tagBookmarksErrorMessage = bookmarksResult.message
                        )
                    }
                }
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

        val cacheKey = Triple(settings.address, settings.apiKey, image ?: "")
        if (cachedProfileHeadersKey == cacheKey) return cachedProfileHeadersMap

        val headers = settings.customHeaders.toMutableMap()
        settings.apiKey?.takeIf { it.isNotBlank() }?.let { apiKey ->
            headers["Authorization"] = "Bearer $apiKey"
        }
        cachedProfileHeadersKey = cacheKey
        cachedProfileHeadersMap = headers
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
}
