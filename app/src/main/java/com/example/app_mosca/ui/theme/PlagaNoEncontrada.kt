package com.example.app_mosca.ui.theme

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapFactory.*
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.app_mosca.R
import com.example.app_mosca.ui.theme.PlagaEncontrada.Companion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

class PlagaNoEncontrada : AppCompatActivity() {

    companion object {
        private const val TAG = "PlagaNoEncontrada"
        private const val DEFAULT_PROCESSING_TIME = 0.0
        private const val DEFAULT_PREDICTION = 0.0f
        private const val TIME_FORMAT = "%.2f"
        private const val PREDICTION_FORMAT = "%.3f"

        // Dimensiones para el redimensionado de imagen
        private const val DEFAULT_MAX_WIDTH = 290
        private const val DEFAULT_MAX_HEIGHT = 387

        // Extras del Intent
        const val EXTRA_PROCESSING_TIME = "processingTime"
        const val EXTRA_IMAGE_FILE_PATH = "imageFilePath"
        const val EXTRA_PREDICTION = "prediction"
        const val EXTRA_DETECTED_OBJECT = "detectedObject"
        const val EXTRA_REASON = "reason"

        const val EXTRA_F1_SCORE = "f1Score" // Asegúrate de que esta constante esté definida
        const val EXTRA_ACCURACY = "accuracy"
        const val EXTRA_AUC = "auc"
    }

    // Views
    private lateinit var timeTakenTextView: TextView
    private lateinit var predictionTextView: TextView
    private lateinit var imageView: ImageView
    private lateinit var regresarButton: Button

    private lateinit var metricsButton: Button

    // Datos
    private var processingTime: Double = DEFAULT_PROCESSING_TIME
    private var prediction: Float = DEFAULT_PREDICTION
    private var imagePath: String? = null
    private var detectedObject: String = ""
    private var reason: String = ""

    private var f1Score: Double = 0.0
    private var accuracy: Double = 0.0
    private var auc: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.hide()

        setContentView(R.layout.activity_detection_1)

        initializeViews()
        extractIntentData()
        setupUI()
        setupClickListeners()
        setupBackPressedHandler()
    }

    private fun initializeViews() {
        timeTakenTextView = findViewById(R.id.time_taken)
        predictionTextView = findViewById(R.id.precision)
        imageView = findViewById(R.id.captured_image)
        regresarButton = findViewById(R.id.button_regresar)
        metricsButton = findViewById(R.id.button_metricas)
    }

    private fun extractIntentData() {
        processingTime = intent.getDoubleExtra(EXTRA_PROCESSING_TIME, DEFAULT_PROCESSING_TIME)
        prediction = intent.getFloatExtra(EXTRA_PREDICTION, DEFAULT_PREDICTION)
        imagePath = intent.getStringExtra(EXTRA_IMAGE_FILE_PATH)
        detectedObject = intent.getStringExtra(EXTRA_DETECTED_OBJECT) ?: ""
        reason = intent.getStringExtra(EXTRA_REASON) ?: ""

        f1Score = intent.getDoubleExtra(EXTRA_F1_SCORE, 0.0)
        accuracy = intent.getDoubleExtra(EXTRA_ACCURACY, 0.0)
        auc = intent.getDoubleExtra(EXTRA_AUC, 0.0)

        Log.d(TAG, "Datos recibidos:")
        Log.d(TAG, "  - Processing time: $processingTime")
        Log.d(TAG, "  - Prediction: $prediction")
        Log.d(TAG, "  - Image path: $imagePath")
        Log.d(TAG, "  - Detected object: $detectedObject")
        Log.d(TAG, "  - Reason: $reason")
    }

    private fun setupUI() {
        displayProcessingTime()
        displayPrediction()
        loadAndDisplayImage()
    }

    private fun displayProcessingTime() {
        val formattedTime = TIME_FORMAT.format(processingTime)
        timeTakenTextView.text = getString(R.string.analysis_time_format, formattedTime)
    }

    private fun displayPrediction() {
        val formattedPrediction = PREDICTION_FORMAT.format(prediction)
        val predictionPercentage = (prediction * 100).toInt()

        // Mostrar tanto el valor decimal como el porcentaje para mayor claridad
        predictionTextView.text = "Predicción: $formattedPrediction ($predictionPercentage%)"

        Log.d(TAG, "Predicción mostrada: $formattedPrediction ($predictionPercentage%)")
    }

    private fun loadAndDisplayImage() {
        val path = imagePath

        if (path.isNullOrEmpty()) {
            Log.w(TAG, "Ruta de imagen no proporcionada")
            handleImageLoadError()
            return
        }

        if (!File(path).exists()) {
            Log.e(TAG, "El archivo de imagen no existe: $path")
            handleImageLoadError()
            return
        }

        try {
            loadImageAsync(path)
        } catch (e: Exception) {
            Log.e(TAG, "Error al cargar la imagen", e)
            handleImageLoadError()
        }
    }

    private fun loadImageAsync(imagePath: String) {
        // Ejecutar el procesamiento de imagen en un hilo de fondo
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val processedBitmap = processImage(imagePath)

                // Cambiar al hilo principal para actualizar la UI
                withContext(Dispatchers.Main) {
                    displayProcessedImage(processedBitmap)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al procesar la imagen", e)
                withContext(Dispatchers.Main) {
                    handleImageLoadError()
                }
            }
        }
    }

    private suspend fun processImage(imagePath: String): Bitmap = withContext(Dispatchers.IO) {
        ImageProcessor.resizeAndFixOrientation(
            imagePath = imagePath,
            maxWidth = DEFAULT_MAX_WIDTH,
            maxHeight = DEFAULT_MAX_HEIGHT
        )
    }

    private fun displayProcessedImage(bitmap: Bitmap) {
        Glide.with(this)
            .load(bitmap)
            .placeholder(R.drawable.placeholder_with_background)
            .error(R.drawable.ic_broken_image)
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(imageView)
    }

    private fun handleImageLoadError() {
        Toast.makeText(this, getString(R.string.error_loading_image), Toast.LENGTH_SHORT).show()
        imageView.setImageResource(R.drawable.ic_broken_image)
        imageView.setBackgroundResource(R.drawable.bg_error_state)
    }

    private fun setupClickListeners() {
        regresarButton.setOnClickListener {
            navigateToMainActivity()
        }

        metricsButton.setOnClickListener {
            // Cargar las métricas antes de mostrar el modal
            val metricsDialogFragment = MetricsDialogFragment().apply {
                arguments = Bundle().apply {
                    putDouble(MetricsDialogFragment.EXTRA_F1_SCORE, f1Score)
                    putDouble(MetricsDialogFragment.EXTRA_ACCURACY, accuracy)
                    putDouble(MetricsDialogFragment.EXTRA_AUC, auc)
                }
            }

            // Mostrar el modal
            metricsDialogFragment.show(supportFragmentManager, "metricsDialog")
        }
    }

    private fun setupBackPressedHandler() {
        // Manejo moderno del botón atrás
        onBackPressedDispatcher.addCallback(this) {
            navigateToMainActivity()
        }
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        finish()
    }

    // Método mantenido para compatibilidad, pero se usa OnBackPressedDispatcher
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        // El manejo real se hace en setupBackPressedHandler()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Limpiar referencias para evitar memory leaks
        Log.d(TAG, "Activity destroyed")
    }

    /**
     * Clase utilitaria para procesamiento de imágenes
     * Reutilizada de PlagaEncontrada para evitar duplicación de código
     */
    object ImageProcessor {

        private val BITMAP_CONFIG = Bitmap.Config.RGB_565 // Más eficiente en memoria

        suspend fun resizeAndFixOrientation(
            imagePath: String,
            maxWidth: Int,
            maxHeight: Int
        ): Bitmap = withContext(Dispatchers.IO) {

            val imageFile = File(imagePath)
            if (!imageFile.exists()) {
                throw FileNotFoundException("Imagen no encontrada: $imagePath")
            }

            // Obtener dimensiones originales sin cargar la imagen completa
            val originalDimensions = getImageDimensions(imagePath)

            // Calcular factor de escala
            val scaleFactor = calculateScaleFactor(
                originalDimensions.first,
                originalDimensions.second,
                maxWidth,
                maxHeight
            )

            // Cargar imagen con el factor de escala apropiado
            val bitmap = loadScaledBitmap(imagePath, scaleFactor)

            // Corregir orientación
            return@withContext fixImageOrientation(bitmap, imagePath)
        }

        private fun getImageDimensions(imagePath: String): Pair<Int, Int> {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(imagePath, options)
            return Pair(options.outWidth, options.outHeight)
        }

        private fun calculateScaleFactor(
            originalWidth: Int,
            originalHeight: Int,
            maxWidth: Int,
            maxHeight: Int
        ): Float {
            val widthRatio = maxWidth.toFloat() / originalWidth
            val heightRatio = maxHeight.toFloat() / originalHeight
            return minOf(widthRatio, heightRatio, 1.0f) // No hacer upscale
        }

        private fun loadScaledBitmap(imagePath: String, scaleFactor: Float): Bitmap {
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = BITMAP_CONFIG
                inSampleSize = calculateInSampleSize(scaleFactor)
            }

            return BitmapFactory.decodeFile(imagePath, options)
                ?: throw IllegalStateException("No se pudo decodificar la imagen")
        }

        private fun calculateInSampleSize(scaleFactor: Float): Int {
            return if (scaleFactor < 1.0f) {
                (1.0f / scaleFactor).toInt()
            } else {
                1
            }
        }

        private fun fixImageOrientation(bitmap: Bitmap, imagePath: String): Bitmap {
            return try {
                val exif = ExifInterface(imagePath)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )

                val matrix = createRotationMatrix(orientation)

                if (matrix.isIdentity) {
                    bitmap
                } else {
                    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                        .also {
                            if (it != bitmap) bitmap.recycle() // Liberar memoria del bitmap original
                        }
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo leer la orientación EXIF, usando imagen original", e)
                bitmap
            }
        }

        private fun createRotationMatrix(orientation: Int): Matrix {
            val matrix = Matrix()

            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.postRotate(90f)
                    matrix.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.postRotate(-90f)
                    matrix.postScale(-1f, 1f)
                }
            }

            return matrix
        }
    }
}