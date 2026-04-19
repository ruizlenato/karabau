package com.ruizlenato.karabau.data.remote

import android.util.Log
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class RetrofitClient {

    companion object {
        private const val TAG = "RetrofitClient"
        private const val TIMEOUT = 30L

        fun create(baseUrl: String, customHeaders: Map<String, String> = emptyMap()): KarabauApiService {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .apply {
                            header("Content-Type", "application/json")
                            customHeaders.forEach { (key, value) ->
                                header(key, value)
                            }
                        }
                        .build()
                    chain.proceed(request)
                }
                .connectTimeout(TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT, TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl.ensureTrailingSlash())
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            return retrofit.create(KarabauApiService::class.java)
        }

        private fun String.ensureTrailingSlash(): String {
            return if (endsWith("/")) this else "$this/"
        }
    }
}
