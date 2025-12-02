package com.example.app_mosca.utils

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Clase para almacenamiento seguro de credenciales usando Android Keystore.
 * REQUISITO RFP05: Almacenamiento seguro con AES/GCM/NoPadding y Android Keystore.
 * 
 * Esta clase:
 * - Genera una clave AES en el Android Keystore
 * - Cifra datos usando Cipher AES/GCM/NoPadding
 * - Almacena datos cifrados en SharedPreferences
 * - Maneja IVs correctamente (se guardan junto con el ciphertext)
 */
class SecureStorage(context: Context) {

    companion object {
        private const val TAG = "SecureStorage"
        private const val KEYSTORE_ALIAS = "MINASCAN_AES_KEY"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12 // 12 bytes para GCM
        private const val GCM_TAG_LENGTH = 128 // 128 bits para el tag de autenticación
        private const val PREFS_NAME = "secure_storage_prefs"
        private const val KEY_ENCRYPTED_DATA = "encrypted_data"
        private const val KEY_ENCRYPTED_IV = "encrypted_iv"
    }

    private val context: Context = context.applicationContext
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val keyStore: KeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)

    init {
        keyStore.load(null)
        ensureKeyExists()
    }

    /**
     * Asegura que la clave AES existe en el Keystore.
     * Si no existe, la genera automáticamente.
     */
    private fun ensureKeyExists() {
        try {
            if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
                generateKey()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al verificar/generar clave en Keystore", e)
            throw SecurityException("No se pudo inicializar el almacenamiento seguro", e)
        }
    }

    /**
     * Genera una clave AES en el Android Keystore.
     * REQUISITO RFP05: Usa KeyGenParameterSpec para generar la clave.
     */
    private fun generateKey() {
        try {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                KEYSTORE_PROVIDER
            )

            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256) // AES-256
                .build()

            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
            Log.d(TAG, "Clave AES generada exitosamente en Keystore")
        } catch (e: Exception) {
            Log.e(TAG, "Error al generar clave AES", e)
            throw SecurityException("No se pudo generar la clave de cifrado", e)
        }
    }

    /**
     * Obtiene la clave secreta del Keystore.
     */
    private fun getSecretKey(): SecretKey {
        return try {
            val keyEntry = keyStore.getEntry(KEYSTORE_ALIAS, null) as KeyStore.SecretKeyEntry
            keyEntry.secretKey
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener clave del Keystore", e)
            throw SecurityException("No se pudo obtener la clave de cifrado", e)
        }
    }

    /**
     * Cifra un token usando AES/GCM/NoPadding.
     * REQUISITO RFP05: Cifrado con Cipher.ENCRYPT_MODE.
     * 
     * @param token El token JWT a cifrar
     * @return true si se guardó correctamente, false en caso contrario
     */
    fun saveToken(token: String): Boolean {
        return try {
            val secretKey = getSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            // Obtener el IV generado automáticamente
            val iv = cipher.iv

            // Cifrar el token
            val encryptedBytes = cipher.doFinal(token.toByteArray(Charsets.UTF_8))

            // Convertir a Base64 para almacenamiento seguro
            val encryptedDataBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)

            // Guardar en SharedPreferences
            sharedPreferences.edit()
                .putString(KEY_ENCRYPTED_DATA, encryptedDataBase64)
                .putString(KEY_ENCRYPTED_IV, ivBase64)
                .apply()

            Log.d(TAG, "Token cifrado y guardado exitosamente")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error al cifrar y guardar token", e)
            false
        }
    }

    /**
     * Descifra y obtiene el token almacenado.
     * REQUISITO RFP05: Descifrado con Cipher.DECRYPT_MODE.
     * 
     * @return El token JWT descifrado, o null si no existe o hay error
     */
    fun getToken(): String? {
        return try {
            val encryptedDataBase64 = sharedPreferences.getString(KEY_ENCRYPTED_DATA, null)
            val ivBase64 = sharedPreferences.getString(KEY_ENCRYPTED_IV, null)

            if (encryptedDataBase64 == null || ivBase64 == null) {
                Log.d(TAG, "No hay token almacenado")
                return null
            }

            // Decodificar desde Base64
            val encryptedBytes = Base64.decode(encryptedDataBase64, Base64.NO_WRAP)
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)

            // Descifrar
            val secretKey = getSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)

            val decryptedBytes = cipher.doFinal(encryptedBytes)
            val decryptedToken = String(decryptedBytes, Charsets.UTF_8)

            Log.d(TAG, "Token descifrado exitosamente")
            decryptedToken
        } catch (e: Exception) {
            Log.e(TAG, "Error al descifrar token", e)
            null
        }
    }

    /**
     * Elimina el token almacenado de forma segura.
     * REQUISITO RFP05: Eliminación segura del token.
     */
    fun deleteToken() {
        try {
            sharedPreferences.edit()
                .remove(KEY_ENCRYPTED_DATA)
                .remove(KEY_ENCRYPTED_IV)
                .apply()
            Log.d(TAG, "Token eliminado exitosamente")
        } catch (e: Exception) {
            Log.e(TAG, "Error al eliminar token", e)
        }
    }

    /**
     * Verifica si existe un token almacenado.
     */
    fun hasToken(): Boolean {
        return sharedPreferences.contains(KEY_ENCRYPTED_DATA) &&
               sharedPreferences.contains(KEY_ENCRYPTED_IV)
    }
}

