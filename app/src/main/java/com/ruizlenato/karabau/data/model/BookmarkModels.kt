package com.ruizlenato.karabau.data.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.json.JSONObject

data class GetBookmarksRequest(
    val archived: Boolean? = false,
    val includeContent: Boolean = false,
    val useCursorV2: Boolean = true,
    val limit: Int = 20,
    val tagId: String? = null,
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
    val tags: ImmutableList<String> = persistentListOf(),
    val imageUrl: String? = null,
    val subtitle: String? = null,
    val linkUrl: String? = null,
    val archived: Boolean = false,
    val favourited: Boolean = false,
    val createdAt: String,
    val content: JSONObject? = null
)
