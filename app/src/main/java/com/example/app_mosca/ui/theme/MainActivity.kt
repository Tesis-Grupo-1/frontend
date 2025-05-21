package com.example.app_mosca.ui.theme

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.app_mosca.R
import com.example.app_mosca.ui.theme.TomarFoto

class MainActivity: ComponentActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // Establece el layout XML


        // Verificar si el permiso para acceder a la cámara está concedido
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA) // Solicitar permiso
        }

        val tomarfotoButton: Button = findViewById(R.id.tomarfoto) // Referencia el botón



        tomarfotoButton.setOnClickListener {
            redirectFoto() // Llamar a la función para tomar la foto
        }
    }


    private fun redirectFoto() {

        val intent = Intent(this@MainActivity, TomarFoto::class.java)
        startActivity(intent)
    }
}