package com.example.app_mosca.models

data class RegisterResponse(
    val access_token: String,
    val token_type: String,
    val user: UserResponse
)

