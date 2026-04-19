package com.ruizlenato.karabau.data.remote

import com.ruizlenato.karabau.data.model.ExchangeKeyRequest
import com.ruizlenato.karabau.data.model.ExchangeKeyResponse
import com.ruizlenato.karabau.data.model.RevokeKeyRequest
import com.ruizlenato.karabau.data.model.Settings
import com.ruizlenato.karabau.data.model.ValidateKeyRequest
import com.ruizlenato.karabau.data.model.ValidateKeyResponse
import com.ruizlenato.karabau.data.model.isLoggedIn
import retrofit2.HttpException
import java.io.IOException
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
