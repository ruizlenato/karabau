package com.ruizlenato.karabau.data.model

import com.google.gson.JsonObject

data class GetBookmarksRequest(
    val archived: Boolean = false,
    val includeContent: Boolean = false,
    val useCursorV2: Boolean = true,
    val limit: Int = 20,
    val cursor: BookmarkCursor? = null
)

data class BookmarkCursor(
    val createdAt: String,
    val id: String
)

data class GetBookmarksResponse(
    val bookmarks: List<BookmarkItem> = emptyList(),
    val nextCursor: BookmarkCursor? = null
)

data class BookmarkItem(
    val id: String,
    val title: String? = null,
    val tags: List<String> = emptyList(),
    val imageUrl: String? = null,
    val subtitle: String? = null,
    val linkUrl: String? = null,
    val archived: Boolean = false,
    val favourited: Boolean = false,
    val createdAt: String,
    val content: JsonObject? = null
)
