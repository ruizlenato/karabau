package com.ruizlenato.karabau.data.remote

import com.ruizlenato.karabau.data.model.ExchangeKeyRequest
import com.ruizlenato.karabau.data.model.ExchangeKeyResponse
import com.ruizlenato.karabau.data.model.HealthCheckResponse
import com.ruizlenato.karabau.data.model.RevokeKeyRequest
import com.ruizlenato.karabau.data.model.ValidateKeyRequest
import com.ruizlenato.karabau.data.model.ValidateKeyResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface KarabauApiService {

    @POST("api/trpc/apiKeys.exchange")
    suspend fun exchangeKey(
        @Body request: TrpcInput<ExchangeKeyRequest>
    ): Response<TrpcResponse<ExchangeKeyResponse>>

    @POST("api/trpc/apiKeys.validate")
    suspend fun validateKey(
        @Body request: TrpcInput<ValidateKeyRequest>
    ): Response<TrpcResponse<ValidateKeyResponse>>

    @POST("api/trpc/apiKeys.revoke")
    suspend fun revokeKey(
        @Header("Authorization") auth: String,
        @Body request: TrpcInput<RevokeKeyRequest>
    ): Response<TrpcResponse<Unit>>

    @GET("api/trpc/bookmarks.getBookmarks")
    suspend fun getBookmarks(
        @Header("Authorization") auth: String,
        @Query("batch") batch: String,
        @Query("input") input: String
    ): Response<ResponseBody>

    @GET("api/trpc/users.whoami")
    suspend fun whoAmI(
        @Header("Authorization") auth: String,
        @Query("batch") batch: String,
        @Query("input") input: String
    ): Response<ResponseBody>

    @GET("api/trpc/tags.list")
    suspend fun listTags(
        @Header("Authorization") auth: String,
        @Query("batch") batch: String,
        @Query("input") input: String
    ): Response<ResponseBody>

    @GET("api/trpc/tags.get")
    suspend fun getTag(
        @Header("Authorization") auth: String,
        @Query("batch") batch: String,
        @Query("input") input: String
    ): Response<ResponseBody>

    @GET("api/health")
    suspend fun healthCheck(): Response<HealthCheckResponse>

    @GET("api/version")
    suspend fun checkVersion(
        @Header("Authorization") auth: String
    ): Response<VersionResponse>
}

data class TrpcInput<T>(
    val json: T
)

data class TrpcResponse<T>(
    val result: TrpcResult<T>? = null,
    val error: TrpcError? = null
)

data class TrpcResult<T>(
    val data: TrpcResultData<T>? = null
)

data class TrpcResultData<T>(
    val json: T? = null
)

data class TrpcError(
    val code: String,
    val message: String? = null
)

data class VersionResponse(
    val version: String
)
