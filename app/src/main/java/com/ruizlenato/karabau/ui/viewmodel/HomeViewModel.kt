package com.ruizlenato.karabau.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ruizlenato.karabau.data.local.LocalCacheManager
import com.ruizlenato.karabau.data.local.SettingsDataStore
import com.ruizlenato.karabau.data.model.BookmarkItem
import com.ruizlenato.karabau.data.model.ArchiveDisplayBehaviour
import com.ruizlenato.karabau.data.model.SavedListItem
import com.ruizlenato.karabau.data.model.Settings
import com.ruizlenato.karabau.data.model.TagItem
import com.ruizlenato.karabau.data.remote.ApiResult
import com.ruizlenato.karabau.data.remote.KarabauRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext

data class HomeUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isTagsLoading: Boolean = false,
    val isTagsRefreshing: Boolean = false,
    val isListsLoading: Boolean = false,
    val isListsRefreshing: Boolean = false,
    val bookmarks: ImmutableList<BookmarkItem> = persistentListOf(),
    val tags: ImmutableList<TagItem> = persistentListOf(),
    val tagsErrorMessage: String? = null,
    val lists: ImmutableList<SavedListItem> = persistentListOf(),
    val listsErrorMessage: String? = null,
    val selectedTag: TagItem? = null,
    val selectedTagDetails: TagItem? = null,
    val selectedList: SavedListItem? = null,
    val selectedListDetails: SavedListItem? = null,
    val isTagBookmarksLoading: Boolean = false,
    val isListBookmarksLoading: Boolean = false,
    val tagBookmarks: ImmutableList<BookmarkItem> = persistentListOf(),
    val listBookmarks: ImmutableList<BookmarkItem> = persistentListOf(),
    val tagBookmarksErrorMessage: String? = null,
    val listBookmarksErrorMessage: String? = null,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val profileName: String? = null,
    val profileImage: String? = null,
    val profileImageHeaders: ImmutableMap<String, String> = persistentMapOf(),
    val hasCompletedInitialBookmarksLoad: Boolean = false,
    val errorMessage: String? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val FAVORITES_LIST_ID = "__favorites__"
        private const val CACHE_SAVE_DEBOUNCE_MS = 500L
    }

    private val settingsDataStore = SettingsDataStore(application)
    private val cacheManager = LocalCacheManager(application)
    private val repository = KarabauRepository()

    private var cachedProfileHeadersKey: Triple<String, String?, String>? = null
    private var cachedProfileHeadersMap: Map<String, String> = emptyMap()

    private var searchDebounceJob: Job? = null
    private var tagDetailJob: Job? = null
    private var listDetailJob: Job? = null
    private var cacheSaveJob: Job? = null
    private var hasLoadedItems = false
    private var hasLoadedTags = false
    private var hasLoadedLists = false
    private val pendingDeleteJobs = mutableMapOf<String, Job>()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _displayedBookmarks = MutableStateFlow<ImmutableList<BookmarkItem>>(persistentListOf())
    val displayedBookmarks: StateFlow<ImmutableList<BookmarkItem>> = _displayedBookmarks.asStateFlow()

    fun loadSavedItems() {
        if (hasLoadedItems && _uiState.value.bookmarks.isNotEmpty()) return
        viewModelScope.launch {
            val settingsDeferred = async { settingsDataStore.settingsFlow.first() }

            if (_uiState.value.bookmarks.isEmpty()) {
                val cached = cacheManager.loadCachedBookmarks()
                if (!cached.isNullOrEmpty()) {
                    val displayed = computeDisplayedBookmarks(
                        bookmarks = cached,
                        query = _uiState.value.searchQuery,
                        isSearchActive = _uiState.value.isSearchActive
                    )
                    _uiState.update {
                        it.copy(
                            bookmarks = cached.toImmutableList()
                        )
                    }
                    _displayedBookmarks.value = displayed.toImmutableList()
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
                        profileImageHeaders = profileImageHeaders.toImmutableMap()
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

    fun syncArchiveDisplayBehaviourAfterInitialLoad() {
        viewModelScope.launch {
            val settings = settingsDataStore.settingsFlow.first()
            repository.configure(settings)

            when (val remote = repository.getArchiveDisplayBehaviour()) {
                is ApiResult.Success -> {
                    if (settings.archiveDisplayBehaviour != remote.data) {
                        settingsDataStore.updateSettings(
                            settings.copy(archiveDisplayBehaviour = remote.data)
                        )
                    }
                }

                is ApiResult.Error -> Unit
                is ApiResult.NetworkError -> Unit
            }
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
                        profileImageHeaders = profileImageHeaders.toImmutableMap()
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
                // no-op
            }

            is ApiResult.NetworkError -> {
                // no-op
            }
        }

        when (bookmarksResult) {
            is ApiResult.Success -> {
                hasLoadedItems = true
                val bookmarksData = bookmarksResult.data
                val displayed = computeDisplayedBookmarks(
                    bookmarks = bookmarksData,
                    query = _uiState.value.searchQuery,
                    isSearchActive = _uiState.value.isSearchActive
                )
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        bookmarks = bookmarksData.toImmutableList(),
                        hasCompletedInitialBookmarksLoad = true,
                        errorMessage = null
                    )
                }
                _displayedBookmarks.value = displayed.toImmutableList()
                viewModelScope.launch { cacheManager.saveBookmarks(bookmarksData) }
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
            val bookmarks = _uiState.value.bookmarks
            val isSearchActive = _uiState.value.isSearchActive
            val displayed = withContext(Dispatchers.Default) {
                computeDisplayedBookmarks(
                    bookmarks = bookmarks,
                    query = query,
                    isSearchActive = isSearchActive
                )
            }
            _displayedBookmarks.value = displayed.toImmutableList()
        }
    }

    fun onSearchActiveChange(active: Boolean) {
        _uiState.update { state ->
            state.copy(isSearchActive = active)
        }
        viewModelScope.launch {
            val bookmarks = _uiState.value.bookmarks
            val query = _uiState.value.searchQuery
            val displayed = withContext(Dispatchers.Default) {
                computeDisplayedBookmarks(
                    bookmarks = bookmarks,
                    query = query,
                    isSearchActive = active
                )
            }
            _displayedBookmarks.value = displayed.toImmutableList()
        }
    }

    fun loadTags() {
        if (hasLoadedTags && _uiState.value.tags.isNotEmpty()) return
        viewModelScope.launch {
            if (_uiState.value.tags.isEmpty()) {
                val cached = cacheManager.loadCachedTags()
                if (!cached.isNullOrEmpty()) {
                    _uiState.update { it.copy(tags = cached.toImmutableList()) }
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
                            lists = result.data.toImmutableList(),
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
                            tags = result.data.toImmutableList(),
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
                tagBookmarks = persistentListOf(),
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
                tagBookmarks = persistentListOf(),
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
                listBookmarks = persistentListOf(),
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

    fun toggleBookmarkArchived(
        bookmark: BookmarkItem,
        onUpdated: (BookmarkItem) -> Unit = {}
    ) {
        viewModelScope.launch {
            val settings = settingsDataStore.settingsFlow.first()
            repository.configure(settings)

            val updatedBookmark = bookmark.copy(archived = !bookmark.archived)
            when (repository.setBookmarkArchived(bookmark.id, updatedBookmark.archived)) {
                is ApiResult.Success -> {
                    applyBookmarkUpdate(updatedBookmark)
                    onUpdated(updatedBookmark)
                }

                is ApiResult.Error -> Unit
                is ApiResult.NetworkError -> Unit
            }
        }
    }

    fun deleteBookmarkWithUndo(bookmark: BookmarkItem) {
        pendingDeleteJobs[bookmark.id]?.cancel()
        applyBookmarkRemoval(bookmark.id)

        pendingDeleteJobs[bookmark.id] = viewModelScope.launch {
            delay(4500)

            val settings = settingsDataStore.settingsFlow.first()
            repository.configure(settings)
            repository.deleteBookmark(bookmark.id)

            pendingDeleteJobs.remove(bookmark.id)
        }
    }

    fun undoDeleteBookmark(bookmark: BookmarkItem) {
        pendingDeleteJobs.remove(bookmark.id)?.cancel()
        applyBookmarkRestore(bookmark)
    }

    fun closeListDetail() {
        listDetailJob?.cancel()
        _uiState.update {
            it.copy(
                selectedList = null,
                selectedListDetails = null,
                isListBookmarksLoading = false,
                listBookmarks = persistentListOf(),
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
            val settings = settingsDataStore.settingsFlow.first()
            val archivedFilter = archivedFilterFor(settings)

            if (_uiState.value.listBookmarks.isEmpty()) {
                cacheManager.loadCachedListBookmarks(selectedList.id)?.takeIf { it.isNotEmpty() }?.let { cached ->
                    if (_uiState.value.selectedList?.id == selectedList.id) {
                        _uiState.update { it.copy(listBookmarks = applyArchiveFilter(cached, archivedFilter).toImmutableList()) }
                    }
                }
            }

            _uiState.update {
                it.copy(
                    isListBookmarksLoading = true,
                    listBookmarksErrorMessage = null
                )
            }

            repository.configure(settings)

            val (listResult, bookmarksResult) = if (selectedList.id == FAVORITES_LIST_ID) {
                ApiResult.Success(selectedList) to repository.getAllFavouritedBookmarks(
                    archived = archivedFilter,
                    limit = 20
                )
            } else {
                coroutineScope {
                    val listDeferred = async { repository.getList(selectedList.id) }
                    val bookmarksDeferred = async {
                        repository.getAllBookmarksByList(
                            archived = archivedFilter,
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
                    val filteredBookmarks = applyArchiveFilter(bookmarksResult.data, archivedFilter)
                    _uiState.update {
                        it.copy(
                            isListBookmarksLoading = false,
                            listBookmarks = filteredBookmarks.toImmutableList(),
                            listBookmarksErrorMessage = null
                        )
                    }
                    launch { cacheManager.saveListBookmarks(selectedList.id, bookmarksResult.data) }
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

    private fun scheduleCacheSave() {
        cacheSaveJob?.cancel()
        cacheSaveJob = viewModelScope.launch {
            delay(CACHE_SAVE_DEBOUNCE_MS)
            val state = _uiState.value
            cacheManager.saveBookmarks(state.bookmarks)
            state.selectedTag?.let { tag ->
                cacheManager.saveTagBookmarks(tag.id, state.tagBookmarks)
            }
            state.selectedList?.let { list ->
                cacheManager.saveListBookmarks(list.id, state.listBookmarks)
            }
        }
    }

    private fun applyBookmarkUpdate(updatedBookmark: BookmarkItem) {
        var newDisplayed: List<BookmarkItem> = _displayedBookmarks.value
        _uiState.update { state ->
            val isFavoritesListOpen = state.selectedList?.id == FAVORITES_LIST_ID

            val bookmarksUpdated = state.bookmarks.map { current ->
                if (current.id == updatedBookmark.id) updatedBookmark else current
            }.let { items ->
                if (updatedBookmark.archived) items.filterNot { it.id == updatedBookmark.id } else items
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

            newDisplayed = computeDisplayedBookmarks(
                bookmarks = bookmarksUpdated,
                query = state.searchQuery,
                isSearchActive = state.isSearchActive
            )

            state.copy(
                bookmarks = bookmarksUpdated.toImmutableList(),
                tagBookmarks = tagBookmarksUpdated.toImmutableList(),
                listBookmarks = listBookmarksUpdated.toImmutableList()
            )
        }
        _displayedBookmarks.value = newDisplayed.toImmutableList()

        scheduleCacheSave()
    }

    private fun applyBookmarkRemoval(bookmarkId: String) {
        var newDisplayed: List<BookmarkItem> = _displayedBookmarks.value
        _uiState.update { state ->
            val bookmarksUpdated = state.bookmarks.filterNot { it.id == bookmarkId }
            val tagBookmarksUpdated = state.tagBookmarks.filterNot { it.id == bookmarkId }
            val listBookmarksUpdated = state.listBookmarks.filterNot { it.id == bookmarkId }

            newDisplayed = computeDisplayedBookmarks(
                bookmarks = bookmarksUpdated,
                query = state.searchQuery,
                isSearchActive = state.isSearchActive
            )

            state.copy(
                bookmarks = bookmarksUpdated.toImmutableList(),
                tagBookmarks = tagBookmarksUpdated.toImmutableList(),
                listBookmarks = listBookmarksUpdated.toImmutableList()
            )
        }
        _displayedBookmarks.value = newDisplayed.toImmutableList()

        scheduleCacheSave()
    }

    private fun applyBookmarkRestore(bookmark: BookmarkItem) {
        var newDisplayed: List<BookmarkItem> = _displayedBookmarks.value
        _uiState.update { state ->
            val bookmarksUpdated = if (state.bookmarks.any { it.id == bookmark.id }) {
                state.bookmarks
            } else {
                listOf(bookmark) + state.bookmarks
            }

            val tagBookmarksUpdated = if (state.selectedTag != null && state.tagBookmarks.none { it.id == bookmark.id }) {
                listOf(bookmark) + state.tagBookmarks
            } else {
                state.tagBookmarks
            }

            val listBookmarksUpdated = if (state.selectedList != null && state.listBookmarks.none { it.id == bookmark.id }) {
                listOf(bookmark) + state.listBookmarks
            } else {
                state.listBookmarks
            }

            newDisplayed = computeDisplayedBookmarks(
                bookmarks = bookmarksUpdated,
                query = state.searchQuery,
                isSearchActive = state.isSearchActive
            )

            state.copy(
                bookmarks = bookmarksUpdated.toImmutableList(),
                tagBookmarks = tagBookmarksUpdated.toImmutableList(),
                listBookmarks = listBookmarksUpdated.toImmutableList()
            )
        }
        _displayedBookmarks.value = newDisplayed.toImmutableList()

        scheduleCacheSave()
    }

    private fun loadSelectedTagContent() {
        val selectedTag = _uiState.value.selectedTag ?: return

        tagDetailJob = viewModelScope.launch {
            val settings = settingsDataStore.settingsFlow.first()
            val archivedFilter = archivedFilterFor(settings)

            if (_uiState.value.tagBookmarks.isEmpty()) {
                cacheManager.loadCachedTagBookmarks(selectedTag.id)?.takeIf { it.isNotEmpty() }?.let { cached ->
                    if (_uiState.value.selectedTag?.id == selectedTag.id) {
                        _uiState.update { it.copy(tagBookmarks = applyArchiveFilter(cached, archivedFilter).toImmutableList()) }
                    }
                }
            }

            _uiState.update {
                it.copy(
                    isTagBookmarksLoading = true,
                    tagBookmarksErrorMessage = null
                )
            }

            repository.configure(settings)

            val (tagResult, bookmarksResult) = coroutineScope {
                val tagDeferred = async { repository.getTag(selectedTag.id) }
                val bookmarksDeferred = async {
                    repository.getAllBookmarksByTag(
                        archived = archivedFilter,
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
                    val filteredBookmarks = applyArchiveFilter(bookmarksResult.data, archivedFilter)
                    _uiState.update {
                        it.copy(
                            isTagBookmarksLoading = false,
                            tagBookmarks = filteredBookmarks.toImmutableList(),
                            tagBookmarksErrorMessage = null
                        )
                    }
                    launch { cacheManager.saveTagBookmarks(selectedTag.id, bookmarksResult.data) }
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

    private fun archivedFilterFor(settings: Settings): Boolean? {
        return when (settings.archiveDisplayBehaviour) {
            ArchiveDisplayBehaviour.HIDE -> false
            ArchiveDisplayBehaviour.SHOW -> null
        }
    }

    private fun applyArchiveFilter(bookmarks: List<BookmarkItem>, archivedFilter: Boolean?): List<BookmarkItem> {
        return when (archivedFilter) {
            false -> bookmarks.filterNot { it.archived }
            true -> bookmarks.filter { it.archived }
            null -> bookmarks
        }
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
