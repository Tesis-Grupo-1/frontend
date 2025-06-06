package com.example.app_mosca.ui.theme

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapFactory.*
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.bumptech.glide.Glide
import com.example.app_mosca.R
import java.io.File

class PlagaNoEncontrada: ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detection_1)

        val processingTime = intent.getDoubleExtra("processingTime", 0.0)
        val timeTakenTextView = findViewById<TextView>(R.id.time_taken)
        timeTakenTextView.text = "Tiempo de análisis: ${"%.2f".format(processingTime)}s"

        val imageView = findViewById<ImageView>(R.id.captured_image)
        val regresarButton = findViewById<Button>(R.id.button_regresar)

        val imagePath = intent.getStringExtra("imageFilePath")

        if (imagePath != null) {
            // Corregir la orientación de la imagen antes de redimensionarla
            val correctedBitmap = resizeAndFixOrientation(imagePath, 290, 387)

            // Usar Glide para cargar la imagen corregida y redimensionada en el ImageView
            Glide.with(this)
                .load(correctedBitmap)
                .into(imageView)
        }

        regresarButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }
    }

    fun resizeAndFixOrientation(imagePath: String, maxWidth: Int, maxHeight: Int): Bitmap {
        // Paso 1: Obtener las dimensiones originales de la imagen
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(imagePath, options)

        val originalWidth = options.outWidth
        val originalHeight = options.outHeight

        // Calcular el factor de escala basado en el tamaño máximo (maxWidth y maxHeight)
        val widthRatio = maxWidth.toFloat() / originalWidth
        val heightRatio = maxHeight.toFloat() / originalHeight

        // Elegir el factor de escala más pequeño para mantener la relación de aspecto
        val scaleFactor = minOf(widthRatio, heightRatio)

        // Calcular las nuevas dimensiones de la imagen manteniendo la relación de aspecto
        val newWidth = (originalWidth * scaleFactor).toInt()
        val newHeight = (originalHeight * scaleFactor).toInt()

        // Paso 2: Corregir la orientación de la imagen si es necesario
        val exif = ExifInterface(imagePath)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

        var bitmap = BitmapFactory.decodeFile(imagePath)
        val matrix = Matrix()

        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        }

        // Crear la imagen corregida de la orientación
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

        // Paso 3: Redimensionar la imagen a las nuevas dimensiones calculadas
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, false)
    }


    @Suppress("MissingSuperCall")
    override fun onBackPressed() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }
}