package com.example.app_mosca

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.app_mosca.api.apiClient.ApiClient
import com.example.app_mosca.repositories.AuthRepository
import com.example.app_mosca.ui.theme.LoginActivity
import com.example.app_mosca.ui.theme.MainActivity
import com.example.app_mosca.utils.NetworkUtils
import com.example.app_mosca.utils.TokenManager
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SplashActivity"
        private const val SPLASH_TIME_OUT = 2000L // 2 segundos
    }

    private lateinit var tokenManager: TokenManager
    private lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Ocultar ActionBar
        supportActionBar?.hide()

        // Inicializar dependencias
        initializeComponents()

        // Verificar autenticación después del splash
        Handler(Looper.getMainLooper()).postDelayed({
            checkAuthentication()
        }, SPLASH_TIME_OUT)
    }

    private fun initializeComponents() {
        tokenManager = TokenManager(this)
        ApiClient.initialize(tokenManager)
        authRepository = AuthRepository(ApiClient.apiService, tokenManager)
    }

    private fun checkAuthentication() {
        if (tokenManager.isLoggedIn()) {
            Log.d(TAG, "Token encontrado, verificando validez...")

            // Verificar si hay conexión a internet
            if (!NetworkUtils.isNetworkAvailable(this)) {
                Log.w(TAG, "Sin conexión a internet, usando token almacenado")
                navigateToMain()
                return
            }

            // Verificar si el token es válido
            lifecycleScope.launch {
                val result = authRepository.getCurrentUser()
                result.fold(
                    onSuccess = { user ->
                        Log.d(TAG, "Token válido - Usuario: ${user.full_name}")
                        navigateToMain()
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "Token inválido: ${exception.message}")
                        authRepository.logout()
                        navigateToLogin()
                    }
                )
            }
        } else {
            Log.d(TAG, "No hay token, redirigiendo a login")
            navigateToLogin()
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}