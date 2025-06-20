package com.example.app_mosca.ui.theme

import android.Manifest
import android.content.ContentValues
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
import java.io.FileInputStream
import java.io.FileOutputStream

class TomarFoto : ComponentActivity() {

    companion object {
        private const val TAG = "TomarFoto"
        private const val IMAGES_FOLDER = "AppMosca"
        private const val IMAGE_MIME_TYPE = "image/jpeg"
        private const val FILE_EXTENSION = ".jpg"
        private const val FILE_PROVIDER_AUTHORITY = "com.example.app_mosca.fileprovider"

        // Extras para el Intent
        const val EXTRA_IMAGE_URI = "imageUri"
        const val EXTRA_IMAGE_PATH = "imagePath"
    }

    private var currentPhotoUri: Uri? = null
    private var currentPhotoFile: File? = null
    private var tempPhotoFile: File? = null

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
            // Crear archivo temporal para la cámara
            val tempFile = createTempImageFile()
            val photoUri = createPhotoUri(tempFile)

            tempPhotoFile = tempFile
            currentPhotoUri = photoUri

            takePictureLauncher.launch(photoUri)
        } catch (e: Exception) {
            Log.e(TAG, "Error al inicializar captura de foto", e)
            showErrorAndFinish("Error al inicializar la cámara")
        }
    }

    private fun createTempImageFile(): File {
        // Crear archivo temporal en el directorio privado para la cámara
        val tempDirectory = File(cacheDir, "temp_images")
        if (!tempDirectory.exists()) {
            tempDirectory.mkdirs()
        }

        val fileName = generateUniqueFileName()
        return File(tempDirectory, fileName)
    }

    private fun generateUniqueFileName(): String {
        val timestamp = System.currentTimeMillis()
        return "IMG_$timestamp$FILE_EXTENSION"
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
        val tempFile = tempPhotoFile

        if (tempFile == null || !tempFile.exists()) {
            Log.e(TAG, "Archivo temporal es null o no existe")
            showErrorAndFinish("Error interno al procesar la foto")
            return
        }

        Log.d(TAG, "Foto capturada exitosamente: ${tempFile.absolutePath}")

        try {
            // Guardar la imagen en la galería y obtener el archivo final
            val finalFile = saveImageToGallery(tempFile)
            if (finalFile != null) {
                currentPhotoFile = finalFile
                navigateToLoadingActivity(Uri.fromFile(finalFile), finalFile)
            } else {
                showErrorAndFinish("Error al guardar la imagen en la galería")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al guardar imagen en galería", e)
            showErrorAndFinish("Error al guardar la imagen")
        } finally {
            // Limpiar archivo temporal
            tempFile.delete()
        }
    }

    private fun saveImageToGallery(tempFile: File): File? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveImageUsingMediaStore(tempFile)
        } else {
            saveImageToPublicDirectory(tempFile)
        }
    }

    // Para Android 10+ (API 29+)
    private fun saveImageUsingMediaStore(tempFile: File): File? {
        return try {
            val fileName = generateUniqueFileName()

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, IMAGE_MIME_TYPE)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$IMAGES_FOLDER")
            }

            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

            uri?.let { imageUri ->
                contentResolver.openOutputStream(imageUri)?.use { outputStream ->
                    FileInputStream(tempFile).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                // Obtener la ruta real del archivo guardado
                val cursor = contentResolver.query(imageUri, arrayOf(MediaStore.Images.Media.DATA), null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val columnIndex = it.getColumnIndex(MediaStore.Images.Media.DATA)
                        if (columnIndex != -1) {
                            val filePath = it.getString(columnIndex)
                            Log.d(TAG, "Imagen guardada en galería: $filePath")
                            return File(filePath)
                        }
                    }
                }

                // Si no podemos obtener la ruta, crear una copia en directorio privado
                return createPrivateCopy(tempFile)
            }

            null
        } catch (e: Exception) {
            Log.e(TAG, "Error al guardar con MediaStore", e)
            createPrivateCopy(tempFile)
        }
    }

    // Para Android 9 y anteriores
    private fun saveImageToPublicDirectory(tempFile: File): File? {
        return try {
            val picturesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), IMAGES_FOLDER)

            if (!picturesDir.exists()) {
                picturesDir.mkdirs()
            }

            val fileName = generateUniqueFileName()
            val finalFile = File(picturesDir, fileName)

            // Copiar archivo temporal al directorio público
            FileInputStream(tempFile).use { input ->
                FileOutputStream(finalFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Notificar al MediaScanner para que aparezca en la galería
            MediaScannerConnection.scanFile(
                this,
                arrayOf(finalFile.absolutePath),
                arrayOf(IMAGE_MIME_TYPE)
            ) { path, uri ->
                Log.d(TAG, "Imagen escaneada y agregada a galería: $path")
            }

            Log.d(TAG, "Imagen guardada en directorio público: ${finalFile.absolutePath}")
            finalFile

        } catch (e: Exception) {
            Log.e(TAG, "Error al guardar en directorio público", e)
            createPrivateCopy(tempFile)
        }
    }

    // Crear copia en directorio privado como respaldo
    private fun createPrivateCopy(tempFile: File): File? {
        return try {
            val privateDir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), IMAGES_FOLDER)
            if (!privateDir.exists()) {
                privateDir.mkdirs()
            }

            val fileName = generateUniqueFileName()
            val privateFile = File(privateDir, fileName)

            FileInputStream(tempFile).use { input ->
                FileOutputStream(privateFile).use { output ->
                    input.copyTo(output)
                }
            }

            Log.d(TAG, "Imagen guardada en directorio privado: ${privateFile.absolutePath}")
            privateFile

        } catch (e: Exception) {
            Log.e(TAG, "Error al crear copia privada", e)
            null
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
        // Limpiar archivo temporal si existe
        tempPhotoFile?.let { file ->
            if (file.exists()) {
                file.delete()
            }
        }

        // Limpiar referencias para evitar memory leaks
        currentPhotoFile = null
        currentPhotoUri = null
        tempPhotoFile = null
    }
}