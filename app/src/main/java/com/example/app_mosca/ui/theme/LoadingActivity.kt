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
import com.example.app_mosca.models.Detection
import com.example.app_mosca.models.DetectionResponse
import com.example.app_mosca.models.MetricsManager
import com.example.app_mosca.models.PestDetectionResponse
import com.example.app_mosca.models.UploadResponse
import com.example.app_mosca.utils.ModelMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.GlobalScope
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
        const val EXTRA_F1_SCORE = "f1score"
        const val EXTRA_PROCESSING_TIME = "processingTime"
        const val EXTRA_PLAGA_TYPE = "plagaType"
        const val EXTRA_DETECTED_OBJECT = "detectedObject"
        const val EXTRA_REASON = "reason"
        const val EXTRA_PROCESSED_IMAGE_PATH = "processedImagePath" // Nueva constante para imagen procesada
        const val EXTRA_API_DETECTIONS = "apiDetections" // Nueva constante para detecciones de la API
        const val EXTRA_IMAGE_DIMENSIONS = "imageDimensions" // Nueva constante para dimensiones
    }

    // UI Components
    private lateinit var progressBar: ProgressBar
    private lateinit var loadingText: TextView

    // Core components
    private lateinit var imageFile: File
    private lateinit var modelManager: ModelManager
    private lateinit var apiManager: ApiManager
    private lateinit var metricsManager: MetricsManager

    // State
    private var startTime: Long = 0
    private var processingResult = ProcessingResult()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loading)

        metricsManager = MetricsManager(this)

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
            finish()
            return
        }

        imageFile = File(imagePath)

        if (!validateStoredImage(imageFile)) {
            finish()
            return
        }

        val imageUri = Uri.parse(imageUriString)
        Log.d(TAG, "Imagen validada correctamente: $imagePath")
    }

    private fun validateStoredImage(imageFile: File): Boolean {
        try {
            if (!imageFile.exists()) {
                showError("Error: El archivo de imagen no existe en: ${imageFile.absolutePath}")
                Log.e(TAG, "Archivo no encontrado: ${imageFile.absolutePath}")
                return false
            }

            if (!imageFile.canRead()) {
                showError("Error: No se puede leer el archivo de imagen.")
                Log.e(TAG, "Archivo no legible: ${imageFile.absolutePath}")
                return false
            }

            if (imageFile.length() == 0L) {
                showError("Error: El archivo de imagen está vacío.")
                Log.e(TAG, "Archivo vacío: ${imageFile.absolutePath}")
                return false
            }

            val fileSizeKB = imageFile.length() / 1024
            if (fileSizeKB < 1) {
                showError("Error: El archivo de imagen es demasiado pequeño.")
                Log.e(TAG, "Archivo muy pequeño: ${fileSizeKB}KB")
                return false
            }
            if (fileSizeKB > 10240) { // 10MB máximo
                showError("Error: El archivo de imagen es demasiado grande (máximo 10MB).")
                Log.e(TAG, "Archivo muy grande: ${fileSizeKB}KB")
                return false
            }

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            BitmapFactory.decodeFile(imageFile.absolutePath, options)

            if (options.outWidth <= 0 || options.outHeight <= 0) {
                showError("Error: El archivo no es una imagen válida o está corrupto.")
                Log.e(TAG, "Imagen corrupta o inválida: ${imageFile.absolutePath}")
                return false
            }

            if (options.outWidth < 32 || options.outHeight < 32) {
                showError("Error: La imagen es demasiado pequeña (mínimo 32x32 píxeles).")
                Log.e(TAG, "Imagen muy pequeña: ${options.outWidth}x${options.outHeight}")
                return false
            }

            val mimeType = options.outMimeType
            if (mimeType != null && !isValidImageMimeType(mimeType)) {
                showError("Error: Formato de imagen no soportado: $mimeType")
                Log.e(TAG, "Formato no soportado: $mimeType")
                return false
            }

            Log.d(TAG, "✅ Imagen validada correctamente:")
            Log.d(TAG, "  - Ruta: ${imageFile.absolutePath}")
            Log.d(TAG, "  - Tamaño: ${fileSizeKB}KB")
            Log.d(TAG, "  - Dimensiones: ${options.outWidth}x${options.outHeight}")
            Log.d(TAG, "  - Tipo MIME: $mimeType")

            return true

        } catch (e: Exception) {
            showError("Error al validar la imagen: ${e.localizedMessage}")
            Log.e(TAG, "Error durante validación de imagen", e)
            return false
        }
    }

    private fun isValidImageMimeType(mimeType: String): Boolean {
        val supportedTypes = listOf(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp",
            "image/gif",
            "image/bmp"
        )
        return supportedTypes.contains(mimeType.lowercase())
    }

    private fun startImageProcessing() {
        lifecycleScope.launch {
            try {
                startTime = System.currentTimeMillis()
                showProgress("Cargando modelos...")

                modelManager.loadModels()

                val imageUri = Uri.fromFile(imageFile)
                // 1. Procesamiento de la imagen con modelos
                val modelPrediction = processImageWithModels(imageUri)

                // 2. Asignar resultados
                processingResult.finalLabel = modelPrediction.finalLabel
                processingResult.confidence = modelPrediction.confidence
                processingResult.plagaType = modelPrediction.plagaType

                // 3. Calcular tiempo de procesamiento
                val processingTime = calculateProcessingTime()
                processingResult.processingTime = processingTime

                val metrics = metricsManager.getMetricsForResult(processingResult.finalLabel)
                Log.d(TAG, "Métricas obtenidas para '${processingResult.finalLabel}': ${metrics.toDisplayString()}")

                // 4. Solo si se detecta plaga, enviar a la API de detección
                if (processingResult.finalLabel == "plaga") {
                    showProgress("Procesando imagen con IA...")
                    val processedImagePath = sendImageToPestDetectionAPI()
                    processingResult.processedImagePath = processedImagePath
                    Log.d(TAG, "Imagen procesada por API de detección: $processedImagePath")
                }

                // 5. Navegar a la pantalla de resultados
                navigateToResultScreen(processingTime, metrics)

                // 6. Subir datos de forma asíncrona en segundo plano
                uploadDataInBackground(processingTime)

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

    // NUEVO MÉTODO: Enviar imagen a la API de detección de plagas
    private suspend fun sendImageToPestDetectionAPI(): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Enviando imagen a API de detección de plagas...")

            val apiResponse = apiManager.detectPests(imageFile)

            if (apiResponse.success) {
                Log.d(TAG, "API Response - Message: ${apiResponse.message}")
                Log.d(TAG, "Detecciones encontradas: ${apiResponse.detections?.size ?: 0}")

                // Guardar información de detecciones
                processingResult.apiDetections = apiResponse.detections
                processingResult.imageWidth = apiResponse.image_width
                processingResult.imageHeight = apiResponse.image_height

                // Guardar imagen procesada desde base64
                val processedImagePath = saveBase64Image(apiResponse.processed_image_base64)

                Log.d(TAG, "Imagen procesada guardada en: $processedImagePath")
                return@withContext processedImagePath
            } else {
                Log.w(TAG, "API no detectó plagas: ${apiResponse.message}")
                return@withContext null
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error al enviar imagen a API de detección", e)
            // Si falla la API, continuamos sin la imagen procesada
            return@withContext null
        }
    }

    // NUEVO MÉTODO: Guardar imagen base64 como archivo
    private suspend fun saveBase64Image(base64String: String): String = withContext(Dispatchers.IO) {
        try {
            // Decodificar base64
            val imageBytes = android.util.Base64.decode(base64String, android.util.Base64.DEFAULT)

            // Crear archivo para la imagen procesada
            val processedImageFile = File(cacheDir, "processed_${System.currentTimeMillis()}.jpg")

            // Escribir bytes al archivo
            processedImageFile.writeBytes(imageBytes)

            Log.d(TAG, "Imagen base64 guardada como: ${processedImageFile.absolutePath}")
            return@withContext processedImageFile.absolutePath

        } catch (e: Exception) {
            Log.e(TAG, "Error al guardar imagen base64", e)
            throw e
        }
    }

    private suspend fun getBitmapFromUri(uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)

                if (bitmap == null) {
                    throw IllegalStateException("No se pudo decodificar la imagen desde URI: $uri")
                }

                if (bitmap.isRecycled) {
                    throw IllegalStateException("La imagen está corrupta o reciclada")
                }

                Log.d(TAG, "✅ Bitmap cargado exitosamente: ${bitmap.width}x${bitmap.height}")
                bitmap

            } ?: throw IllegalStateException("No se pudo abrir el stream de la imagen: $uri")

        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Error de memoria al cargar imagen", e)
            throw IllegalStateException("La imagen es demasiado grande para procesar")
        } catch (e: Exception) {
            Log.e(TAG, "Error al cargar imagen desde URI", e)
            throw IllegalStateException("Error al cargar la imagen: ${e.localizedMessage}")
        }
    }

    private fun uploadDataInBackground(processingTimeSeconds: Double) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Iniciando subida de datos en segundo plano...")

                // Subir imagen
                val imageId = apiManager.uploadImage(imageFile)
                Log.d(TAG, "Imagen subida exitosamente. ID: $imageId")

                // Guardar detección
                apiManager.saveDetection(imageId, processingResult, startTime, processingTimeSeconds)
                Log.d(TAG, "Detección guardada exitosamente")

            } catch (e: Exception) {
                Log.e(TAG, "Error durante la subida de datos en segundo plano", e)
            }
        }
    }

    private fun navigateToResultScreen(processingTimeSeconds: Double, metrics: ModelMetrics) {
        val intent = when (processingResult.finalLabel) {
            "plaga" -> createPlagaEncontradaIntent(processingTimeSeconds, metrics)
            else -> createPlagaNoEncontradaIntent(processingTimeSeconds, metrics)
        }

        startActivity(intent)
        finish()
    }

    private fun createPlagaEncontradaIntent(processingTimeSeconds: Double, metrics: ModelMetrics): Intent {
        return Intent(this, PlagaEncontrada::class.java).apply {
            putExtra(PlagaEncontrada.EXTRA_IMAGE_FILE_PATH, imageFile.absolutePath)
            putExtra(PlagaEncontrada.EXTRA_PREDICTION, processingResult.confidence)
            putExtra(PlagaEncontrada.EXTRA_PROCESSING_TIME, processingTimeSeconds)
            putExtra(PlagaEncontrada.EXTRA_F1_SCORE, (metrics.f1Score * 100).toDouble())
            putExtra(PlagaEncontrada.EXTRA_ACCURACY, (metrics.accuracy * 100).toDouble())
            putExtra(PlagaEncontrada.EXTRA_AUC, (metrics.auc * 100).toDouble())

            // CORREGIDO: Usar la constante correcta de PlagaEncontrada
            processingResult.processedImagePath?.let { processedPath ->
                putExtra(PlagaEncontrada.EXTRA_PROCESSED_IMAGE_PATH, processedPath)
                Log.d(TAG, "Enviando imagen procesada: $processedPath")
            }

            // Enviar información de detecciones de la API
            processingResult.apiDetections?.let { detections ->

                putExtra(EXTRA_IMAGE_DIMENSIONS, intArrayOf(
                    processingResult.imageWidth ?: 0,
                    processingResult.imageHeight ?: 0
                ))
                Log.d(TAG, "Enviando ${detections.size} detecciones de la API")
            }

            Log.d(TAG, "Enviando a PlagaEncontrada - Prediction: ${processingResult.confidence}, Processing Time: $processingTimeSeconds")
        }
    }

    private fun createPlagaNoEncontradaIntent(processingTimeSeconds: Double, metrics: ModelMetrics): Intent {
        return Intent(this, PlagaNoEncontrada::class.java).apply {
            putExtra(EXTRA_IMAGE_FILE_PATH, imageFile.absolutePath)
            putExtra(EXTRA_PROCESSING_TIME, processingTimeSeconds)
            val finalPrediction = when (processingResult.finalLabel) {
                "no_hojas" -> {
                    processingResult.confidence
                }
                "sana" -> {
                    1.0f - processingResult.confidence
                }
                else -> processingResult.confidence
            }
            putExtra(PlagaNoEncontrada.EXTRA_PREDICTION, finalPrediction)
            putExtra(PlagaNoEncontrada.EXTRA_F1_SCORE, (metrics.f1Score * 100).toDouble())
            putExtra(PlagaNoEncontrada.EXTRA_ACCURACY, (metrics.accuracy * 100).toDouble())
            putExtra(PlagaNoEncontrada.EXTRA_AUC, (metrics.auc * 100).toDouble())
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
        var processingTime: Double = 0.0,
        var processedImagePath: String? = null, // NUEVO: Ruta de imagen procesada
        var apiDetections: List<Detection>? = null, // NUEVO: Detecciones de la API
        var imageWidth: Int? = null, // NUEVO: Ancho de imagen
        var imageHeight: Int? = null // NUEVO: Alto de imagen
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

// CLASE OPTIMIZADA: ApiManager con nuevo método para detección de plagas
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

    // NUEVO MÉTODO: Detectar plagas usando la API
    suspend fun detectPests(imageFile: File): PestDetectionResponse = withContext(Dispatchers.IO) {
        val requestFile = RequestBody.create("image/jpeg".toMediaType(), imageFile)
        val body = MultipartBody.Part.createFormData("file", imageFile.name, requestFile)
        val returnImageBody = RequestBody.create("text/plain".toMediaType(), "true")

        val response = apiService.detectPests(body, returnImageBody).execute()
        if (response.isSuccessful) {
            response.body() ?: throw Exception("Respuesta vacía de la API")
        } else {
            throw Exception("Error al detectar plagas: ${response.message()}")
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