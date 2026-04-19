package com.ruizlenato.karabau.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ruizlenato.karabau.data.model.BookmarkView
import com.ruizlenato.karabau.data.model.Settings
import com.ruizlenato.karabau.data.model.Theme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "karabau_settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        private val API_KEY = stringPreferencesKey("api_key")
        private val API_KEY_ID = stringPreferencesKey("api_key_id")
        private val ADDRESS = stringPreferencesKey("address")
        private val IMAGE_QUALITY = floatPreferencesKey("image_quality")
        private val THEME = stringPreferencesKey("theme")
        private val DEFAULT_BOOKMARK_VIEW = stringPreferencesKey("default_bookmark_view")
        private val SHOW_NOTES = booleanPreferencesKey("show_notes")
        private val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on_while_reading")
        private val CUSTOM_HEADERS = stringPreferencesKey("custom_headers")
        private val READER_FONT_SIZE = intPreferencesKey("reader_font_size")
        private val READER_LINE_HEIGHT = floatPreferencesKey("reader_line_height")
        private val READER_FONT_FAMILY = stringPreferencesKey("reader_font_family")
    }

    val settingsFlow: Flow<Settings> = context.dataStore.data.map { preferences ->
        Settings(
            apiKey = preferences[API_KEY],
            apiKeyId = preferences[API_KEY_ID],
            address = preferences[ADDRESS] ?: "https://cloud.karakeep.app",
            imageQuality = preferences[IMAGE_QUALITY] ?: 0.2f,
            theme = preferences[THEME]?.let { Theme.valueOf(it) } ?: Theme.SYSTEM,
            defaultBookmarkView = preferences[DEFAULT_BOOKMARK_VIEW]?.let { BookmarkView.valueOf(it) } ?: BookmarkView.READER,
            showNotes = preferences[SHOW_NOTES] ?: false,
            keepScreenOnWhileReading = preferences[KEEP_SCREEN_ON] ?: false,
            customHeaders = preferences[CUSTOM_HEADERS]?.let { Json.decodeFromString(it) } ?: emptyMap(),
            readerFontSize = preferences[READER_FONT_SIZE],
            readerLineHeight = preferences[READER_LINE_HEIGHT],
            readerFontFamily = preferences[READER_FONT_FAMILY]
        )
    }

    suspend fun updateSettings(settings: Settings) {
        context.dataStore.edit { preferences ->
            preferences[API_KEY] = settings.apiKey ?: ""
            preferences[API_KEY_ID] = settings.apiKeyId ?: ""
            preferences[ADDRESS] = settings.address
            preferences[IMAGE_QUALITY] = settings.imageQuality
            preferences[THEME] = settings.theme.name
            preferences[DEFAULT_BOOKMARK_VIEW] = settings.defaultBookmarkView.name
            preferences[SHOW_NOTES] = settings.showNotes
            preferences[KEEP_SCREEN_ON] = settings.keepScreenOnWhileReading
            preferences[CUSTOM_HEADERS] = Json.encodeToString(settings.customHeaders)
            settings.readerFontSize?.let { preferences[READER_FONT_SIZE] = it }
            settings.readerLineHeight?.let { preferences[READER_LINE_HEIGHT] = it }
            settings.readerFontFamily?.let { preferences[READER_FONT_FAMILY] = it }
        }
    }

    suspend fun clearAuth() {
        context.dataStore.edit { preferences ->
            preferences.remove(API_KEY)
            preferences.remove(API_KEY_ID)
        }
    }

    suspend fun setApiKey(apiKey: String, apiKeyId: String? = null) {
        context.dataStore.edit { preferences ->
            preferences[API_KEY] = apiKey
            apiKeyId?.let { preferences[API_KEY_ID] = it }
        }
    }

    suspend fun setServerAddress(address: String) {
        context.dataStore.edit { preferences ->
            preferences[ADDRESS] = address
        }
    }
}
