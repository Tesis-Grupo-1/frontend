package com.example.app_mosca.ui.theme

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import com.example.app_mosca.R
import com.example.app_mosca.api.apiClient.ApiClient
import com.example.app_mosca.models.DetectionResponse
import com.example.app_mosca.models.UploadResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.io.InputStream
import java.text.SimpleDateFormat

class LoadingActivity : ComponentActivity() {

    companion object {
        private const val TAG = "LoadingActivity"
        private const val INPUT_SIZE = 224
        private const val MODEL_1 = "modelo1_hojas.tflite"
        private const val MODEL_2 = "modelo2_plaga.tflite"
        private const val MODEL_3 = "modelo3_clasificacion.tflite"
        private const val PLAGA_THRESHOLD = 0.5f
        private const val CONFIDENCE_THRESHOLD = 0.5f

        // Intent extras
        const val EXTRA_IMAGE_FILE_PATH = "imageFilePath"
        const val EXTRA_PREDICTION = "prediction"
        const val EXTRA_PROCESSING_TIME = "processingTime"
        const val EXTRA_PLAGA_TYPE = "plagaType"
        const val EXTRA_DETECTED_OBJECT = "detectedObject"
        const val EXTRA_REASON = "reason"
    }

    // UI Components
    private lateinit var progressBar: ProgressBar
    private lateinit var loadingText: TextView

    // Core components
    private lateinit var imageFile: File
    private lateinit var modelManager: ModelManager
    private lateinit var apiManager: ApiManager

    // State
    private var startTime: Long = 0
    private var processingResult = ProcessingResult()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loading)

        initializeComponents()
        processIntentData()
        startImageProcessing()
    }

    private fun initializeComponents() {
        progressBar = findViewById(R.id.progressBar)
        loadingText = findViewById(R.id.loadingText)
        modelManager = ModelManager(this)
        apiManager = ApiManager()
    }

    private fun processIntentData() {
        val imagePath = intent.getStringExtra(MainActivity.EXTRA_IMAGE_PATH)
        val imageUriString = intent.getStringExtra(MainActivity.EXTRA_IMAGE_URI)

        if (imagePath == null) {
            showError("Error: No se recibió la ruta de la imagen.")
            return
        }

        imageFile = File(imagePath)

        if (!imageFile.exists()) {
            showError("Error: El archivo no existe en: $imagePath")
            Log.e(TAG, "Archivo no encontrado: $imagePath")
            return
        }

        val imageUri = Uri.parse(imageUriString)
        Log.d(TAG, "Procesando imagen: $imagePath")
    }

    private fun startImageProcessing() {
        lifecycleScope.launch {
            try {
                startTime = System.currentTimeMillis()
                showProgress("Cargando modelos...")

                modelManager.loadModels()

                val imageUri = Uri.fromFile(imageFile)
                // 1. Llama a la función y obtén el resultado del modelo
                val modelPrediction = processImageWithModels(imageUri)

                // 2. ASIGNA EL RESULTADO a la variable de estado de la actividad (ESTA ES LA CORRECCIÓN)
                processingResult.finalLabel = modelPrediction.finalLabel
                processingResult.confidence = modelPrediction.confidence
                processingResult.plagaType = modelPrediction.plagaType

                // 3. El resto del código ahora usará los valores correctos
                val processingTime = calculateProcessingTime()
                processingResult.processingTime = processingTime

                uploadAndSaveResults(processingTime)

            } catch (e: Exception) {
                Log.e(TAG, "Error durante el procesamiento", e)
                showError("Error durante el procesamiento: ${e.localizedMessage}")
            }
        }
    }

    private suspend fun processImageWithModels(imageUri: Uri): ModelPredictionResult = withContext(Dispatchers.Default) {
        val bitmap = getBitmapFromUri(imageUri)
        val inputBuffer = ImageProcessor.preprocessImage(bitmap, INPUT_SIZE)

        // Step 1: Leaf classification
        showProgress("Analizando imagen...")
        val leafResult = modelManager.predictLeaves(inputBuffer.duplicate())

        Log.d(TAG, "Modelo 1 - Predicción: ${leafResult.label} con confianza ${leafResult.confidence}")

        // Early return if not leaves or low confidence
        if (leafResult.label != "hojas" || leafResult.confidence < CONFIDENCE_THRESHOLD) {
            return@withContext ModelPredictionResult(
                finalLabel = if (leafResult.label == "hojas") "hojas" else "no_hojas",
                confidence = leafResult.confidence,
                plagaType = leafResult.label
            )
        }

        // Step 2: Pest detection
        showProgress("Detectando plaga...")
        val pestResult = modelManager.predictPest(inputBuffer.duplicate())

        Log.d(TAG, "Modelo 2 - Probabilidad de plaga: ${pestResult.confidence} (${(pestResult.confidence * 100).toInt()}%)")

        return@withContext ModelPredictionResult(
            finalLabel = if (pestResult.isPestDetected) "plaga" else "sana",
            confidence = pestResult.confidence,
            plagaType = if (pestResult.isPestDetected) "plaga_detectada" else "no_plaga"
        )
    }

    private suspend fun getBitmapFromUri(uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        } ?: throw IllegalStateException("Cannot decode bitmap from URI: $uri")
    }

    private suspend fun uploadAndSaveResults(processingTimeSeconds: Double) {
        showProgress("Guardando resultados...")

        try {
            val imageId = apiManager.uploadImage(imageFile)
            apiManager.saveDetection(imageId, processingResult, startTime, processingTimeSeconds)

            navigateToResultScreen(processingTimeSeconds)

        } catch (e: Exception) {
            Log.e(TAG, "Error en API, continuando con navegación", e)
            // Continue with navigation even if API fails
            navigateToResultScreen(processingTimeSeconds)
        }
    }

    private fun navigateToResultScreen(processingTimeSeconds: Double) {
        val intent = when (processingResult.finalLabel) {
            "plaga" -> createPlagaEncontradaIntent(processingTimeSeconds)
            else -> createPlagaNoEncontradaIntent(processingTimeSeconds)
        }

        startActivity(intent)
        finish()
    }

    private fun createPlagaEncontradaIntent(processingTimeSeconds: Double): Intent {
        return Intent(this, PlagaEncontrada::class.java).apply {
            putExtra(EXTRA_IMAGE_FILE_PATH, imageFile.absolutePath)
            putExtra(EXTRA_PREDICTION, processingResult.confidence)
            putExtra(EXTRA_PROCESSING_TIME, processingTimeSeconds)
            putExtra(EXTRA_PLAGA_TYPE, processingResult.plagaType)
        }
    }

    private fun createPlagaNoEncontradaIntent(processingTimeSeconds: Double): Intent {
        return Intent(this, PlagaNoEncontrada::class.java).apply {
            putExtra(EXTRA_IMAGE_FILE_PATH, imageFile.absolutePath)
            putExtra(EXTRA_PROCESSING_TIME, processingTimeSeconds)
            putExtra(EXTRA_DETECTED_OBJECT, processingResult.finalLabel)
            putExtra(EXTRA_REASON, getReasonForResult(processingResult.finalLabel, processingResult.plagaType))
        }
    }

    private fun getReasonForResult(finalLabel: String, plagaType: String): String {
        return when (finalLabel) {
            "no_hojas" -> "No se detectaron hojas en la imagen"
            "sana" -> "Hoja sana, sin plaga detectada"
            else -> "Objeto detectado: $plagaType"
        }
    }

    private fun calculateProcessingTime(): Double {
        val endTime = System.currentTimeMillis()
        return (endTime - startTime) / 1000.0
    }

    private suspend fun showProgress(message: String) {
        withContext(Dispatchers.Main) {
            loadingText.text = message
            progressBar.visibility = View.VISIBLE
        }
    }

    private fun showError(message: String) {
        loadingText.text = message
        progressBar.visibility = View.GONE
        Log.e(TAG, message)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::modelManager.isInitialized) {
            modelManager.close()
        }
    }

    // Data classes for better organization
    data class ProcessingResult(
        var finalLabel: String = "",
        var confidence: Float = 0f,
        var plagaType: String = "",
        var processingTime: Double = 0.0
    )

    data class ModelPredictionResult(
        val finalLabel: String,
        val confidence: Float,
        val plagaType: String
    )

    data class LeafPredictionResult(
        val label: String,
        val confidence: Float
    )

    data class PestPredictionResult(
        val confidence: Float,
        val isPestDetected: Boolean = confidence > PLAGA_THRESHOLD
    )
}

// Separate class for model management
class ModelManager(private val activity: LoadingActivity) {
    private lateinit var interpreter1: Interpreter
    private lateinit var interpreter2: Interpreter
    private lateinit var interpreter3: Interpreter

    suspend fun loadModels() = withContext(Dispatchers.IO) {
        interpreter1 = Interpreter(loadModelFile("modelo1_hojas.tflite"))
        interpreter2 = Interpreter(loadModelFile("modelo2_plaga.tflite"))
        interpreter3 = Interpreter(loadModelFile("modelo3_clasificacion.tflite"))
    }

    fun predictLeaves(inputBuffer: ByteBuffer): LoadingActivity.LeafPredictionResult {
        val output = Array(1) { FloatArray(3) } // ['hojas', 'otros', 'animales']
        interpreter1.run(inputBuffer, output)

        val probabilities = output[0]
        val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: -1
        val classLabels = listOf("hojas", "otros", "animales")
        val predictedLabel = if (maxIndex != -1) classLabels[maxIndex] else "desconocido"
        val confidence = if (maxIndex != -1) probabilities[maxIndex] else 0f

        return LoadingActivity.LeafPredictionResult(predictedLabel, confidence)
    }

    fun predictPest(inputBuffer: ByteBuffer): LoadingActivity.PestPredictionResult {
        val output = Array(1) { FloatArray(1) }
        interpreter2.run(inputBuffer, output)

        val plagaConfidence = output[0][0]
        return LoadingActivity.PestPredictionResult(plagaConfidence)
    }

    private fun loadModelFile(modelName: String): ByteBuffer {
        val assetFileDescriptor = activity.assets.openFd(modelName)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun close() {
        if (::interpreter1.isInitialized) interpreter1.close()
        if (::interpreter2.isInitialized) interpreter2.close()
        if (::interpreter3.isInitialized) interpreter3.close()
    }
}

// Separate class for image processing
object ImageProcessor {
    fun preprocessImage(bitmap: Bitmap, inputSize: Int): ByteBuffer {
        val imgData = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        imgData.order(ByteOrder.nativeOrder())

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val intValues = IntArray(inputSize * inputSize)
        scaledBitmap.getPixels(intValues, 0, scaledBitmap.width, 0, 0, scaledBitmap.width, scaledBitmap.height)

        for (pixel in intValues) {
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f
            imgData.putFloat(r)
            imgData.putFloat(g)
            imgData.putFloat(b)
        }

        imgData.rewind()
        return imgData
    }
}

// Separate class for API management
class ApiManager {
    private val apiService = ApiClient.apiService

    suspend fun uploadImage(imageFile: File): Int = withContext(Dispatchers.IO) {
        val requestFile = RequestBody.create("image/jpeg".toMediaType(), imageFile)
        val body = MultipartBody.Part.createFormData("file", imageFile.name, requestFile)

        val response = apiService.uploadImage(body).execute()
        if (response.isSuccessful) {
            response.body()?.id_image ?: throw Exception("No se obtuvo ID de imagen")
        } else {
            throw Exception("Error al subir imagen: ${response.message()}")
        }
    }

    suspend fun saveDetection(
        imageId: Int,
        result: LoadingActivity.ProcessingResult,
        startTime: Long,
        processingTimeSeconds: Double
    ) = withContext(Dispatchers.IO) {
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateDetection = sdfDate.format(Date())

        val sdfTime = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        val timeInitial = sdfTime.format(Date(startTime))
        val timeFinal = sdfTime.format(Date(startTime + (processingTimeSeconds * 1000).toLong()))

        val response = apiService.saveDetectionTime(
            image_id = imageId,
            result = (result.finalLabel == "plaga").toString(),
            prediction_value = result.confidence.toString(),
            time_initial = timeInitial,
            time_final = timeFinal,
            date_detection = dateDetection
        ).execute()

        if (!response.isSuccessful) {
            throw Exception("Error al guardar detección: ${response.message()}")
        }
    }
}
