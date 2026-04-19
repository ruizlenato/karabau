package com.ruizlenato.karabau.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Settings(
    val apiKey: String? = null,
    val apiKeyId: String? = null,
    val address: String = "https://cloud.karakeep.app",
    val imageQuality: Float = 0.2f,
    val theme: Theme = Theme.SYSTEM,
    val defaultBookmarkView: BookmarkView = BookmarkView.READER,
    val showNotes: Boolean = false,
    val keepScreenOnWhileReading: Boolean = false,
    val customHeaders: Map<String, String> = emptyMap(),
    val readerFontSize: Int? = null,
    val readerLineHeight: Float? = null,
    val readerFontFamily: String? = null
)

enum class Theme {
    LIGHT, DARK, SYSTEM
}

enum class BookmarkView {
    READER, BROWSER, EXTERNAL_BROWSER
}

fun Settings.isLoggedIn(): Boolean = !apiKey.isNullOrBlank()
