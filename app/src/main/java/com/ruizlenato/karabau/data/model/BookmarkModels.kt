package com.ruizlenato.karabau.data.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

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
    val createdAt: String
)
