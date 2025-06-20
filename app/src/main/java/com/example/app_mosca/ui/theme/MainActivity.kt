package com.example.app_mosca.ui.theme

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.app_mosca.R
import com.example.app_mosca.ui.theme.TomarFoto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class MainActivity : ComponentActivity() {

    private lateinit var permissionLauncher: ActivityResultLauncher<String>
    private lateinit var galleryLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeLaunchers()
        requestCameraPermissionIfNeeded()
        setupClickListeners()
    }

    private fun initializeLaunchers() {
        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            handlePermissionResult(isGranted)
        }

        galleryLauncher = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            handleGalleryResult(uri)
        }
    }

    private fun requestCameraPermissionIfNeeded() {
        if (!isCameraPermissionGranted()) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun setupClickListeners() {
        findViewById<android.widget.Button>(R.id.tomarfoto).setOnClickListener {
            navigateToCamera()
        }

        findViewById<android.widget.Button>(R.id.galeria).setOnClickListener {
            openGallery()
        }
    }

    private fun isCameraPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun handlePermissionResult(isGranted: Boolean) {
        if (!isGranted) {
            showToast(getString(R.string.camera_permission_required))
            Log.w(TAG, "Camera permission denied by user")
        }
    }

    private fun navigateToCamera() {
        try {
            val intent = Intent(this, TomarFoto::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to camera", e)
            showToast(getString(R.string.error_opening_camera))
        }
    }

    private fun openGallery() {
        try {
            galleryLauncher.launch(IMAGE_MIME_TYPE)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening gallery", e)
            showToast(getString(R.string.error_opening_gallery))
        }
    }

    private fun handleGalleryResult(uri: Uri?) {
        uri?.let { selectedUri ->
            Log.d(TAG, "Image selected: $selectedUri")
            processSelectedImage(selectedUri)
        } ?: run {
            Log.d(TAG, "No image selected")
            showToast(getString(R.string.no_image_selected))
        }
    }

    private fun processSelectedImage(uri: Uri) {
        // Use coroutines for background processing
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tempFile = copyImageToTempFile(uri)
                withContext(Dispatchers.Main) {
                    navigateToLoadingActivity(tempFile)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing selected image", e)
                withContext(Dispatchers.Main) {
                    showToast(getString(R.string.error_processing_image, e.localizedMessage))
                }
            }
        }
    }

    private suspend fun copyImageToTempFile(uri: Uri): File = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting image copy from URI: $uri")

        val inputStream = contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open input stream for URI: $uri")

        val tempFile = createTempFile()
        Log.d(TAG, "Created temporary file: ${tempFile.absolutePath}")

        inputStream.use { input ->
            FileOutputStream(tempFile).use { output ->
                copyStream(input, output)
            }
        }

        validateTempFile(tempFile)
        Log.d(TAG, "Image successfully copied to: ${tempFile.absolutePath}, size: ${tempFile.length()} bytes")

        tempFile
    }

    private fun createTempFile(): File {
        val timeStamp = System.currentTimeMillis()
        return File(cacheDir, "$TEMP_FILE_PREFIX$timeStamp$TEMP_FILE_EXTENSION")
    }

    private fun copyStream(input: InputStream, output: FileOutputStream) {
        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
        }
    }

    private fun validateTempFile(tempFile: File) {
        if (!tempFile.exists() || tempFile.length() == 0L) {
            throw IllegalStateException("Temporary file was not created correctly")
        }
    }

    private fun navigateToLoadingActivity(tempFile: File) {
        try {
            val fileUri = createFileUri(tempFile)
            Log.d(TAG, "Temporary file URI: $fileUri")

            val intent = createLoadingIntent(tempFile, fileUri)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to LoadingActivity", e)
            showToast(getString(R.string.error_processing_image, e.localizedMessage))
        }
    }

    private fun createFileUri(tempFile: File): Uri {
        return FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            tempFile
        )
    }

    private fun createLoadingIntent(tempFile: File, fileUri: Uri): Intent {
        return Intent(this, LoadingActivity::class.java).apply {
            putExtra(EXTRA_IMAGE_PATH, tempFile.absolutePath)
            putExtra(EXTRA_IMAGE_URI, fileUri.toString())
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val IMAGE_MIME_TYPE = "image/*"
        private const val TEMP_FILE_PREFIX = "temp_gallery_image_"
        private const val TEMP_FILE_EXTENSION = ".jpg"
        private const val BUFFER_SIZE = 8192 // Increased buffer size for better performance

        // Constants for intent extras
        const val EXTRA_IMAGE_PATH = "imagePath"
        const val EXTRA_IMAGE_URI = "imageUri"
    }
}