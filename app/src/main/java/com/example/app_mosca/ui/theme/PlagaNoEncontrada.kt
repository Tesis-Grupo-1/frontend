package com.example.app_mosca.ui.theme

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import androidx.activity.ComponentActivity
import com.bumptech.glide.Glide
import com.example.app_mosca.R
import java.io.File

class PlagaNoEncontrada: ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detection_1)

        val imageView = findViewById<ImageView>(R.id.captured_image)
        val regresarButton = findViewById<Button>(R.id.button_regresar)

        // Obtener la ruta o URI de la imagen pasada en el Intent
        val imagePath = intent.getStringExtra("imageFilePath")

        if (imagePath != null) {
            // Cargar la imagen usando Glide o similar
            Glide.with(this)
                .load(File(imagePath))
                .into(imageView)
        }

        regresarButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }
    }

    @Suppress("MissingSuperCall")
    override fun onBackPressed() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }
}