package com.example.app_mosca.ui.theme

import android.Manifest
import android.content.Intent
import com.example.app_mosca.R
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File



class TomarFoto : ComponentActivity() {

    companion object {
        private const val TAG = "TomarFoto"
        private const val IMAGES_FOLDER = "images"
        private const val IMAGE_MIME_TYPE = "image/jpeg"
        private const val FILE_EXTENSION = ".jpg"
        private const val FILE_PROVIDER_AUTHORITY = "com.example.app_mosca.fileprovider"

        // Extras para el Intent
        const val EXTRA_IMAGE_URI = "imageUri"
        const val EXTRA_IMAGE_PATH = "imagePath"
    }

    private var currentPhotoUri: Uri? = null
    private var currentPhotoFile: File? = null

    // Usar lazy para inicializar el launcher
    private val takePictureLauncher by lazy {
        registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
            handleCameraResult(isSuccess)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (hasRequiredPermissions()) {
            initializePhotoCapture()
        } else {
            showErrorAndFinish("Permisos de cámara requeridos")
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun initializePhotoCapture() {
        try {
            val photoFile = createImageFile()
            val photoUri = createPhotoUri(photoFile)

            currentPhotoFile = photoFile
            currentPhotoUri = photoUri

            takePictureLauncher.launch(photoUri)
        } catch (e: Exception) {
            Log.e(TAG, "Error al inicializar captura de foto", e)
            showErrorAndFinish("Error al inicializar la cámara")
        }
    }

    private fun createImageFile(): File {
        val picturesDirectory = getPicturesDirectory()
        ensureDirectoryExists(picturesDirectory)

        val fileName = generateUniqueFileName()
        return File(picturesDirectory, fileName)
    }

    private fun getPicturesDirectory(): File {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Para Android 10+ usar directorio privado de la app
            File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), IMAGES_FOLDER)
        } else {
            // Para versiones anteriores usar directorio público
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), IMAGES_FOLDER)
        }
    }

    private fun ensureDirectoryExists(directory: File) {
        if (!directory.exists()) {
            val isCreated = directory.mkdirs()
            if (isCreated) {
                Log.d(TAG, "Directorio creado: ${directory.absolutePath}")
            } else {
                Log.w(TAG, "No se pudo crear el directorio: ${directory.absolutePath}")
                throw IllegalStateException("No se pudo crear el directorio de imágenes")
            }
        }
    }

    private fun generateUniqueFileName(): String {
        val timestamp = System.currentTimeMillis()
        return "$timestamp$FILE_EXTENSION"
    }

    private fun createPhotoUri(photoFile: File): Uri {
        return FileProvider.getUriForFile(
            this,
            FILE_PROVIDER_AUTHORITY,
            photoFile
        )
    }

    private fun handleCameraResult(isSuccess: Boolean) {
        if (isSuccess) {
            handleSuccessfulCapture()
        } else {
            handleCaptureError()
        }
    }

    private fun handleSuccessfulCapture() {
        val photoFile = currentPhotoFile
        val photoUri = currentPhotoUri

        if (photoFile == null || photoUri == null) {
            Log.e(TAG, "Archivo o URI de foto es null")
            showErrorAndFinish("Error interno al procesar la foto")
            return
        }

        Log.d(TAG, "Foto capturada exitosamente: ${photoFile.absolutePath}")

        if (photoFile.exists()) {
            addImageToGallery(photoFile)
            navigateToLoadingActivity(photoUri, photoFile)
        } else {
            Log.e(TAG, "El archivo de foto no existe: ${photoFile.absolutePath}")
            showErrorAndFinish("La foto no se guardó correctamente")
        }
    }

    private fun addImageToGallery(photoFile: File) {
        MediaScannerConnection.scanFile(
            this,
            arrayOf(photoFile.absolutePath),
            arrayOf(IMAGE_MIME_TYPE)
        ) { path, uri ->
            Log.d(TAG, "Imagen agregada a la galería: $path")
        }
    }

    private fun navigateToLoadingActivity(photoUri: Uri, photoFile: File) {
        val intent = Intent(this, LoadingActivity::class.java).apply {
            putExtra(EXTRA_IMAGE_URI, photoUri.toString())
            putExtra(EXTRA_IMAGE_PATH, photoFile.absolutePath)
        }
        startActivity(intent)
        finish() // Finalizar esta actividad
    }

    private fun handleCaptureError() {
        Log.w(TAG, "Error al capturar la foto")
        showErrorAndFinish("Error al tomar la foto")
    }

    private fun showErrorAndFinish(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Limpiar referencias para evitar memory leaks
        currentPhotoFile = null
        currentPhotoUri = null
    }
}