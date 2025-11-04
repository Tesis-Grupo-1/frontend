package com.example.app_mosca.repositories

import com.example.app_mosca.api.apiEndpoints.ApiService
import com.example.app_mosca.models.*
import com.example.app_mosca.utils.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

class AuthRepository(
    private val apiService: ApiService,
    private val tokenManager: TokenManager
) {

    suspend fun login(email: String, password: String): Result<UserResponse> {
        return withContext(Dispatchers.IO) {
            try {
                // Llamada al endpoint de login
                val response = apiService.login(email, password)

                if (response.isSuccessful) {
                    val token = response.body()
                    if (token != null) {
                        // Guardar el token
                        tokenManager.saveToken(token.access_token)

                        // Obtener datos del usuario
                        val userResponse = apiService.getCurrentUser()
                        if (userResponse.isSuccessful) {
                            val user = userResponse.body()
                            if (user != null) {
                                // Guardar datos del usuario
                                tokenManager.saveUserData(user.email, user.role)
                                Result.success(user)
                            } else {
                                Result.failure(Exception("Error al obtener datos del usuario"))
                            }
                        } else {
                            val errorMsg = parseErrorMessage(userResponse)
                            Result.failure(Exception(errorMsg))
                        }
                    } else {
                        Result.failure(Exception("Token no recibido"))
                    }
                } else {
                    val errorMsg = parseErrorMessage(response)
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getCurrentUser(): Result<UserResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getCurrentUser()
                if (response.isSuccessful) {
                    val user = response.body()
                    if (user != null) {
                        tokenManager.saveUserData(user.email, user.role)
                        Result.success(user)
                    } else {
                        Result.failure(Exception("Usuario no encontrado"))
                    }
                } else {
                    val errorMsg = parseErrorMessage(response)
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    fun logout() {
        tokenManager.clearToken()
    }

    fun isLoggedIn(): Boolean {
        return tokenManager.isLoggedIn()
    }

    fun getCurrentUserEmail(): String? {
        return tokenManager.getUserEmail()
    }

    fun getCurrentUserRole(): String? {
        return tokenManager.getUserRole()
    }

    private fun parseErrorMessage(response: Response<*>): String {
        return when (response.code()) {
            400 -> "Datos inválidos. Verifica tu email y contraseña."
            401 -> "Credenciales incorrectas. Verifica tu email y contraseña."
            403 -> "Acceso denegado. Tu cuenta puede estar desactivada."
            404 -> "Usuario no encontrado."
            422 -> "Datos de entrada inválidos."
            500 -> "Error del servidor. Inténtalo más tarde."
            else -> "Error de conexión. Verifica tu conexión a internet."
        }
    }
}