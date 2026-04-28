package com.ruizlenato.karabau.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ruizlenato.karabau.data.local.LocalCacheManager
import com.ruizlenato.karabau.data.local.SettingsDataStore
import com.ruizlenato.karabau.data.model.BookmarkItem
import com.ruizlenato.karabau.data.model.SavedListItem
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
    val isListsLoading: Boolean = false,
    val isListsRefreshing: Boolean = false,
    val bookmarks: List<BookmarkItem> = emptyList(),
    val displayedBookmarks: List<BookmarkItem> = emptyList(),
    val tags: List<TagItem> = emptyList(),
    val tagsErrorMessage: String? = null,
    val lists: List<SavedListItem> = emptyList(),
    val listsErrorMessage: String? = null,
    val selectedTag: TagItem? = null,
    val selectedTagDetails: TagItem? = null,
    val selectedList: SavedListItem? = null,
    val selectedListDetails: SavedListItem? = null,
    val isTagBookmarksLoading: Boolean = false,
    val isListBookmarksLoading: Boolean = false,
    val tagBookmarks: List<BookmarkItem> = emptyList(),
    val listBookmarks: List<BookmarkItem> = emptyList(),
    val tagBookmarksErrorMessage: String? = null,
    val listBookmarksErrorMessage: String? = null,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val profileName: String? = null,
    val profileImage: String? = null,
    val profileImageHeaders: Map<String, String> = emptyMap(),
    val hasCompletedInitialBookmarksLoad: Boolean = false,
    val errorMessage: String? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val FAVORITES_LIST_ID = "__favorites__"
    }

    private val settingsDataStore = SettingsDataStore(application)
    private val cacheManager = LocalCacheManager(application)
    private val repository = KarabauRepository()

    private var cachedProfileHeadersKey: Triple<String, String?, String>? = null
    private var cachedProfileHeadersMap: Map<String, String> = emptyMap()

    private var searchDebounceJob: Job? = null
    private var tagDetailJob: Job? = null
    private var listDetailJob: Job? = null
    private var hasLoadedItems = false
    private var hasLoadedTags = false
    private var hasLoadedLists = false

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun loadSavedItems() {
        if (hasLoadedItems && _uiState.value.bookmarks.isNotEmpty()) return
        viewModelScope.launch {
            val settingsDeferred = async { settingsDataStore.settingsFlow.first() }

            if (_uiState.value.bookmarks.isEmpty()) {
                val cached = cacheManager.loadCachedBookmarks()
                if (!cached.isNullOrEmpty()) {
                    _uiState.update {
                        it.copy(
                            bookmarks = cached,
                            displayedBookmarks = computeDisplayedBookmarks(
                                bookmarks = cached,
                                query = it.searchQuery,
                                isSearchActive = it.isSearchActive
                            )
                        )
                    }
                }
            }

            val settings = settingsDeferred.await()

            cacheManager.loadCachedProfile(profileCacheKey(settings))?.let { cachedProfile ->
                val profileImage = resolveProfileImageUrl(
                    serverAddress = settings.address,
                    image = cachedProfile.profileImage
                )
                val profileImageHeaders = buildProfileImageHeaders(
                    settings = settings,
                    image = cachedProfile.profileImage
                )

                _uiState.update {
                    it.copy(
                        profileName = cachedProfile.profileName,
                        profileImage = profileImage,
                        profileImageHeaders = profileImageHeaders
                    )
                }
            }

            loadSavedItemsInternal(isRefresh = false, preloadedSettings = settings)
        }
    }

    fun refreshSavedItems() {
        viewModelScope.launch {
            loadSavedItemsInternal(isRefresh = true)
        }
    }

    private suspend fun loadSavedItemsInternal(isRefresh: Boolean, preloadedSettings: Settings? = null) {
        val hasExistingData = _uiState.value.bookmarks.isNotEmpty()
        _uiState.update {
            it.copy(
                isLoading = if (isRefresh || hasExistingData) it.isLoading else true,
                isRefreshing = isRefresh,
                errorMessage = null
            )
        }

        val settings = preloadedSettings ?: settingsDataStore.settingsFlow.first()
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

                viewModelScope.launch {
                    cacheManager.saveProfile(
                        cacheKey = profileCacheKey(settings),
                        profileName = userResult.data.name,
                        profileImage = userResult.data.image
                    )
                }
            }

            is ApiResult.Error -> {
                Unit
            }

            is ApiResult.NetworkError -> {
                Unit
            }
        }

        when (bookmarksResult) {
            is ApiResult.Success -> {
                hasLoadedItems = true
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
                        hasCompletedInitialBookmarksLoad = true,
                        errorMessage = null
                    )
                }
                viewModelScope.launch { cacheManager.saveBookmarks(bookmarksResult.data) }
            }

            is ApiResult.Error -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        hasCompletedInitialBookmarksLoad = true,
                        errorMessage = if ((isRefresh || hasExistingData) && it.bookmarks.isNotEmpty()) null else bookmarksResult.message
                    )
                }
            }

            is ApiResult.NetworkError -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        hasCompletedInitialBookmarksLoad = true,
                        errorMessage = if ((isRefresh || hasExistingData) && it.bookmarks.isNotEmpty()) null else bookmarksResult.message
                    )
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
        if (hasLoadedTags && _uiState.value.tags.isNotEmpty()) return
        viewModelScope.launch {
            if (_uiState.value.tags.isEmpty()) {
                val cached = cacheManager.loadCachedTags()
                if (!cached.isNullOrEmpty()) {
                    _uiState.update { it.copy(tags = cached) }
                }
            }
            loadTagsInternal(isRefresh = false)
        }
    }

    fun refreshTags() {
        loadTagsInternal(isRefresh = true)
    }

    fun loadLists() {
        if (hasLoadedLists && _uiState.value.lists.isNotEmpty()) return
        loadListsInternal(isRefresh = false)
    }

    fun refreshLists() {
        loadListsInternal(isRefresh = true)
    }

    private fun loadListsInternal(isRefresh: Boolean) {
        viewModelScope.launch {
            val hasExistingLists = _uiState.value.lists.isNotEmpty()
            _uiState.update {
                it.copy(
                    isListsLoading = if (isRefresh || hasExistingLists) it.isListsLoading else true,
                    isListsRefreshing = isRefresh,
                    listsErrorMessage = null
                )
            }

            val settings = settingsDataStore.settingsFlow.first()
            repository.configure(settings)

            when (val result = repository.getLists()) {
                is ApiResult.Success -> {
                    hasLoadedLists = true
                    _uiState.update {
                        it.copy(
                            isListsLoading = false,
                            isListsRefreshing = false,
                            lists = result.data,
                            listsErrorMessage = null
                        )
                    }
                }

                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isListsLoading = false,
                            isListsRefreshing = false,
                            listsErrorMessage = if ((isRefresh || hasExistingLists) && it.lists.isNotEmpty()) null else result.message
                        )
                    }
                }

                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isListsLoading = false,
                            isListsRefreshing = false,
                            listsErrorMessage = if ((isRefresh || hasExistingLists) && it.lists.isNotEmpty()) null else result.message
                        )
                    }
                }
            }
        }
    }

    private fun loadTagsInternal(isRefresh: Boolean) {
        viewModelScope.launch {
            val hasExistingTags = _uiState.value.tags.isNotEmpty()
            _uiState.update {
                it.copy(
                    isTagsLoading = if (isRefresh || hasExistingTags) it.isTagsLoading else true,
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
                    hasLoadedTags = true
                    _uiState.update {
                        it.copy(
                            isTagsLoading = false,
                            isTagsRefreshing = false,
                            tags = result.data,
                            tagsErrorMessage = null
                        )
                    }
                    launch { cacheManager.saveTags(result.data) }
                }

                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isTagsLoading = false,
                            isTagsRefreshing = false,
                            tagsErrorMessage = if ((isRefresh || hasExistingTags) && it.tags.isNotEmpty()) null else result.message
                        )
                    }
                }

                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isTagsLoading = false,
                            isTagsRefreshing = false,
                            tagsErrorMessage = if ((isRefresh || hasExistingTags) && it.tags.isNotEmpty()) null else result.message
                        )
                    }
                }
            }
        }
    }

    fun openTag(tag: TagItem) {
        tagDetailJob?.cancel()
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
        tagDetailJob?.cancel()
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

    fun openList(list: SavedListItem) {
        listDetailJob?.cancel()
        _uiState.update {
            it.copy(
                selectedList = list,
                selectedListDetails = null,
                isListBookmarksLoading = true,
                listBookmarks = emptyList(),
                listBookmarksErrorMessage = null
            )
        }
        loadSelectedListContent()
    }

    fun openFavoritesList() {
        openList(
            SavedListItem(
                id = FAVORITES_LIST_ID,
                name = "Favorites",
                description = "Your favourited bookmarks",
                icon = "⭐",
                parentId = null,
                type = "smart",
                query = "favourited:true",
                isPublic = false,
                hasCollaborators = false,
                userRole = "owner"
            )
        )
    }

    fun toggleBookmarkFavourite(
        bookmark: BookmarkItem,
        onUpdated: (BookmarkItem) -> Unit = {}
    ) {
        viewModelScope.launch {
            val settings = settingsDataStore.settingsFlow.first()
            repository.configure(settings)

            val updatedBookmark = bookmark.copy(favourited = !bookmark.favourited)
            when (repository.setBookmarkFavourited(bookmark.id, updatedBookmark.favourited)) {
                is ApiResult.Success -> {
                    applyBookmarkUpdate(updatedBookmark)
                    onUpdated(updatedBookmark)
                }

                is ApiResult.Error -> Unit
                is ApiResult.NetworkError -> Unit
            }
        }
    }

    fun deleteBookmark(
        bookmark: BookmarkItem,
        onDeleted: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val settings = settingsDataStore.settingsFlow.first()
            repository.configure(settings)

            when (repository.deleteBookmark(bookmark.id)) {
                is ApiResult.Success -> {
                    applyBookmarkRemoval(bookmark.id)
                    onDeleted()
                }

                is ApiResult.Error -> Unit
                is ApiResult.NetworkError -> Unit
            }
        }
    }

    fun closeListDetail() {
        listDetailJob?.cancel()
        _uiState.update {
            it.copy(
                selectedList = null,
                selectedListDetails = null,
                isListBookmarksLoading = false,
                listBookmarks = emptyList(),
                listBookmarksErrorMessage = null
            )
        }
    }

    fun refreshListBookmarks() {
        if (_uiState.value.selectedList != null) {
            loadSelectedListContent()
        }
    }

    private fun loadSelectedListContent() {
        val selectedList = _uiState.value.selectedList ?: return

        listDetailJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isListBookmarksLoading = true,
                    listBookmarksErrorMessage = null
                )
            }

            val settings = settingsDataStore.settingsFlow.first()
            repository.configure(settings)

            val (listResult, bookmarksResult) = if (selectedList.id == FAVORITES_LIST_ID) {
                ApiResult.Success(selectedList) to repository.getAllFavouritedBookmarks(
                    archived = false,
                    limit = 20
                )
            } else {
                coroutineScope {
                    val listDeferred = async { repository.getList(selectedList.id) }
                    val bookmarksDeferred = async {
                        repository.getAllBookmarksByList(
                            archived = null,
                            listId = selectedList.id,
                            limit = 20
                        )
                    }
                    listDeferred.await() to bookmarksDeferred.await()
                }
            }

            if (_uiState.value.selectedList?.id != selectedList.id) return@launch

            when (listResult) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(selectedListDetails = listResult.data) }
                }

                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isListBookmarksLoading = false,
                            listBookmarksErrorMessage = listResult.message
                        )
                    }
                }

                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isListBookmarksLoading = false,
                            listBookmarksErrorMessage = listResult.message
                        )
                    }
                }
            }

            when (bookmarksResult) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isListBookmarksLoading = false,
                            listBookmarks = bookmarksResult.data,
                            listBookmarksErrorMessage = null
                        )
                    }
                }

                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isListBookmarksLoading = false,
                            listBookmarksErrorMessage = bookmarksResult.message
                        )
                    }
                }

                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isListBookmarksLoading = false,
                            listBookmarksErrorMessage = bookmarksResult.message
                        )
                    }
                }
            }
        }
    }

    private fun applyBookmarkUpdate(updatedBookmark: BookmarkItem) {
        var snapshotBookmarks: List<BookmarkItem> = emptyList()

        _uiState.update { state ->
            val isFavoritesListOpen = state.selectedList?.id == FAVORITES_LIST_ID

            val bookmarksUpdated = state.bookmarks.map { current ->
                if (current.id == updatedBookmark.id) updatedBookmark else current
            }

            val tagBookmarksUpdated = state.tagBookmarks.map { current ->
                if (current.id == updatedBookmark.id) updatedBookmark else current
            }

            val listBookmarksUpdated = state.listBookmarks
                .map { current -> if (current.id == updatedBookmark.id) updatedBookmark else current }
                .let { items ->
                    if (isFavoritesListOpen && !updatedBookmark.favourited) {
                        items.filterNot { it.id == updatedBookmark.id }
                    } else {
                        items
                    }
                }

            snapshotBookmarks = bookmarksUpdated

            state.copy(
                bookmarks = bookmarksUpdated,
                displayedBookmarks = computeDisplayedBookmarks(
                    bookmarks = bookmarksUpdated,
                    query = state.searchQuery,
                    isSearchActive = state.isSearchActive
                ),
                tagBookmarks = tagBookmarksUpdated,
                listBookmarks = listBookmarksUpdated
            )
        }

        if (snapshotBookmarks.isNotEmpty()) {
            viewModelScope.launch { cacheManager.saveBookmarks(snapshotBookmarks) }
        }
    }

    private fun applyBookmarkRemoval(bookmarkId: String) {
        var snapshotBookmarks: List<BookmarkItem> = emptyList()

        _uiState.update { state ->
            val bookmarksUpdated = state.bookmarks.filterNot { it.id == bookmarkId }
            val tagBookmarksUpdated = state.tagBookmarks.filterNot { it.id == bookmarkId }
            val listBookmarksUpdated = state.listBookmarks.filterNot { it.id == bookmarkId }

            snapshotBookmarks = bookmarksUpdated

            state.copy(
                bookmarks = bookmarksUpdated,
                displayedBookmarks = computeDisplayedBookmarks(
                    bookmarks = bookmarksUpdated,
                    query = state.searchQuery,
                    isSearchActive = state.isSearchActive
                ),
                tagBookmarks = tagBookmarksUpdated,
                listBookmarks = listBookmarksUpdated
            )
        }

        viewModelScope.launch { cacheManager.saveBookmarks(snapshotBookmarks) }
    }

    private fun loadSelectedTagContent() {
        val selectedTag = _uiState.value.selectedTag ?: return

        tagDetailJob = viewModelScope.launch {
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

            if (_uiState.value.selectedTag?.id != selectedTag.id) return@launch

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

    private fun profileCacheKey(settings: Settings): String {
        return "${settings.address.trimEnd('/')}|${settings.apiKeyId.orEmpty()}"
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
