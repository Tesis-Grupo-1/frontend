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

    private const val BASE_URL = "https://www.sominascan.tech/"
    // private const val BASE_URL = "http://localhost:8000/"
    private lateinit var tokenManager: TokenManager

    fun initialize(tokenManager: TokenManager) {
        this.tokenManager = tokenManager
    }

    /**
     * Interceptor para agregar el token JWT a cada petición.
     * REQUISITO RFP05: Lee el token desde SecureStorage (Android Keystore) a través de TokenManager.
     * 
     * - Si no hay token → no se agrega el header Authorization
     * - Si hay token → se agrega como "Bearer {token}"
     * - Si recibimos 401 → elimina el token del almacenamiento seguro
     */
    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()

        // Si es el endpoint de login o registro, no agregar token
        if (originalRequest.url.pathSegments.contains("login") ||
            originalRequest.url.pathSegments.contains("register")) {
            return@Interceptor chain.proceed(originalRequest)
        }

        // REQUISITO RFP05: Obtener token desde SecureStorage (Android Keystore)
        val token = tokenManager.getToken()

        // Si el token no existe, no se agrega el header Authorization
        val newRequest = if (token != null) {
            originalRequest.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            originalRequest
        }

        val response = chain.proceed(newRequest)

        // Si recibimos 401, el token expiró o es inválido
        if (response.code == 401) {
            // REQUISITO RFP05: Eliminar token del almacenamiento seguro
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