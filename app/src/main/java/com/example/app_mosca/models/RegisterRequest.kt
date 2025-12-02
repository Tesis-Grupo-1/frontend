package com.example.app_mosca.models

data class RegisterRequest(
    val email: String,
    val full_name: String,
    val role: String = "employee", // Por defecto "employee" según la documentación
    val password: String
)

