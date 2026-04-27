package com.ruizlenato.karabau.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.ruizlenato.karabau.data.local.SettingsDataStore
import com.ruizlenato.karabau.data.remote.ApiResult
import com.ruizlenato.karabau.data.remote.KarabauRepository
import kotlinx.coroutines.flow.first

class CreateBookmarkViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsDataStore = SettingsDataStore(application)
    private val repository = KarabauRepository()

    suspend fun submitBookmark(
        url: String,
        title: String,
        note: String
    ): ApiResult<Unit> {
        val settings = settingsDataStore.settingsFlow.first()
        repository.configure(settings)
        return repository.createLinkBookmark(
            url = url.trim(),
            title = title.trim().takeIf { it.isNotEmpty() },
            note = note.trim().takeIf { it.isNotEmpty() }
        )
    }
}
