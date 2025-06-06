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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.app_mosca.R
import com.example.app_mosca.ui.theme.TomarFoto
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class MainActivity: ComponentActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        }

    // Launcher para seleccionar imagen de galería
    private val selectImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { selectedUri ->
            // Copiar la imagen seleccionada a un archivo temporal
            copyImageToTempFile(selectedUri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Verificar permisos
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        // Botón para tomar foto
        val tomarfotoButton: Button = findViewById(R.id.tomarfoto)
        tomarfotoButton.setOnClickListener {
            redirectFoto()
        }

        // Botón para seleccionar de galería
        val galeriaButton: Button = findViewById(R.id.galeria) // Asegúrate de tener este botón en tu XML
        galeriaButton.setOnClickListener {
            selectFromGallery()
        }
    }

    private fun redirectFoto() {
        val intent = Intent(this@MainActivity, TomarFoto::class.java)
        startActivity(intent)
    }

    private fun selectFromGallery() {
        selectImageLauncher.launch("image/*")
    }

    private fun copyImageToTempFile(uri: Uri) {
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            if (inputStream != null) {
                // Crear archivo temporal
                val tempFile = File(cacheDir, "temp_image_${System.currentTimeMillis()}.jpg")
                val outputStream = FileOutputStream(tempFile)

                // Copiar contenido
                inputStream.copyTo(outputStream)
                inputStream.close()
                outputStream.close()

                Log.d("MainActivity", "Imagen copiada a: ${tempFile.absolutePath}")

                // Pasar a LoadingActivity
                val intent = Intent(this, LoadingActivity::class.java)
                intent.putExtra("imagePath", tempFile.absolutePath)
                intent.putExtra("imageUri", uri.toString()) // También pasar la URI original
                startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error copiando imagen: ${e.message}")
            Toast.makeText(this, "Error al procesar la imagen seleccionada", Toast.LENGTH_SHORT).show()
        }
    }
}