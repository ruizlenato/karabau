package com.ruizlenato.karabau.data.remote

import com.ruizlenato.karabau.data.model.ExchangeKeyRequest
import com.ruizlenato.karabau.data.model.ExchangeKeyResponse
import com.ruizlenato.karabau.data.model.BookmarkItem
import com.ruizlenato.karabau.data.model.BookmarkCursor
import com.ruizlenato.karabau.data.model.GetBookmarksResponse
import com.ruizlenato.karabau.data.model.GetBookmarksRequest
import com.ruizlenato.karabau.data.model.RevokeKeyRequest
import com.ruizlenato.karabau.data.model.Settings
import com.ruizlenato.karabau.data.model.TagDetails
import com.ruizlenato.karabau.data.model.TagItem
import com.ruizlenato.karabau.data.model.ValidateKeyRequest
import com.ruizlenato.karabau.data.model.ValidateKeyResponse
import com.ruizlenato.karabau.data.model.WhoAmIResponse
import com.ruizlenato.karabau.data.model.isLoggedIn
import org.json.JSONObject
import retrofit2.HttpException
import java.io.IOException
import java.net.URI
import java.util.UUID

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val code: String, val message: String) : ApiResult<Nothing>()
    data class NetworkError(val message: String) : ApiResult<Nothing>()
}

class KarabauRepository(
    private val settings: Settings
) {
    private val apiService: KarabauApiService by lazy {
        RetrofitClient.create(settings.address, settings.customHeaders)
    }

    suspend fun exchangeKey(email: String, password: String): ApiResult<ExchangeKeyResponse> {
        return try {
            val randStr = UUID.randomUUID().toString().take(8)
            val request = ExchangeKeyRequest(
                email = email.trim(),
                password = password,
                keyName = "Mobile App: ($randStr)"
            )
            val response = apiService.exchangeKey(TrpcInput(json = request))

            if (response.isSuccessful) {
                val body = response.body()
                val data = body?.result?.data?.json
                if (data != null) {
                    ApiResult.Success(data)
                } else {
                    ApiResult.Error("UNKNOWN", "Empty response")
                }
            } else {
                val errorBody = response.errorBody()?.string().orEmpty()
                val errorCode = when (response.code()) {
                    401 -> "UNAUTHORIZED"
                    403 -> "FORBIDDEN"
                    429 -> "RATE_LIMIT"
                    else -> "UNKNOWN"
                }
                ApiResult.Error(errorCode, errorBody.ifBlank { "Request failed: ${response.code()}" })
            }
        } catch (e: HttpException) {
            ApiResult.Error("HTTP_ERROR", e.message ?: "HTTP error")
        } catch (e: IOException) {
            ApiResult.NetworkError(e.message ?: "Network error")
        } catch (e: Exception) {
            ApiResult.Error("EXCEPTION", e.message ?: "Unknown error")
        }
    }

    suspend fun validateKey(apiKey: String): ApiResult<ValidateKeyResponse> {
        return try {
            val request = ValidateKeyRequest(apiKey = apiKey)
            val response = apiService.validateKey(TrpcInput(json = request))

            if (response.isSuccessful) {
                val body = response.body()
                val data = body?.result?.data?.json
                if (data != null) {
                    ApiResult.Success(data)
                } else {
                    ApiResult.Error("UNKNOWN", "Empty response")
                }
            } else {
                val errorBody = response.errorBody()?.string().orEmpty()
                val errorCode = when (response.code()) {
                    401 -> "UNAUTHORIZED"
                    429 -> "RATE_LIMIT"
                    else -> "UNKNOWN"
                }
                ApiResult.Error(errorCode, errorBody.ifBlank { "Request failed: ${response.code()}" })
            }
        } catch (e: HttpException) {
            ApiResult.Error("HTTP_ERROR", e.message ?: "HTTP error")
        } catch (e: IOException) {
            ApiResult.NetworkError(e.message ?: "Network error")
        } catch (e: Exception) {
            ApiResult.Error("EXCEPTION", e.message ?: "Unknown error")
        }
    }

    suspend fun healthCheck(): ApiResult<Boolean> {
        return try {
            val response = apiService.healthCheck()
            if (response.isSuccessful) {
                ApiResult.Success(true)
            } else {
                ApiResult.Error("FAILED", "Health check failed")
            }
        } catch (e: IOException) {
            ApiResult.NetworkError("Cannot connect to server")
        } catch (e: Exception) {
            ApiResult.Error("EXCEPTION", e.message ?: "Unknown error")
        }
    }

    suspend fun getBookmarks(
        archived: Boolean? = false,
        tagId: String? = null,
        cursor: BookmarkCursor? = null,
        limit: Int = 20
    ): ApiResult<List<BookmarkItem>> {
        if (!settings.isLoggedIn()) {
            return ApiResult.Error("NOT_LOGGED_IN", "Not logged in")
        }

        return try {
            val auth = "Bearer ${settings.apiKey}"
            val request = GetBookmarksRequest(
                archived = archived,
                includeContent = false,
                useCursorV2 = true,
                tagId = tagId,
                cursor = cursor,
                limit = limit
            )

            val inputJson = JSONObject().apply {
                put("0", JSONObject().apply {
                    put("json", JSONObject().apply {
                        request.archived?.let { put("archived", it) }
                        put("includeContent", request.includeContent)
                        put("useCursorV2", request.useCursorV2)
                        put("limit", request.limit)
                        request.tagId?.takeIf { it.isNotBlank() }?.let {
                            put("tagId", it)
                        }
                        request.cursor?.let {
                            put("cursor", JSONObject().apply {
                                put("createdAt", it.createdAt)
                                put("id", it.id)
                            })
                        }
                    })
                })
            }.toString()

            val response = apiService.getBookmarks(
                auth = auth,
                batch = "1",
                input = inputJson
            )

            if (response.isSuccessful) {
                val rawBody = response.body()?.string().orEmpty()
                val bookmarksPage = parseBookmarksPageFromBatchResponse(rawBody)
                if (bookmarksPage != null) {
                    ApiResult.Success(bookmarksPage.bookmarks)
                } else {
                    ApiResult.Error("UNKNOWN", "Empty response")
                }
            } else {
                val errorBody = response.errorBody()?.string().orEmpty()
                ApiResult.Error("FAILED", errorBody.ifBlank { "Failed to load bookmarks" })
            }
        } catch (e: IOException) {
            ApiResult.NetworkError(e.message ?: "Network error")
        } catch (e: Exception) {
            ApiResult.Error("EXCEPTION", e.message ?: "Unknown error")
        }
    }

    suspend fun getAllBookmarksByTag(
        tagId: String,
        archived: Boolean? = null,
        limit: Int = 20
    ): ApiResult<List<BookmarkItem>> {
        if (!settings.isLoggedIn()) {
            return ApiResult.Error("NOT_LOGGED_IN", "Not logged in")
        }

        return try {
            val allItems = mutableListOf<BookmarkItem>()
            var cursor: BookmarkCursor? = null

            do {
                val pageResult = getBookmarksPage(
                    archived = archived,
                    tagId = tagId,
                    cursor = cursor,
                    limit = limit
                )

                when (pageResult) {
                    is ApiResult.Success -> {
                        allItems += pageResult.data.bookmarks
                        cursor = pageResult.data.nextCursor
                    }

                    is ApiResult.Error -> return pageResult
                    is ApiResult.NetworkError -> return pageResult
                }
            } while (cursor != null)

            ApiResult.Success(allItems)
        } catch (e: IOException) {
            ApiResult.NetworkError(e.message ?: "Network error")
        } catch (e: Exception) {
            ApiResult.Error("EXCEPTION", e.message ?: "Unknown error")
        }
    }

    suspend fun whoAmI(): ApiResult<WhoAmIResponse> {
        if (!settings.isLoggedIn()) {
            return ApiResult.Error("NOT_LOGGED_IN", "Not logged in")
        }

        return try {
            val auth = "Bearer ${settings.apiKey}"
            val inputJson = JSONObject().apply {
                put("0", JSONObject().apply {
                    put("json", JSONObject())
                })
            }.toString()

            val response = apiService.whoAmI(
                auth = auth,
                batch = "1",
                input = inputJson
            )

            if (response.isSuccessful) {
                val rawBody = response.body()?.string().orEmpty()
                val user = parseWhoAmIFromBatchResponse(rawBody)
                if (user != null) {
                    ApiResult.Success(user)
                } else {
                    ApiResult.Error("UNKNOWN", "Empty response")
                }
            } else {
                val errorBody = response.errorBody()?.string().orEmpty()
                ApiResult.Error("FAILED", errorBody.ifBlank { "Failed to load profile" })
            }
        } catch (e: IOException) {
            ApiResult.NetworkError(e.message ?: "Network error")
        } catch (e: Exception) {
            ApiResult.Error("EXCEPTION", e.message ?: "Unknown error")
        }
    }

    suspend fun getTags(
        nameContains: String? = null,
        limit: Int = 50,
        sortBy: String = if (nameContains.isNullOrBlank()) "usage" else "relevance",
        page: Int = 0
    ): ApiResult<List<TagItem>> {
        if (!settings.isLoggedIn()) {
            return ApiResult.Error("NOT_LOGGED_IN", "Not logged in")
        }

        return try {
            val auth = "Bearer ${settings.apiKey}"
            val inputJson = JSONObject().apply {
                put("0", JSONObject().apply {
                    put("json", JSONObject().apply {
                        put("limit", limit)
                        put("sortBy", sortBy)
                        put("cursor", JSONObject().apply {
                            put("page", page)
                        })
                        nameContains?.takeIf { it.isNotBlank() }?.let {
                            put("nameContains", it)
                        }
                    })
                })
            }.toString()

            val response = apiService.listTags(
                auth = auth,
                batch = "1",
                input = inputJson
            )

            if (response.isSuccessful) {
                val rawBody = response.body()?.string().orEmpty()
                val tags = parseTagsListFromBatchResponse(rawBody)
                if (tags != null) {
                    ApiResult.Success(tags)
                } else {
                    ApiResult.Error("UNKNOWN", "Empty response")
                }
            } else {
                val errorBody = response.errorBody()?.string().orEmpty()
                ApiResult.Error("FAILED", errorBody.ifBlank { "Failed to load tags" })
            }
        } catch (e: IOException) {
            ApiResult.NetworkError(e.message ?: "Network error")
        } catch (e: Exception) {
            ApiResult.Error("EXCEPTION", e.message ?: "Unknown error")
        }
    }

    suspend fun getTag(tagId: String): ApiResult<TagDetails> {
        if (!settings.isLoggedIn()) {
            return ApiResult.Error("NOT_LOGGED_IN", "Not logged in")
        }

        return try {
            val auth = "Bearer ${settings.apiKey}"
            val inputJson = JSONObject().apply {
                put("0", JSONObject().apply {
                    put("json", JSONObject().apply {
                        put("tagId", tagId)
                    })
                })
            }.toString()

            val response = apiService.getTag(
                auth = auth,
                batch = "1",
                input = inputJson
            )

            if (response.isSuccessful) {
                val rawBody = response.body()?.string().orEmpty()
                val tag = parseTagDetailsFromBatchResponse(rawBody)
                if (tag != null) {
                    ApiResult.Success(tag)
                } else {
                    ApiResult.Error("UNKNOWN", "Empty response")
                }
            } else {
                val errorBody = response.errorBody()?.string().orEmpty()
                ApiResult.Error("FAILED", errorBody.ifBlank { "Failed to load tag" })
            }
        } catch (e: IOException) {
            ApiResult.NetworkError(e.message ?: "Network error")
        } catch (e: Exception) {
            ApiResult.Error("EXCEPTION", e.message ?: "Unknown error")
        }
    }

    private suspend fun getBookmarksPage(
        archived: Boolean?,
        tagId: String?,
        cursor: BookmarkCursor?,
        limit: Int
    ): ApiResult<GetBookmarksResponse> {
        val auth = "Bearer ${settings.apiKey}"
        val request = GetBookmarksRequest(
            archived = archived,
            includeContent = false,
            useCursorV2 = true,
            tagId = tagId,
            cursor = cursor,
            limit = limit
        )

        val inputJson = JSONObject().apply {
            put("0", JSONObject().apply {
                put("json", JSONObject().apply {
                    request.archived?.let { put("archived", it) }
                    put("includeContent", request.includeContent)
                    put("useCursorV2", request.useCursorV2)
                    put("limit", request.limit)
                    request.tagId?.takeIf { it.isNotBlank() }?.let {
                        put("tagId", it)
                    }
                    request.cursor?.let {
                        put("cursor", JSONObject().apply {
                            put("createdAt", it.createdAt)
                            put("id", it.id)
                        })
                    }
                })
            })
        }.toString()

        val response = apiService.getBookmarks(
            auth = auth,
            batch = "1",
            input = inputJson
        )

        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string().orEmpty()
            return ApiResult.Error("FAILED", errorBody.ifBlank { "Failed to load bookmarks" })
        }

        val rawBody = response.body()?.string().orEmpty()
        val bookmarksPage = parseBookmarksPageFromBatchResponse(rawBody)
        return if (bookmarksPage != null) {
            ApiResult.Success(bookmarksPage)
        } else {
            ApiResult.Error("UNKNOWN", "Empty response")
        }
    }

    private fun parseBookmarksPageFromBatchResponse(raw: String): GetBookmarksResponse? {
        if (raw.isBlank()) return null

        val json = parseBatchJson(raw) ?: return null
        val bookmarksArray = json.optJSONArray("bookmarks")
            ?: return GetBookmarksResponse(bookmarks = emptyList(), nextCursor = null)

        val items = mutableListOf<BookmarkItem>()
        for (i in 0 until bookmarksArray.length()) {
            val obj = bookmarksArray.optJSONObject(i) ?: continue
            val content = obj.optJSONObject("content")
            val imageUrl = content?.optStringOrNull("imageUrl")
            val linkUrl = content?.optStringOrNull("url")
                ?: content?.optStringOrNull("sourceUrl")
            val subtitle = content?.optStringOrNull("publisher")
                ?: content?.optStringOrNull("author")
                ?: domainFromUrl(linkUrl)
            val tags = parseTags(obj.optJSONArray("tags"))

            items += BookmarkItem(
                id = obj.optString("id"),
                title = if (obj.isNull("title")) null else obj.optString("title"),
                tags = tags,
                imageUrl = imageUrl,
                subtitle = subtitle,
                linkUrl = linkUrl,
                archived = obj.optBoolean("archived", false),
                favourited = obj.optBoolean("favourited", false),
                createdAt = obj.optString("createdAt", ""),
                content = content?.let { contentObj ->
                    com.google.gson.JsonParser.parseString(contentObj.toString()).asJsonObject
                }
            )
        }

        val nextCursorObject = json.optJSONObject("nextCursor")
        val nextCursor = nextCursorObject?.let {
            val id = it.optStringOrNull("id")
            val createdAt = it.optStringOrNull("createdAt")
            if (id != null && createdAt != null) {
                BookmarkCursor(createdAt = createdAt, id = id)
            } else {
                null
            }
        }

        return GetBookmarksResponse(
            bookmarks = items,
            nextCursor = nextCursor
        )
    }

    private fun parseWhoAmIFromBatchResponse(raw: String): WhoAmIResponse? {
        if (raw.isBlank()) return null

        val json = parseBatchJson(raw) ?: return null

        return WhoAmIResponse(
            id = json.optString("id"),
            name = json.optStringOrNull("name"),
            email = json.optStringOrNull("email"),
            image = json.optStringOrNull("image")
        )
    }

    private fun parseTagsListFromBatchResponse(raw: String): List<TagItem>? {
        if (raw.isBlank()) return null

        val json = parseBatchJson(raw) ?: return null
        val tagsArray = json.optJSONArray("tags") ?: return emptyList()

        val tags = mutableListOf<TagItem>()
        for (i in 0 until tagsArray.length()) {
            val obj = tagsArray.optJSONObject(i) ?: continue
            val id = obj.optStringOrNull("id") ?: continue
            val name = obj.optStringOrNull("name") ?: continue
            val numBookmarks = obj.optInt("numBookmarks", 0)
            tags += TagItem(
                id = id,
                name = name,
                numBookmarks = numBookmarks
            )
        }

        return tags
    }

    private fun parseTagDetailsFromBatchResponse(raw: String): TagDetails? {
        if (raw.isBlank()) return null

        val json = parseBatchJson(raw) ?: return null
        val id = json.optStringOrNull("id") ?: return null
        val name = json.optStringOrNull("name") ?: return null
        val numBookmarks = json.optInt("numBookmarks", 0)

        return TagDetails(
            id = id,
            name = name,
            numBookmarks = numBookmarks
        )
    }

    private fun parseBatchJson(raw: String): JSONObject? {
        val rootArray = runCatching { org.json.JSONArray(raw) }.getOrNull() ?: return null
        if (rootArray.length() == 0) return null

        return rootArray
            .optJSONObject(0)
            ?.optJSONObject("result")
            ?.optJSONObject("data")
            ?.optJSONObject("json")
    }

    private fun JSONObject.optStringOrNull(name: String): String? {
        val element = get(name) ?: return null
        if (element == JSONObject.NULL) return null
        val value = element.toString().trim()
        return value.takeIf { it.isNotBlank() }
    }

    private fun domainFromUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return runCatching { URI(url).host?.removePrefix("www.") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    private fun parseTags(tagsArray: org.json.JSONArray?): List<String> {
        if (tagsArray == null || tagsArray.length() == 0) return emptyList()

        val tags = mutableListOf<String>()
        for (i in 0 until tagsArray.length()) {
            val item = tagsArray.opt(i) ?: continue
            when (item) {
                is String -> item.trim().takeIf { it.isNotBlank() }?.let(tags::add)
                is JSONObject -> item.optStringOrNull("name")?.let(tags::add)
            }
        }
        return tags
    }

    suspend fun revokeKey(apiKeyId: String): ApiResult<Unit> {
        if (!settings.isLoggedIn()) {
            return ApiResult.Error("NOT_LOGGED_IN", "Not logged in")
        }

        return try {
            val auth = "Bearer ${settings.apiKey}"
            val request = RevokeKeyRequest(id = apiKeyId)
            val response = apiService.revokeKey(auth, TrpcInput(json = request))

            if (response.isSuccessful) {
                ApiResult.Success(Unit)
            } else {
                ApiResult.Error("FAILED", "Failed to revoke key")
            }
        } catch (e: Exception) {
            ApiResult.Error("EXCEPTION", e.message ?: "Unknown error")
        }
    }
}
