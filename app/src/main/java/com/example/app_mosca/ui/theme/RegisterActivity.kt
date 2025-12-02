package com.example.app_mosca.ui.theme

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.app_mosca.R
import com.example.app_mosca.api.apiClient.ApiClient
import com.example.app_mosca.databinding.ActivityRegisterBinding
import com.example.app_mosca.repositories.AuthRepository
import com.example.app_mosca.utils.TokenManager
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "RegisterActivity"
    }

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var tokenManager: TokenManager
    private lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar TokenManager y ApiClient
        tokenManager = TokenManager(this)
        ApiClient.initialize(tokenManager)
        authRepository = AuthRepository(ApiClient.apiService, tokenManager)

        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ocultar ActionBar si existe
        supportActionBar?.hide()

        setupUI()
    }

    private fun setupUI() {
        // Configurar listeners para los campos de texto
        binding.nameEditText.addTextChangedListener(createTextWatcher {
            validateForm()
        })

        binding.emailEditText.addTextChangedListener(createTextWatcher {
            validateEmail()
            validateForm()
        })

        binding.passwordEditText.addTextChangedListener(createTextWatcher {
            validatePassword()
            validateForm()
        })

        binding.confirmPasswordEditText.addTextChangedListener(createTextWatcher {
            validatePasswordMatch()
            validateForm()
        })

        // Listener para el CheckBox de consentimiento (REQUISITO RPF02)
        binding.privacyConsentCheckbox.setOnCheckedChangeListener { _, isChecked ->
            validateForm()
            if (!isChecked && binding.registerButton.isEnabled) {
                // Si se desmarca, deshabilitar el botón inmediatamente
                disableRegisterButton()
            }
        }

        // Listener para el botón de registro
        binding.registerButton.setOnClickListener {
            attemptRegister()
        }

        // Listener para el enlace de login
        binding.loginLink.setOnClickListener {
            navigateToLogin()
        }

        // Hacer clickeable el texto "Aviso de Privacidad" en el CheckBox
        setupPrivacyNoticeClickableText()
    }

    /**
     * Configura el texto del consentimiento para que "Aviso de Privacidad" sea clickeable
     * y abra PrivacyNoticeActivity.
     * REQUISITO RPF02: Permite al usuario ver el aviso de privacidad antes de aceptarlo.
     */
    private fun setupPrivacyNoticeClickableText() {
        val fullText = "He leído y acepto el Aviso de Privacidad"
        val privacyText = "Aviso de Privacidad"
        
        val spannable = SpannableString(fullText)
        
        // Encontrar la posición del texto "Aviso de Privacidad"
        val startIndex = fullText.indexOf(privacyText)
        val endIndex = startIndex + privacyText.length
        
        if (startIndex >= 0) {
            // Crear un ClickableSpan para el texto "Aviso de Privacidad"
            val clickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    // Abrir PrivacyNoticeActivity cuando se toque "Aviso de Privacidad"
                    val intent = Intent(this@RegisterActivity, PrivacyNoticeActivity::class.java)
                    startActivity(intent)
                }
            }
            
            // Aplicar el clickable span
            spannable.setSpan(
                clickableSpan,
                startIndex,
                endIndex,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            // Cambiar el color del texto clickeable
            val colorSpan = ForegroundColorSpan(
                ContextCompat.getColor(this, R.color.teal_700)
            )
            spannable.setSpan(
                colorSpan,
                startIndex,
                endIndex,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        
        // Aplicar el texto spannable al TextView
        binding.privacyConsentText.text = spannable
        binding.privacyConsentText.movementMethod = LinkMovementMethod.getInstance()
    }

    /**
     * Valida el formulario completo y habilita/deshabilita el botón de registro.
     * REQUISITO RPF02: El botón solo se habilita si el checkbox de consentimiento está marcado.
     * @return true si el formulario es válido, false en caso contrario.
     */
    private fun validateForm(): Boolean {
        val isNameValid = binding.nameEditText.text.toString().trim().isNotEmpty()
        val isEmailValid = validateEmail()
        val isPasswordValid = validatePassword()
        val isPasswordMatchValid = validatePasswordMatch()
        
        // REQUISITO RPF02: Validación obligatoria del consentimiento
        val isConsentAccepted = binding.privacyConsentCheckbox.isChecked

        val isFormValid = isNameValid && isEmailValid && isPasswordValid && 
                         isPasswordMatchValid && isConsentAccepted

        if (isFormValid) {
            enableRegisterButton()
        } else {
            disableRegisterButton()
        }
        
        return isFormValid
    }

    private fun validateEmail(): Boolean {
        val email = binding.emailEditText.text.toString().trim()
        val emailPattern = android.util.Patterns.EMAIL_ADDRESS

        return if (email.isEmpty()) {
            binding.emailInputLayout.error = "El email es requerido"
            binding.emailInputLayout.boxStrokeColor = 
                ContextCompat.getColor(this, android.R.color.holo_red_dark)
            false
        } else if (!emailPattern.matcher(email).matches()) {
            binding.emailInputLayout.error = "Email inválido"
            binding.emailInputLayout.boxStrokeColor = 
                ContextCompat.getColor(this, android.R.color.holo_red_dark)
            false
        } else {
            binding.emailInputLayout.error = null
            binding.emailInputLayout.boxStrokeColor = 
                ContextCompat.getColor(this, R.color.teal_700)
            true
        }
    }

    private fun validatePassword(): Boolean {
        val password = binding.passwordEditText.text.toString()

        return if (password.isEmpty()) {
            binding.passwordInputLayout.error = "La contraseña es requerida"
            binding.passwordInputLayout.boxStrokeColor = 
                ContextCompat.getColor(this, android.R.color.holo_red_dark)
            false
        } else if (password.length < 8) {
            binding.passwordInputLayout.error = "La contraseña debe tener al menos 8 caracteres"
            binding.passwordInputLayout.boxStrokeColor = 
                ContextCompat.getColor(this, android.R.color.holo_red_dark)
            false
        } else {
            binding.passwordInputLayout.error = null
            binding.passwordInputLayout.boxStrokeColor = 
                ContextCompat.getColor(this, R.color.teal_700)
            true
        }
    }

    private fun validatePasswordMatch(): Boolean {
        val password = binding.passwordEditText.text.toString()
        val confirmPassword = binding.confirmPasswordEditText.text.toString()

        return if (confirmPassword.isEmpty()) {
            binding.confirmPasswordInputLayout.error = "Confirma tu contraseña"
            binding.confirmPasswordInputLayout.boxStrokeColor = 
                ContextCompat.getColor(this, android.R.color.holo_red_dark)
            false
        } else if (password != confirmPassword) {
            binding.confirmPasswordInputLayout.error = "Las contraseñas no coinciden"
            binding.confirmPasswordInputLayout.boxStrokeColor = 
                ContextCompat.getColor(this, android.R.color.holo_red_dark)
            false
        } else {
            binding.confirmPasswordInputLayout.error = null
            binding.confirmPasswordInputLayout.boxStrokeColor = 
                ContextCompat.getColor(this, R.color.teal_700)
            true
        }
    }

    private fun enableRegisterButton() {
        binding.registerButton.isEnabled = true
        binding.registerButton.alpha = 1.0f
    }

    private fun disableRegisterButton() {
        binding.registerButton.isEnabled = false
        binding.registerButton.alpha = 0.6f
    }

    /**
     * Intenta registrar al usuario.
     * REQUISITO RPF02: Valida que el consentimiento esté aceptado antes de enviar al backend.
     */
    private fun attemptRegister() {
        // REQUISITO RPF02: Validación final del consentimiento antes de enviar
        if (!binding.privacyConsentCheckbox.isChecked) {
            showConsentError()
            return
        }

        // Validar formulario una vez más antes de enviar
        if (!validateForm()) {
            showError("Por favor, completa todos los campos correctamente")
            return
        }

        // Obtener datos del formulario
        val name = binding.nameEditText.text.toString().trim()
        val email = binding.emailEditText.text.toString().trim()
        val password = binding.passwordEditText.text.toString()

        // Mostrar loading
        showLoading()

        // Llamada al API de registro
        lifecycleScope.launch {
            val result = authRepository.register(
                email = email,
                fullName = name,
                password = password,
                role = "employee" // Por defecto "employee" según la documentación de la API
            )
            
            result.fold(
                onSuccess = { user ->
                    hideLoading()
                    showSuccess("Cuenta creada exitosamente. Bienvenido ${user.full_name}")
                    Log.d(TAG, "Registro exitoso - Nombre: ${user.full_name}, Email: ${user.email}, Token guardado")
                    // Navegar a MainActivity ya que el usuario está autenticado (token guardado)
                    navigateToMain()
                },
                onFailure = { exception ->
                    hideLoading()
                    val errorMessage = exception.message ?: "Error al crear la cuenta"
                    Log.e(TAG, "Error en registro: $errorMessage", exception)
                    showError(errorMessage)
                }
            )
        }
    }

    /**
     * Muestra un error específico cuando el usuario intenta registrarse sin aceptar el consentimiento.
     * REQUISITO RPF02: Snackbar o Toast indicando que falta aceptar el aviso.
     */
    private fun showConsentError() {
        // Usar Snackbar para mejor UX (requisito RPF02)
        Snackbar.make(
            binding.root,
            "Debes aceptar el Aviso de Privacidad para continuar",
            Snackbar.LENGTH_LONG
        ).setAction("Ver aviso") {
            // TODO: Abrir PrivacyNoticeActivity cuando se implemente
            // val intent = Intent(this, PrivacyNoticeActivity::class.java)
            // startActivity(intent)
            showMessage("Abrir aviso de privacidad")
        }.show()

        // También mostrar Toast como alternativa
        Toast.makeText(
            this,
            "Debes aceptar el Aviso de Privacidad para continuar",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showLoading() {
        binding.registerButton.isEnabled = false
        binding.registerButton.text = "Creando cuenta..."
        binding.nameEditText.isEnabled = false
        binding.emailEditText.isEnabled = false
        binding.passwordEditText.isEnabled = false
        binding.confirmPasswordEditText.isEnabled = false
        binding.privacyConsentCheckbox.isEnabled = false
    }

    private fun hideLoading() {
        binding.registerButton.isEnabled = true
        binding.registerButton.text = "CREAR CUENTA"
        binding.nameEditText.isEnabled = true
        binding.emailEditText.isEnabled = true
        binding.passwordEditText.isEnabled = true
        binding.confirmPasswordEditText.isEnabled = true
        binding.privacyConsentCheckbox.isEnabled = true
        validateForm() // Revalidar para actualizar estado del botón
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showSuccess(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun createTextWatcher(onTextChanged: () -> Unit): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                onTextChanged()
            }
        }
    }
}

