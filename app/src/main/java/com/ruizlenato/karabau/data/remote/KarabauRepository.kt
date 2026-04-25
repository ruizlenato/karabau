package com.ruizlenato.karabau.data.remote

import com.ruizlenato.karabau.data.model.ExchangeKeyRequest
import com.ruizlenato.karabau.data.model.ExchangeKeyResponse
import com.ruizlenato.karabau.data.model.BookmarkItem
import com.ruizlenato.karabau.data.model.BookmarkCursor
import com.ruizlenato.karabau.data.model.CreateBookmarkRequest
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val code: String, val message: String) : ApiResult<Nothing>()
    data class NetworkError(val message: String) : ApiResult<Nothing>()
}

class KarabauRepository {

    private val _settingsFlow = MutableStateFlow<Settings?>(null)
    val settingsFlow: StateFlow<Settings?> = _settingsFlow.asStateFlow()

    private val _apiServiceCache = ConcurrentHashMap<String, KarabauApiService>()

    private val currentSettings: Settings?
        get() = _settingsFlow.value

    private val apiService: KarabauApiService
        get() {
            val settings = currentSettings
                ?: throw IllegalStateException("KarabauRepository not configured. Call configure() first.")
            return _apiServiceCache.getOrPut(settings.address) {
                RetrofitClient.getOrCreate(settings.address, settings.customHeaders)
            }
        }

    fun configure(settings: Settings) {
        _settingsFlow.value = settings
    }

    fun isConfigured(): Boolean = currentSettings != null

    private fun requireSettings(): Settings =
        currentSettings ?: throw IllegalStateException("KarabauRepository not configured. Call configure() first.")

    private fun requireLoggedIn(): Settings {
        val settings = requireSettings()
        check(settings.isLoggedIn()) { "Not logged in" }
        return settings
    }

    // ──────────────────────────────────────────────
    //  Generic API call wrappers (eliminate duplication)
    // ──────────────────────────────────────────────

    /**
     * Wraps a suspend API call with unified error handling.
     * Every public method delegates its try/catch to this.
     */
    private inline fun <T> safeApiCall(
        defaultErrorCode: String = "FAILED",
        defaultErrorMessage: String = "Request failed",
        block: () -> ApiResult<T>
    ): ApiResult<T> {
        return try {
            block()
        } catch (e: HttpException) {
            ApiResult.Error("HTTP_ERROR", e.message ?: "HTTP error")
        } catch (e: IOException) {
            ApiResult.NetworkError(e.message ?: "Network error")
        } catch (e: Exception) {
            ApiResult.Error("EXCEPTION", e.message ?: "Unknown error")
        }
    }

    /**
     * Handles a Retrofit Response<ResponseBody> from a tRPC batch GET endpoint.
     * Parses the raw body using the provided mapper, or returns a structured error.
     */
    private inline fun <T> handleBatchGetResponse(
        response: Response<ResponseBody>,
        errorContext: String,
        mapper: (JSONObject) -> T?
    ): ApiResult<T> {
        if (response.isSuccessful) {
            val rawBody = response.body()?.string().orEmpty()
            val json = parseBatchJson(rawBody)
                ?: return ApiResult.Error("UNKNOWN", "Empty response")
            val result = mapper(json)
                ?: return ApiResult.Error("UNKNOWN", "Empty response")
            return ApiResult.Success(result)
        } else {
            val errorBody = response.errorBody()?.string().orEmpty()
            return ApiResult.Error(
                "FAILED",
                errorBody.ifBlank { "Failed to $errorContext" }
            )
        }
    }

    /**
     * Handles a Retrofit Response from a tRPC POST endpoint that returns
     * a typed TrpcResponse<T> body (exchangeKey, validateKey, revokeKey).
     */
    private inline fun <T> handleTrpcPostResponse(
        response: Response<TrpcResponse<T>>,
        errorMapCode: (Int) -> String = { "UNKNOWN" }
    ): ApiResult<T> {
        if (response.isSuccessful) {
            val data = response.body()?.result?.data?.json
            return if (data != null) {
                ApiResult.Success(data)
            } else {
                ApiResult.Error("UNKNOWN", "Empty response")
            }
        } else {
            val errorBody = response.errorBody()?.string().orEmpty()
            val errorCode = errorMapCode(response.code())
            return ApiResult.Error(
                errorCode,
                errorBody.ifBlank { "Request failed: ${response.code()}" }
            )
        }
    }

    /**
     * Builds the tRPC batch input JSON: {"0":{"json":{...block...}}}
     */
    private fun buildTrpcInput(block: JSONObject.() -> Unit): String {
        return JSONObject().apply {
            put("0", JSONObject().apply {
                put("json", JSONObject().apply(block))
            })
        }.toString()
    }

    private fun Settings.authHeader(): String = "Bearer $apiKey"

    // ──────────────────────────────────────────────
    //  Public API
    // ──────────────────────────────────────────────

    suspend fun exchangeKey(email: String, password: String): ApiResult<ExchangeKeyResponse> {
        val settings = currentSettings ?: return ApiResult.Error("NOT_CONFIGURED", "Repository not configured")

        return safeApiCall {
            val randStr = UUID.randomUUID().toString().take(8)
            val request = ExchangeKeyRequest(
                email = email.trim(),
                password = password,
                keyName = "Mobile App: ($randStr)"
            )
            val response = apiService.exchangeKey(TrpcInput(json = request))
            handleTrpcPostResponse(response) { code ->
                when (code) {
                    401 -> "UNAUTHORIZED"
                    403 -> "FORBIDDEN"
                    429 -> "RATE_LIMIT"
                    else -> "UNKNOWN"
                }
            }
        }
    }

    suspend fun validateKey(apiKey: String): ApiResult<ValidateKeyResponse> {
        val settings = currentSettings ?: return ApiResult.Error("NOT_CONFIGURED", "Repository not configured")

        return safeApiCall {
            val request = ValidateKeyRequest(apiKey = apiKey)
            val response = apiService.validateKey(TrpcInput(json = request))
            handleTrpcPostResponse(response) { code ->
                when (code) {
                    401 -> "UNAUTHORIZED"
                    429 -> "RATE_LIMIT"
                    else -> "UNKNOWN"
                }
            }
        }
    }

    suspend fun healthCheck(): ApiResult<Boolean> {
        val settings = currentSettings ?: return ApiResult.Error("NOT_CONFIGURED", "Repository not configured")

        return safeApiCall {
            val response = apiService.healthCheck()
            if (response.isSuccessful) {
                ApiResult.Success(true)
            } else {
                ApiResult.Error("FAILED", "Health check failed")
            }
        }
    }

    suspend fun getBookmarks(
        archived: Boolean? = false,
        tagId: String? = null,
        cursor: BookmarkCursor? = null,
        limit: Int = 20
    ): ApiResult<List<BookmarkItem>> {
        val settings = currentSettings ?: return ApiResult.Error("NOT_CONFIGURED", "Repository not configured")
        if (!settings.isLoggedIn()) return ApiResult.Error("NOT_LOGGED_IN", "Not logged in")

        return safeApiCall {
            val inputJson = buildTrpcInput {
                archived?.let { put("archived", it) }
                put("includeContent", false)
                put("useCursorV2", true)
                put("limit", limit)
                tagId?.takeIf { it.isNotBlank() }?.let { put("tagId", it) }
                cursor?.let {
                    put("cursor", JSONObject().apply {
                        put("createdAt", it.createdAt)
                        put("id", it.id)
                    })
                }
            }

            val response = apiService.getBookmarks(
                auth = settings.authHeader(),
                batch = "1",
                input = inputJson
            )

            handleBatchGetResponse(response, "load bookmarks") { json ->
                parseBookmarksPageFromBatchJson(json)?.bookmarks
            }
        }
    }

    suspend fun createLinkBookmark(
        url: String,
        title: String? = null,
        note: String? = null
    ): ApiResult<Unit> {
        val settings = currentSettings ?: return ApiResult.Error("NOT_CONFIGURED", "Repository not configured")
        if (!settings.isLoggedIn()) return ApiResult.Error("NOT_LOGGED_IN", "Not logged in")

        return safeApiCall {
            val response = apiService.createBookmark(
                auth = settings.authHeader(),
                request = TrpcInput(
                    json = CreateBookmarkRequest(
                        url = url.trim(),
                        title = title,
                        note = note
                    )
                )
            )

            if (response.isSuccessful) {
                ApiResult.Success(Unit)
            } else {
                val errorBody = response.errorBody()?.string().orEmpty()
                ApiResult.Error("FAILED", errorBody.ifBlank { "Failed to create bookmark" })
            }
        }
    }

    suspend fun getAllBookmarksByTag(
        tagId: String,
        archived: Boolean? = null,
        limit: Int = 20,
        maxTotal: Int = 200
    ): ApiResult<List<BookmarkItem>> {
        val settings = currentSettings ?: return ApiResult.Error("NOT_CONFIGURED", "Repository not configured")
        if (!settings.isLoggedIn()) return ApiResult.Error("NOT_LOGGED_IN", "Not logged in")

        return safeApiCall {
            val allItems = mutableListOf<BookmarkItem>()
            var cursor: BookmarkCursor? = null

            while (allItems.size < maxTotal) {
                val pageResult = getBookmarksPage(
                    archived = archived,
                    tagId = tagId,
                    cursor = cursor,
                    limit = minOf(limit, maxTotal - allItems.size)
                )

                when (pageResult) {
                    is ApiResult.Success -> {
                        if (pageResult.data.bookmarks.isEmpty()) break
                        allItems += pageResult.data.bookmarks
                        cursor = pageResult.data.nextCursor
                        if (cursor == null) break
                    }

                    is ApiResult.Error -> return@safeApiCall pageResult
                    is ApiResult.NetworkError -> return@safeApiCall pageResult
                }
            }

            ApiResult.Success(allItems)
        }
    }

    suspend fun whoAmI(): ApiResult<WhoAmIResponse> {
        val settings = currentSettings ?: return ApiResult.Error("NOT_CONFIGURED", "Repository not configured")
        if (!settings.isLoggedIn()) return ApiResult.Error("NOT_LOGGED_IN", "Not logged in")

        return safeApiCall {
            val inputJson = buildTrpcInput { }

            val response = apiService.whoAmI(
                auth = settings.authHeader(),
                batch = "1",
                input = inputJson
            )

            handleBatchGetResponse(response, "load profile") { json ->
                WhoAmIResponse(
                    id = json.optString("id"),
                    name = json.optStringOrNull("name"),
                    email = json.optStringOrNull("email"),
                    image = json.optStringOrNull("image")
                )
            }
        }
    }

    suspend fun getTags(
        nameContains: String? = null,
        limit: Int = 50,
        sortBy: String = if (nameContains.isNullOrBlank()) "usage" else "relevance",
        page: Int = 0
    ): ApiResult<List<TagItem>> {
        val settings = currentSettings ?: return ApiResult.Error("NOT_CONFIGURED", "Repository not configured")
        if (!settings.isLoggedIn()) return ApiResult.Error("NOT_LOGGED_IN", "Not logged in")

        return safeApiCall {
            val inputJson = buildTrpcInput {
                put("limit", limit)
                put("sortBy", sortBy)
                put("cursor", JSONObject().apply { put("page", page) })
                nameContains?.takeIf { it.isNotBlank() }?.let { put("nameContains", it) }
            }

            val response = apiService.listTags(
                auth = settings.authHeader(),
                batch = "1",
                input = inputJson
            )

            handleBatchGetResponse(response, "load tags") { json ->
                parseTagsListFromBatchJson(json)
            }
        }
    }

    suspend fun getTag(tagId: String): ApiResult<TagDetails> {
        val settings = currentSettings ?: return ApiResult.Error("NOT_CONFIGURED", "Repository not configured")
        if (!settings.isLoggedIn()) return ApiResult.Error("NOT_LOGGED_IN", "Not logged in")

        return safeApiCall {
            val inputJson = buildTrpcInput {
                put("tagId", tagId)
            }

            val response = apiService.getTag(
                auth = settings.authHeader(),
                batch = "1",
                input = inputJson
            )

            handleBatchGetResponse(response, "load tag") { json ->
                val id = json.optStringOrNull("id") ?: return@handleBatchGetResponse null
                val name = json.optStringOrNull("name") ?: return@handleBatchGetResponse null
                TagDetails(
                    id = id,
                    name = name,
                    numBookmarks = json.optInt("numBookmarks", 0)
                )
            }
        }
    }

    suspend fun revokeKey(apiKeyId: String): ApiResult<Unit> {
        val settings = currentSettings ?: return ApiResult.Error("NOT_CONFIGURED", "Repository not configured")
        if (!settings.isLoggedIn()) return ApiResult.Error("NOT_LOGGED_IN", "Not logged in")

        return safeApiCall {
            val auth = settings.authHeader()
            val request = RevokeKeyRequest(id = apiKeyId)
            val response = apiService.revokeKey(auth, TrpcInput(json = request))

            if (response.isSuccessful) {
                ApiResult.Success(Unit)
            } else {
                ApiResult.Error("FAILED", "Failed to revoke key")
            }
        }
    }

    // ──────────────────────────────────────────────
    //  Private: paginated bookmarks (used by getAllBookmarksByTag)
    // ──────────────────────────────────────────────

    private suspend fun getBookmarksPage(
        archived: Boolean?,
        tagId: String?,
        cursor: BookmarkCursor?,
        limit: Int
    ): ApiResult<GetBookmarksResponse> {
        val settings = requireSettings()

        return safeApiCall {
            val inputJson = buildTrpcInput {
                archived?.let { put("archived", it) }
                put("includeContent", false)
                put("useCursorV2", true)
                put("limit", limit)
                tagId?.takeIf { it.isNotBlank() }?.let { put("tagId", it) }
                cursor?.let {
                    put("cursor", JSONObject().apply {
                        put("createdAt", it.createdAt)
                        put("id", it.id)
                    })
                }
            }

            val response = apiService.getBookmarks(
                auth = settings.authHeader(),
                batch = "1",
                input = inputJson
            )

            handleBatchGetResponse(response, "load bookmarks") { json ->
                parseBookmarksPageFromBatchJson(json)
            }
        }
    }

    // ──────────────────────────────────────────────
    //  Private: JSON parsing
    // ──────────────────────────────────────────────

    private fun parseBookmarksPageFromBatchJson(json: JSONObject): GetBookmarksResponse? {
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

        val nextCursor = json.optJSONObject("nextCursor")?.let {
            val id = it.optStringOrNull("id") ?: return@let null
            val createdAt = it.optStringOrNull("createdAt") ?: return@let null
            BookmarkCursor(createdAt = createdAt, id = id)
        }

        return GetBookmarksResponse(
            bookmarks = items,
            nextCursor = nextCursor
        )
    }

    private fun parseTagsListFromBatchJson(json: JSONObject): List<TagItem>? {
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
}
