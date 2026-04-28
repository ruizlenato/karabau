package com.ruizlenato.karabau.data.local

import android.content.Context
import com.google.gson.Gson
import com.ruizlenato.karabau.data.model.BookmarkItem
import com.ruizlenato.karabau.data.model.TagItem
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class LocalCacheManager(context: Context) {

    private val cacheDir = File(context.cacheDir, "data_cache").also { it.mkdirs() }
    private val bookmarksFile = File(cacheDir, "bookmarks.json")
    private val tagsFile = File(cacheDir, "tags.json")
    private val profileFile = File(cacheDir, "profile.json")

    private val mutex = Mutex()
    private val gson = Gson()

    suspend fun loadCachedBookmarks(): List<BookmarkItem>? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!bookmarksFile.exists()) return@withLock null
            runCatching {
                val json = bookmarksFile.readText()
                gson.fromJson(json, Array<CachedBookmarkItem>::class.java)
                    ?.map { it.toDomain() }
            }.getOrNull()
        }
    }

    suspend fun saveBookmarks(bookmarks: List<BookmarkItem>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val tmp = File(bookmarksFile.parentFile, "${bookmarksFile.name}.tmp")
                val cacheItems = bookmarks.map { CachedBookmarkItem.fromDomain(it) }
                tmp.writeText(gson.toJson(cacheItems))
                tmp.renameTo(bookmarksFile)
            }
        }
    }

    suspend fun loadCachedTags(): List<TagItem>? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!tagsFile.exists()) return@withLock null
            runCatching {
                val json = tagsFile.readText()
                gson.fromJson(json, Array<TagItem>::class.java)?.toList()
            }.getOrNull()
        }
    }

    suspend fun saveTags(tags: List<TagItem>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val tmp = File(tagsFile.parentFile, "${tagsFile.name}.tmp")
                tmp.writeText(gson.toJson(tags))
                tmp.renameTo(tagsFile)
            }
        }
    }

    suspend fun loadCachedTagBookmarks(tagId: String): List<BookmarkItem>? = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadCachedBookmarksFromFile(fileForKey("tag", tagId))
        }
    }

    suspend fun saveTagBookmarks(tagId: String, bookmarks: List<BookmarkItem>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            saveBookmarksToFile(fileForKey("tag", tagId), bookmarks)
        }
    }

    suspend fun loadCachedListBookmarks(listId: String): List<BookmarkItem>? = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadCachedBookmarksFromFile(fileForKey("list", listId))
        }
    }

    suspend fun saveListBookmarks(listId: String, bookmarks: List<BookmarkItem>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            saveBookmarksToFile(fileForKey("list", listId), bookmarks)
        }
    }

    suspend fun loadCachedProfile(cacheKey: String): CachedProfileSummary? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!profileFile.exists()) return@withLock null
            runCatching {
                val json = profileFile.readText()
                gson.fromJson(json, CachedProfileSummary::class.java)
            }.getOrNull()?.takeIf { it.cacheKey == cacheKey }
        }
    }

    suspend fun saveProfile(
        cacheKey: String,
        profileName: String?,
        profileImage: String?
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val tmp = File(profileFile.parentFile, "${profileFile.name}.tmp")
                val payload = CachedProfileSummary(
                    cacheKey = cacheKey,
                    profileName = profileName,
                    profileImage = profileImage
                )
                tmp.writeText(gson.toJson(payload))
                tmp.renameTo(profileFile)
            }
        }
    }

    private fun fileForKey(prefix: String, key: String): File {
        val safeKey = key.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(cacheDir, "${prefix}_$safeKey.json")
    }

    private fun loadCachedBookmarksFromFile(file: File): List<BookmarkItem>? {
        if (!file.exists()) return null
        return runCatching {
            val json = file.readText()
            gson.fromJson(json, Array<CachedBookmarkItem>::class.java)
                ?.map { it.toDomain() }
        }.getOrNull()
    }

    private fun saveBookmarksToFile(file: File, bookmarks: List<BookmarkItem>) {
        runCatching {
            val tmp = File(file.parentFile, "${file.name}.tmp")
            val cacheItems = bookmarks.map { CachedBookmarkItem.fromDomain(it) }
            tmp.writeText(gson.toJson(cacheItems))
            tmp.renameTo(file)
        }
    }
}

data class CachedProfileSummary(
    val cacheKey: String,
    val profileName: String? = null,
    val profileImage: String? = null
)

private data class CachedBookmarkItem(
    val id: String,
    val title: String? = null,
    val tags: List<String> = emptyList(),
    val imageUrl: String? = null,
    val subtitle: String? = null,
    val linkUrl: String? = null,
    val archived: Boolean = false,
    val favourited: Boolean = false,
    val createdAt: String
) {
    fun toDomain(): BookmarkItem {
        return BookmarkItem(
            id = id,
            title = title,
            tags = tags.toImmutableList(),
            imageUrl = imageUrl,
            subtitle = subtitle,
            linkUrl = linkUrl,
            archived = archived,
            favourited = favourited,
            createdAt = createdAt
        )
    }

    companion object {
        fun fromDomain(item: BookmarkItem): CachedBookmarkItem {
            return CachedBookmarkItem(
                id = item.id,
                title = item.title,
                tags = item.tags.toList(),
                imageUrl = item.imageUrl,
                subtitle = item.subtitle,
                linkUrl = item.linkUrl,
                archived = item.archived,
                favourited = item.favourited,
                createdAt = item.createdAt
            )
        }
    }
}
