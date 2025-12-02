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
                    val loginResponse = response.body()
                    if (loginResponse != null) {
                        // REQUISITO RFP05: Guardar el token JWT en SecureStorage (Android Keystore)
                        // El token se cifra con AES/GCM/NoPadding antes de almacenarse
                        tokenManager.saveToken(loginResponse.access_token)

                        // Guardar datos del usuario (vienen en la respuesta del login)
                        val user = loginResponse.user
                        tokenManager.saveUserData(user.email, user.role)
                        
                        Result.success(user)
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

    suspend fun register(email: String, fullName: String, password: String, role: String = "employee"): Result<UserResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val registerRequest = RegisterRequest(
                    email = email,
                    full_name = fullName,
                    role = role,
                    password = password
                )
                
                val response = apiService.register(registerRequest)
                
                if (response.isSuccessful) {
                    val user = response.body()
                    if (user != null) {
                        // Después del registro exitoso, hacer login automático para obtener el token
                        val loginResponse = apiService.login(email, password)
                        
                        if (loginResponse.isSuccessful) {
                            val token = loginResponse.body()
                            if (token != null) {
                                // REQUISITO RFP05: Guardar el token JWT en SecureStorage (Android Keystore)
                                // El token se cifra con AES/GCM/NoPadding antes de almacenarse
                                tokenManager.saveToken(token.access_token)
                                
                                // Guardar datos del usuario
                                tokenManager.saveUserData(user.email, user.role)
                                
                                Result.success(user)
                            } else {
                                Result.failure(Exception("Error al obtener el token de autenticación"))
                            }
                        } else {
                            val errorMsg = parseErrorMessage(loginResponse)
                            Result.failure(Exception("Registro exitoso, pero error al iniciar sesión: $errorMsg"))
                        }
                    } else {
                        Result.failure(Exception("Error al crear la cuenta"))
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

    /**
     * Cierra la sesión del usuario.
     * REQUISITO RFP05: Elimina el token JWT del almacenamiento seguro (Android Keystore).
     */
    fun logout() {
        // REQUISITO RFP05: Eliminar token del SecureStorage
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