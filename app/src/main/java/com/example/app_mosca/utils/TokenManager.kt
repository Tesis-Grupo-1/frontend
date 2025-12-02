package com.example.app_mosca.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * TokenManager que usa SecureStorage (Android Keystore) para almacenar el token JWT.
 * REQUISITO RFP05: El token JWT se almacena de forma segura usando Android Keystore.
 * 
 * Los datos del usuario (email, role) se mantienen en EncryptedSharedPreferences
 * ya que no son tan sensibles como el token JWT.
 */
class TokenManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "secure_prefs"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_ROLE = "user_role"
    }

    // REQUISITO RFP05: SecureStorage para el token JWT usando Android Keystore
    private val secureStorage: SecureStorage = SecureStorage(context)
    
    // EncryptedSharedPreferences para datos del usuario (menos sensibles)
    private val sharedPrefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        sharedPrefs = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Guarda el token JWT de forma segura usando Android Keystore.
     * REQUISITO RFP05: El token se cifra con AES/GCM/NoPadding.
     */
    fun saveToken(token: String) {
        val success = secureStorage.saveToken(token)
        if (!success) {
            throw SecurityException("No se pudo guardar el token de forma segura")
        }
    }

    /**
     * Obtiene el token JWT descifrado desde el almacenamiento seguro.
     * REQUISITO RFP05: El token se descifra desde Android Keystore.
     */
    fun getToken(): String? {
        return secureStorage.getToken()
    }

    fun saveUserData(email: String, role: String) {
        sharedPrefs.edit()
            .putString(KEY_USER_EMAIL, email)
            .putString(KEY_USER_ROLE, role)
            .apply()
    }

    fun getUserEmail(): String? {
        return sharedPrefs.getString(KEY_USER_EMAIL, null)
    }

    fun getUserRole(): String? {
        return sharedPrefs.getString(KEY_USER_ROLE, null)
    }

    /**
     * Elimina el token JWT y los datos del usuario de forma segura.
     * REQUISITO RFP05: El token se elimina del almacenamiento seguro.
     */
    fun clearToken() {
        // REQUISITO RFP05: Eliminar token del SecureStorage (Android Keystore)
        secureStorage.deleteToken()
        
        // Eliminar datos del usuario
        sharedPrefs.edit()
            .remove(KEY_USER_EMAIL)
            .remove(KEY_USER_ROLE)
            .apply()
    }

    fun isLoggedIn(): Boolean {
        return !getToken().isNullOrEmpty()
    }
}