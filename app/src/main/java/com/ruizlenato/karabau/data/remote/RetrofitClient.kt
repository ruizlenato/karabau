package com.ruizlenato.karabau.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val TIMEOUT = 30L
    private const val ENABLE_LOGGING = false

    private data class CacheKey(
        val baseUrl: String,
        val customHeaders: Map<String, String>
    )

    @Volatile
    private var cachedKey: CacheKey? = null
    @Volatile
    private var cachedService: KarabauApiService? = null
    private val lock = Any()

    fun getOrCreate(baseUrl: String, customHeaders: Map<String, String> = emptyMap()): KarabauApiService {
        val key = CacheKey(baseUrl, customHeaders)
        cachedService?.let { service ->
            if (cachedKey == key) return service
        }
        synchronized(lock) {
            cachedService?.let { service ->
                if (cachedKey == key) return service
            }
            val service = createNew(baseUrl, customHeaders)
            cachedKey = key
            cachedService = service
            return service
        }
    }

    private fun createNew(baseUrl: String, customHeaders: Map<String, String>): KarabauApiService {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (ENABLE_LOGGING) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
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
