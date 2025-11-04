package com.example.app_mosca.models

data class LoginRequest(
    val email: String,  // email o usuario según la API
    val password: String
)