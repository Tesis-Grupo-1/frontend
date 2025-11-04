package com.example.app_mosca.models

data class UserResponse(
    val id: Int,
    val email: String,
    val full_name: String,
    val role: String,
    val is_active: Boolean,
    val linking_code: String? = null
)