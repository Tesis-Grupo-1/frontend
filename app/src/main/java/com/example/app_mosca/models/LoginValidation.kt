package com.example.app_mosca.models

data class LoginValidation(
    val isEmailValid: Boolean = true,
    val isPasswordValid: Boolean = true,
    val emailError: String? = null,
    val passwordError: String? = null
) {
    val isValid: Boolean
        get() = isEmailValid && isPasswordValid
}