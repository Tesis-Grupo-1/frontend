package com.example.app_mosca.api.apiClient

import com.example.app_mosca.api.apiEndpoints.ApiService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val BASE_URL = "https://minascan.duckdns.org/" // Reemplaza con la URL base de tu API

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY // Muestra todo el cuerpo de la solicitud/respuesta
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(120, TimeUnit.SECONDS)  // Tiempo máximo de conexión (30 segundos)
        .readTimeout(120, TimeUnit.SECONDS)     // Tiempo máximo de lectura (30 segundos)
        .writeTimeout(120, TimeUnit.SECONDS)    // Tiempo máximo de escritura (30 segundos)
        .build()

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create()) // Convierte JSON a objetos Kotlin usando Gson
            .build()
            .create(ApiService::class.java)
    }

}