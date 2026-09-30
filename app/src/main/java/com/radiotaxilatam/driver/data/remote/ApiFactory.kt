package com.radiotaxilatam.driver.data.remote

import com.radiotaxilatam.driver.BuildConfig
import com.radiotaxilatam.driver.data.SessionStore
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

val AppJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

object ApiFactory {
    fun retrofit(session: SessionStore, onUnauthorized: () -> Unit): Retrofit {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val token = session.token.value
                val request = chain.request().newBuilder()
                    // Evita la página de aviso del plan gratuito de ngrok (no afecta a otros servidores).
                    .header("ngrok-skip-browser-warning", "true")
                    .apply { if (token != null) header("Authorization", "Bearer $token") }
                    .build()
                val response = chain.proceed(request)
                if (response.code == 401 && token != null) onUnauthorized()
                response
            }
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
                }
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(AppJson.asConverterFactory("application/json".toMediaType()))
            .build()
    }
}
