package com.example.app_mosca.ui.theme

import android.Manifest
import android.content.Intent
import com.example.app_mosca.R
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.util.concurrent.ExecutionException
import androidx.appcompat.app.AppCompatActivity


class TomarFoto : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private var imageCapture: ImageCapture? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() // Si el permiso es concedido, iniciar la cámara
            else Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_tomar_foto) // Establece el layout XML de la cámara

        previewView = findViewById(R.id.previewView) // Referencia el PreviewView

        val takePhotoButton: Button = findViewById(R.id.capturar_imagen) // Referencia el botón

        // Verificar si el permiso para acceder a la cámara está concedido
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA) // Solicitar permiso
        } else {
            startCamera() // Iniciar la cámara si el permiso está concedido
        }

        takePhotoButton.setOnClickListener {
            takePhoto() // Llamar a la función para tomar la foto
        }
    }

    private fun startCamera() {
        val camProviderF = ProcessCameraProvider.getInstance(this) // Obtener la instancia del proveedor de cámara
        camProviderF.addListener({
            try {
                val camProvider = camProviderF.get() // Obtener el proveedor de cámara

                val preview = Preview.Builder()
                    .build()
                preview.setSurfaceProvider(previewView.surfaceProvider) // Conectar el PreviewView

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY) // Establece el modo de captura
                    .build()

                val selector = CameraSelector.DEFAULT_BACK_CAMERA // Usar la cámara trasera
                camProvider.unbindAll() // Desvincula cualquier cámara previamente vinculada
                camProvider.bindToLifecycle(
                    this as LifecycleOwner, selector, preview, imageCapture // Vincula la cámara al ciclo de vida de la actividad
                )
            } catch (e: ExecutionException) {
                Toast.makeText(this, "Error al configurar la cámara", Toast.LENGTH_SHORT).show()
            } catch (e: InterruptedException) {
                Toast.makeText(this, "Error al configurar la cámara", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this)) // Ejecutar en el hilo principal
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        // Crear un archivo en el directorio "Pictures"
        val photoFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "${System.currentTimeMillis()}.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // Captura la imagen
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    Toast.makeText(this@TomarFoto, "Foto tomada exitosamente", Toast.LENGTH_SHORT).show()

                    // Redirige a la actividad de carga con la imagen
                    val intent = Intent(this@TomarFoto, LoadingActivity::class.java)
                    intent.putExtra("imageFilePath", photoFile.absolutePath)
                    startActivity(intent)
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@TomarFoto, "Error al tomar la foto: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}
