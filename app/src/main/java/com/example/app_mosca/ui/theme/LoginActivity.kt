package com.example.app_mosca.ui.theme

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.app_mosca.api.apiClient.ApiClient
import com.example.app_mosca.databinding.ActivityLoginBinding
import com.example.app_mosca.models.AuthState
import com.example.app_mosca.repositories.AuthRepository
import com.example.app_mosca.utils.TokenManager
import com.example.app_mosca.viewmodels.LoginViewModel
import com.example.app_mosca.viewmodels.LoginViewModelFactory
import kotlinx.coroutines.launch
import com.example.app_mosca.R

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var tokenManager: TokenManager
    private lateinit var authRepository: AuthRepository

    private val viewModel: LoginViewModel by viewModels {
        LoginViewModelFactory(authRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar TokenManager y ApiClient
        tokenManager = TokenManager(this)
        ApiClient.initialize(tokenManager)
        authRepository = AuthRepository(ApiClient.apiService, tokenManager)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ocultar ActionBar si existe
        supportActionBar?.hide()

        setupUI()
        observeViewModel()

        // Verificar si ya está autenticado
        viewModel.checkAuthStatus()
    }

    private fun setupUI() {
        // Configurar listeners para los campos de texto
        binding.emailEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.onEmailChanged(s.toString())
            }
        })

        binding.passwordEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.onPasswordChanged(s.toString())
            }
        })

        binding.rememberCheckbox.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onRememberMeChanged(isChecked)
        }

        binding.loginButton.setOnClickListener {
            viewModel.login()
        }

        binding.forgotPassword.setOnClickListener {
            showMessage("Funcionalidad próximamente")
        }

        binding.createAccount.setOnClickListener {
            showMessage("Funcionalidad próximamente")
        }
    }

    private fun observeViewModel() {
        // Observar el estado de autenticación
        lifecycleScope.launch {
            viewModel.authState.collect { state ->
                when (state) {
                    is AuthState.Idle -> {
                        hideLoading()
                    }
                    is AuthState.Loading -> {
                        showLoading()
                    }
                    is AuthState.Success -> {
                        hideLoading()
                        showMessage("Bienvenido ${state.user.full_name}")
                        navigateToMain()
                    }
                    is AuthState.Error -> {
                        hideLoading()
                        showError(state.message)
                    }
                }
            }
        }

        // Observar validaciones
        lifecycleScope.launch {
            viewModel.validation.collect { validation ->
                // Email validation
                if (!validation.isEmailValid) {
                    binding.emailInputLayout.error = validation.emailError
                    binding.emailInputLayout.boxStrokeColor =
                        ContextCompat.getColor(this@LoginActivity, android.R.color.holo_red_dark)
                } else {
                    binding.emailInputLayout.error = null
                    binding.emailInputLayout.boxStrokeColor =
                        ContextCompat.getColor(this@LoginActivity, R.color.teal_700)
                }

                // Password validation
                if (!validation.isPasswordValid) {
                    binding.passwordInputLayout.error = validation.passwordError
                    binding.passwordInputLayout.boxStrokeColor =
                        ContextCompat.getColor(this@LoginActivity, android.R.color.holo_red_dark)
                } else {
                    binding.passwordInputLayout.error = null
                    binding.passwordInputLayout.boxStrokeColor =
                        ContextCompat.getColor(this@LoginActivity, R.color.teal_700)
                }

                // Habilitar/deshabilitar botón de login
                binding.loginButton.isEnabled = validation.isValid
                binding.loginButton.alpha = if (validation.isValid) 1.0f else 0.6f
            }
        }
    }

    private fun showLoading() {
        binding.loginButton.isEnabled = false
        binding.loginButton.text = "Iniciando sesión..."
        binding.emailEditText.isEnabled = false
        binding.passwordEditText.isEnabled = false
    }

    private fun hideLoading() {
        binding.loginButton.isEnabled = true
        binding.loginButton.text = "INICIAR SESIÓN"
        binding.emailEditText.isEnabled = true
        binding.passwordEditText.isEnabled = true
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        viewModel.clearError()
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}