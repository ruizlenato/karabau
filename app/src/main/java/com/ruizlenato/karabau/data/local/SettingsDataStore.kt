package com.ruizlenato.karabau.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ruizlenato.karabau.data.model.BookmarkView
import com.ruizlenato.karabau.data.model.DEFAULT_SERVER_ADDRESS
import com.ruizlenato.karabau.data.model.Settings
import com.ruizlenato.karabau.data.model.Theme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "karabau_settings")

class SettingsDataStore(private val context: Context) {

    companion object {
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

        private const val ENCRYPTED_PREFS_NAME = "karabau_secure"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_API_KEY_ID = "api_key_id"
    }

    private val encryptedPrefs: SharedPreferences? by lazy {
        getOrCreateEncryptedPrefs()
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun getOrCreateEncryptedPrefs(): SharedPreferences? {
        return runCatching { createEncryptedPrefs() }
            .recoverCatching {
                context.deleteSharedPreferences(ENCRYPTED_PREFS_NAME)
                createEncryptedPrefs()
            }
            .getOrNull()
    }

    private fun getEncryptedApiKey(): String? =
        runCatching { encryptedPrefs?.getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() } }
            .getOrNull()

    private fun getEncryptedApiKeyId(): String? =
        runCatching { encryptedPrefs?.getString(KEY_API_KEY_ID, null)?.takeIf { it.isNotBlank() } }
            .getOrNull()

    val settingsFlow: Flow<Settings> = context.dataStore.data.map { preferences ->
        val apiKey = getEncryptedApiKey()
            ?: preferences[stringPreferencesKey("api_key")]?.takeIf { it.isNotBlank() }
        val apiKeyId = getEncryptedApiKeyId()
            ?: preferences[stringPreferencesKey("api_key_id")]?.takeIf { it.isNotBlank() }

        Settings(
            apiKey = apiKey,
            apiKeyId = apiKeyId,
            address = preferences[ADDRESS] ?: DEFAULT_SERVER_ADDRESS,
            imageQuality = preferences[IMAGE_QUALITY] ?: 0.2f,
            theme = runCatching { preferences[THEME]?.let { Theme.valueOf(it) } }.getOrNull() ?: Theme.SYSTEM,
            defaultBookmarkView = runCatching { preferences[DEFAULT_BOOKMARK_VIEW]?.let { BookmarkView.valueOf(it) } }.getOrNull() ?: BookmarkView.READER,
            showNotes = preferences[SHOW_NOTES] ?: false,
            keepScreenOnWhileReading = preferences[KEEP_SCREEN_ON] ?: false,
            customHeaders = runCatching { preferences[CUSTOM_HEADERS]?.let { Json.decodeFromString<Map<String, String>>(it) } }.getOrNull() ?: emptyMap(),
            readerFontSize = preferences[READER_FONT_SIZE],
            readerLineHeight = preferences[READER_LINE_HEIGHT],
            readerFontFamily = preferences[READER_FONT_FAMILY]
        )
    }

    suspend fun updateSettings(settings: Settings) {
        val securePrefs = encryptedPrefs

        securePrefs?.edit()
            ?.putString(KEY_API_KEY, settings.apiKey ?: "")
            ?.putString(KEY_API_KEY_ID, settings.apiKeyId ?: "")
            ?.apply()

        context.dataStore.edit { preferences ->
            if (securePrefs == null) {
                settings.apiKey?.let { preferences[stringPreferencesKey("api_key")] = it }
                    ?: preferences.remove(stringPreferencesKey("api_key"))
                settings.apiKeyId?.let { preferences[stringPreferencesKey("api_key_id")] = it }
                    ?: preferences.remove(stringPreferencesKey("api_key_id"))
            } else {
                preferences.remove(stringPreferencesKey("api_key"))
                preferences.remove(stringPreferencesKey("api_key_id"))
            }
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
        encryptedPrefs?.edit()
            ?.remove(KEY_API_KEY)
            ?.remove(KEY_API_KEY_ID)
            ?.apply()

        context.dataStore.edit { preferences ->
            preferences.remove(stringPreferencesKey("api_key"))
            preferences.remove(stringPreferencesKey("api_key_id"))
        }
    }

    suspend fun setApiKey(apiKey: String, apiKeyId: String? = null) {
        val securePrefs = encryptedPrefs

        securePrefs?.edit()
            ?.putString(KEY_API_KEY, apiKey)
            ?.apply {
                apiKeyId?.let { putString(KEY_API_KEY_ID, it) }
            }
            ?.apply()

        if (securePrefs == null) {
            context.dataStore.edit { preferences ->
                preferences[stringPreferencesKey("api_key")] = apiKey
                apiKeyId?.let { preferences[stringPreferencesKey("api_key_id")] = it }
                    ?: preferences.remove(stringPreferencesKey("api_key_id"))
            }
        } else {
            context.dataStore.edit { preferences ->
                preferences.remove(stringPreferencesKey("api_key"))
                preferences.remove(stringPreferencesKey("api_key_id"))
            }
        }
    }

    suspend fun setServerAddress(address: String) {
        context.dataStore.edit { preferences ->
            preferences[ADDRESS] = address
        }
    }
}
