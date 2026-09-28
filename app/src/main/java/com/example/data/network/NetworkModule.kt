package com.example.data.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {
    const val USER_AGENT = "ValenciaTransitHub/1.0 (Android; Contact: info@valenciatransithub.com)"

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20L, TimeUnit.SECONDS)
            .readTimeout(25L, TimeUnit.SECONDS)
            .writeTimeout(20L, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val request = chain.request()
                val header = request.newBuilder().header("Accept", "application/json")
                if (request.url.host.contains("metrovalencia")) {
                    header.header("X-App-Secret", "ApiVLCTH2584")
                }
                chain.proceed(header.build())
            }
            .build()
    }

    private val nominatimOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15L, TimeUnit.SECONDS)
            .readTimeout(15L, TimeUnit.SECONDS)
            .writeTimeout(15L, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .build()
                )
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

    val nominatimApiService: NominatimApiService by lazy {
        nominatimRetrofit.create(NominatimApiService::class.java)
    }

    private val transitousOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10L, TimeUnit.SECONDS)
            .readTimeout(10L, TimeUnit.SECONDS)
            .writeTimeout(10L, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/json")
                        .build()
                )
            }
            .build()
    }

    val transitousRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.transitous.org/api/v1/")
            .client(transitousOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val transitousApiService: TransitousApiService by lazy {
        transitousRetrofit.create(TransitousApiService::class.java)
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
                } catch (_: InterruptedException) {
                }
            }
            lastRequestTime = System.currentTimeMillis()
            return chain.proceed(chain.request())
        }
    }
}
