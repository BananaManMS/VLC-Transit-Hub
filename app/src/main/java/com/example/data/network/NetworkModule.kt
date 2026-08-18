package com.example.data.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {
    const val USER_AGENT = "VLC Transit/1.0 (https://github.com/BananaManMS/Valencia-Transit-Hub)"

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private class RateLimitInterceptor(private val minIntervalMs: Long) : Interceptor {
        private var lastRequestTime = 0L

        @Synchronized
        override fun intercept(chain: Interceptor.Chain): Response {
            val now = System.currentTimeMillis()
            val diff = now - lastRequestTime
            if (diff < minIntervalMs) {
                val sleepTime = minIntervalMs - diff
                try {
                    Thread.sleep(sleepTime)
                } catch (e: InterruptedException) {
                    // Ignore
                }
            }
            lastRequestTime = System.currentTimeMillis()
            return chain.proceed(chain.request())
        }
    }

    private val nominatimOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(RateLimitInterceptor(1000L))
            .build()
    }

    val nominatimRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://nominatim.openstreetmap.org/")
            .client(nominatimOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val transitousOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    val transitousRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.transitous.org/api/v2/")
            .client(transitousOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val transitousApiService: TransitousApiService by lazy {
        transitousRetrofit.create(TransitousApiService::class.java)
    }
}
