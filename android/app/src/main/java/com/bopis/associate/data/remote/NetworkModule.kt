package com.bopis.associate.data.remote

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {
    // 10.0.2.2 is the Android emulator's alias for the host machine's localhost.
    // On a physical device (e.g. Pixel 6a), replace with the host's LAN IP.
    const val BASE_URL = "http://10.0.2.2:3000/"

    private val json = Json { ignoreUnknownKeys = true }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    // Server-sent-events connections stay open indefinitely, so they need no read timeout.
    val sseClient: OkHttpClient = okHttpClient.newBuilder()
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    val api: ApiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ApiService::class.java)
}
