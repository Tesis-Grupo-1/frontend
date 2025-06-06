package com.example.app_mosca.ui.theme

import android.Manifest
import android.content.Intent
import com.example.app_mosca.R
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
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
import androidx.core.content.FileProvider
import java.io.File



class TomarFoto : ComponentActivity() {

    private lateinit var currentPhotoUri: Uri // Variable global para almacenar la URI de la foto tomada
    private lateinit var currentPhotoFile: File

    // Definir el ActivityResultLauncher para capturar la imagen
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
        if (isSuccess) {
            Log.d("TomarFoto", "Ruta del archivo: ${currentPhotoFile.absolutePath}")

            if (currentPhotoFile.exists()) {

                MediaScannerConnection.scanFile(
                    this,
                    arrayOf(currentPhotoFile.absolutePath),
                    arrayOf("image/jpeg")
                ) { path, uri ->
                    Log.d("TomarFoto", "Imagen agregada a la galería: $path")
                }

                val intent = Intent(this, LoadingActivity::class.java)
                // Pasar tanto la URI como la ruta del archivo
                intent.putExtra("imageUri", currentPhotoUri.toString())
                intent.putExtra("imagePath", currentPhotoFile.absolutePath) // Agregar la ruta
                startActivity(intent)
            } else {
                Toast.makeText(this, "Error: La foto no se ha guardado correctamente", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Error al tomar la foto", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val picturesDirectory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "images")

        if (!picturesDirectory.exists()) {
            val isCreated = picturesDirectory.mkdirs()
            if (isCreated) {
                Log.d("TomarFoto", "Carpeta creada en: ${picturesDirectory.absolutePath}")
            } else {
                Log.d("TomarFoto", "No se pudo crear la carpeta: ${picturesDirectory.absolutePath}")
            }
        }

        // Crear el archivo y guardarlo en variable de clase
        currentPhotoFile = File(picturesDirectory, "${System.currentTimeMillis()}.jpg")

        currentPhotoUri = FileProvider.getUriForFile(
            this,
            "com.example.app_mosca.fileprovider",
            currentPhotoFile // Usar la variable de clase
        )

        takePictureLauncher.launch(currentPhotoUri)
    }
}