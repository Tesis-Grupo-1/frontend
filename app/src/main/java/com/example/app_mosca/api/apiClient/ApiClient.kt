package com.example.app_mosca.api.apiClient

import com.example.app_mosca.api.apiEndpoints.ApiService
import com.example.app_mosca.utils.TokenManager
import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    // private const val BASE_URL = "https://625758cdcb72.ngrok-free.app/"
    private const val BASE_URL = "https://sominascan.tech/"
    private lateinit var tokenManager: TokenManager

    fun initialize(tokenManager: TokenManager) {
        this.tokenManager = tokenManager
    }

    // Interceptor para agregar el token JWT a cada petición
    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()

        // Si es el endpoint de login, no agregar token
        if (originalRequest.url.pathSegments.contains("login") ||
            originalRequest.url.pathSegments.contains("register")) {
            return@Interceptor chain.proceed(originalRequest)
        }

        val token = tokenManager.getToken()

        val newRequest = if (token != null) {
            originalRequest.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            originalRequest
        }

        val response = chain.proceed(newRequest)

        // Si recibimos 401, el token expiró
        if (response.code == 401) {
            // Limpiar token inválido
            tokenManager.clearToken()
            // El usuario será redirigido al login por el AuthRepository
        }

        response
    }

    // Interceptor para logging (solo en debug)
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor(authInterceptor)
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(
                GsonConverterFactory.create(
                    GsonBuilder()
                        .setLenient()
                        .create()
                )
            )
            .build()
            .create(ApiService::class.java)
    }

    // Método auxiliar para crear el servicio (usado por LoadingActivity)
    fun createApiService(context: Any): ApiService {
        return apiService
    }
}