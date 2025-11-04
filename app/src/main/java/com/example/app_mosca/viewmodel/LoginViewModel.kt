package com.example.app_mosca.viewmodels

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_mosca.models.AuthState
import com.example.app_mosca.models.LoginValidation
import com.example.app_mosca.repositories.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _validation = MutableStateFlow(LoginValidation())
    val validation: StateFlow<LoginValidation> = _validation.asStateFlow()

    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _rememberMe = MutableStateFlow(false)
    val rememberMe: StateFlow<Boolean> = _rememberMe.asStateFlow()

    fun onEmailChanged(newEmail: String) {
        _email.value = newEmail
        validateEmail(newEmail)
    }

    fun onPasswordChanged(newPassword: String) {
        _password.value = newPassword
        validatePassword(newPassword)
    }

    fun onRememberMeChanged(remember: Boolean) {
        _rememberMe.value = remember
    }

    fun login() {
        if (!validateForm()) return

        viewModelScope.launch {
            _authState.value = AuthState.Loading

            val result = authRepository.login(_email.value, _password.value)

            result.fold(
                onSuccess = { user ->
                    _authState.value = AuthState.Success(user)
                },
                onFailure = { exception ->
                    _authState.value = AuthState.Error(
                        exception.message ?: "Error desconocido"
                    )
                }
            )
        }
    }

    fun checkAuthStatus() {
        if (authRepository.isLoggedIn()) {
            viewModelScope.launch {
                _authState.value = AuthState.Loading

                val result = authRepository.getCurrentUser()
                result.fold(
                    onSuccess = { user ->
                        _authState.value = AuthState.Success(user)
                    },
                    onFailure = {
                        // Token inválido, limpiar y mostrar login
                        authRepository.logout()
                        _authState.value = AuthState.Idle
                    }
                )
            }
        }
    }

    fun clearError() {
        _authState.value = AuthState.Idle
    }

    private fun validateEmail(email: String): Boolean {
        val isValid = when {
            email.isEmpty() -> {
                _validation.value = _validation.value.copy(
                    isEmailValid = false,
                    emailError = "El email es obligatorio"
                )
                false
            }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                _validation.value = _validation.value.copy(
                    isEmailValid = false,
                    emailError = "Email inválido"
                )
                false
            }
            else -> {
                _validation.value = _validation.value.copy(
                    isEmailValid = true,
                    emailError = null
                )
                true
            }
        }
        return isValid
    }

    private fun validatePassword(password: String): Boolean {
        val isValid = when {
            password.isEmpty() -> {
                _validation.value = _validation.value.copy(
                    isPasswordValid = false,
                    passwordError = "La contraseña es obligatoria"
                )
                false
            }
            password.length < 6 -> {
                _validation.value = _validation.value.copy(
                    isPasswordValid = false,
                    passwordError = "La contraseña debe tener al menos 6 caracteres"
                )
                false
            }
            else -> {
                _validation.value = _validation.value.copy(
                    isPasswordValid = true,
                    passwordError = null
                )
                true
            }
        }
        return isValid
    }

    private fun validateForm(): Boolean {
        val emailValid = validateEmail(_email.value)
        val passwordValid = validatePassword(_password.value)
        return emailValid && passwordValid
    }
}