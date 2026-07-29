package com.ruizlenato.karabau.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ruizlenato.karabau.data.local.LocalCacheManager
import com.ruizlenato.karabau.data.local.SettingsDataStore
import com.ruizlenato.karabau.data.model.TagItem
import com.ruizlenato.karabau.data.remote.ApiResult
import com.ruizlenato.karabau.data.remote.KarabauRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CreateBookmarkViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsDataStore = SettingsDataStore(application)
    private val repository = KarabauRepository()
    private val cacheManager = LocalCacheManager(application)

    private val _tags = MutableStateFlow<ImmutableList<TagItem>>(persistentListOf())
    val tags: StateFlow<ImmutableList<TagItem>> = _tags.asStateFlow()

    private var hasLoadedTags = false

    fun loadTags() {
        if (hasLoadedTags && _tags.value.isNotEmpty()) return
        viewModelScope.launch {
            if (_tags.value.isEmpty()) {
                val cached = cacheManager.loadCachedTags()
                if (!cached.isNullOrEmpty()) {
                    _tags.value = cached.toImmutableList()
                }
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
                    _tags.value = result.data.toImmutableList()
                    launch { cacheManager.saveTags(result.data) }
                }

                is ApiResult.Error -> Unit
                is ApiResult.NetworkError -> Unit
            }
        }
    }

    suspend fun submitBookmark(
        url: String,
        title: String,
        note: String,
        tags: List<String> = emptyList()
    ): ApiResult<Unit> {
        val settings = settingsDataStore.settingsFlow.first()
        repository.configure(settings)
        return repository.createLinkBookmark(
            url = url.trim(),
            title = title.trim().takeIf { it.isNotEmpty() },
            note = note.trim().takeIf { it.isNotEmpty() },
            tags = tags
        )
    }
}
