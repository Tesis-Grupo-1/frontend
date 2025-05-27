package com.example.app_mosca.ui.theme

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.bumptech.glide.Glide
import com.example.app_mosca.R
import java.io.File

class PlagaEncontrada: ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detection_2)

        val processingTime = intent.getDoubleExtra("processingTime", 0.0)
        val timeTakenTextView = findViewById<TextView>(R.id.time_taken)
        timeTakenTextView.text = "Tiempo de análisis: ${"%.2f".format(processingTime)}s"

        val imageView = findViewById<ImageView>(R.id.captured_image)
        val precisionTextView = findViewById<TextView>(R.id.tvPrecision)
        val regresarButton = findViewById<Button>(R.id.button_regresar)

        val imagePath = intent.getStringExtra("imageFilePath")
        val precision = intent.getDoubleExtra("prediction", 0.0)

        if (imagePath != null) {
            Glide.with(this)
                .load(File(imagePath))
                .into(imageView)
        }

        // Mostrar la precisión en porcentaje con dos decimales
        precisionTextView.text = "¡Probable presencia!\nPrecisión: ${"%.2f".format(precision * 100)}%"

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