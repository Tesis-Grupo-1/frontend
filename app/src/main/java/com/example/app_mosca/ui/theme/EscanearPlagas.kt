package com.example.app_mosca.ui.theme

import com.example.app_mosca.R
import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.app_mosca.api.apiClient.ApiClient
import com.example.app_mosca.repositories.FieldRepository
import com.example.app_mosca.utils.TokenManager
import com.google.common.util.concurrent.ListenableFuture
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class EscanearPlagas : ComponentActivity() {

    companion object {
        private const val TAG = "EscanearPlagas"
        private const val IMAGES_FOLDER = "AppMosca/Plagas"
        private const val IMAGE_MIME_TYPE = "image/jpeg"
        private const val FILE_EXTENSION = ".jpg"
        private const val FRAME_ANALYSIS_INTERVAL_MS = 1000L
        private const val MIN_CONFIDENCE_THRESHOLD = 0.5f
        private const val PLAGA_THRESHOLD = 0.5f
        private const val INPUT_SIZE = 224
        private const val MODEL_1 = "modelo1_hojas.tflite"
        private const val MODEL_2 = "modelo2_plaga.tflite"

        const val EXTRA_DETECTED_IMAGES_PATHS = "detectedImagesPaths"
        const val EXTRA_TOTAL_FRAMES_ANALYZED = "totalFramesAnalyzed"
        const val EXTRA_PLAGAS_DETECTED = "plagasDetected"
        const val EXTRA_PROCESSING_TIMES = "processingTimes"
        const val EXTRA_CONFIDENCES = "confidences"
        const val EXTRA_SCAN_DURATION = "scanDuration"
    }

    // UI Components
    private lateinit var previewView: PreviewView
    private lateinit var btnIniciarEscaneo: Button
    private lateinit var btnDetenerEscaneo: Button
    private lateinit var btnFinalizarEscaneo: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEstadoEscaneo: TextView
    private lateinit var tvPlagasDetectadas: TextView
    private lateinit var tvFramesAnalizados: TextView

    private var scanStartTimeFormatted = ""

    // Camera
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var preview: Preview? = null
    private var cameraExecutor: ExecutorService? = null

    // ML Models
    private var interpreter1: Interpreter? = null
    private var interpreter2: Interpreter? = null
    private var modelsLoaded = false

    // Repository
    private lateinit var fieldRepository: FieldRepository
    private lateinit var tokenManager: TokenManager

    // Campo seleccionado (recibido desde MainActivity)
    private var selectedFieldId: Int = -1
    private var selectedFieldName: String = ""

    // Escaneo estado
    private var isScanning = false
    private var framesAnalyzed = 0
    private var plagasDetected = 0
    private var scanStartTime = 0L
    private val detectedImagesPaths = mutableListOf<String>()
    private val processingTimes = mutableListOf<Double>()
    private val confidences = mutableListOf<Float>()
    private var lastAnalysisTime = 0L

    // Handlers
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_escanear_plagas)

        // Obtener campo seleccionado desde MainActivity
        selectedFieldId = intent.getIntExtra(MainActivity.EXTRA_SELECTED_FIELD_ID, -1)
        selectedFieldName = intent.getStringExtra(MainActivity.EXTRA_SELECTED_FIELD_NAME) ?: ""

        // Validar que se haya recibido un campo válido
        if (selectedFieldId == -1) {
            showErrorAndFinish("Error: No se seleccionó un campo válido")
            return
        }

        initializeComponents()
        initializeViews()
        setupClickListeners()
        loadMLModels()

        if (hasRequiredPermissions()) {
            setupCamera()
        } else {
            showErrorAndFinish("Permisos de cámara requeridos")
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        Log.d(TAG, "Iniciando escaneo para campo: $selectedFieldName (ID: $selectedFieldId)")
    }

    private fun initializeComponents() {
        tokenManager = TokenManager(this)
        ApiClient.initialize(tokenManager)
        fieldRepository = FieldRepository(ApiClient.apiService, tokenManager)
    }

    private fun initializeViews() {
        previewView = findViewById(R.id.preview_view)
        btnIniciarEscaneo = findViewById(R.id.btn_iniciar_escaneo)
        btnDetenerEscaneo = findViewById(R.id.btn_detener_escaneo)
        btnFinalizarEscaneo = findViewById(R.id.btn_finalizar_escaneo)
        progressBar = findViewById(R.id.progress_bar)
        tvEstadoEscaneo = findViewById(R.id.tv_estado_escaneo)
        tvPlagasDetectadas = findViewById(R.id.tv_plagas_detectadas)
        tvFramesAnalizados = findViewById(R.id.tv_frames_analizados)

        btnDetenerEscaneo.isEnabled = false
        btnFinalizarEscaneo.isEnabled = false
        btnIniciarEscaneo.isEnabled = false
        progressBar.visibility = View.GONE

        // Mostrar información del campo seleccionado
        tvEstadoEscaneo.text = "Preparando escaneo para: $selectedFieldName"

        val btnBackMain: Button = findViewById(R.id.btn_back_main)
        btnBackMain.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun setupClickListeners() {
        btnIniciarEscaneo.setOnClickListener {
            startScanning()
        }

        btnDetenerEscaneo.setOnClickListener {
            stopScanning()
        }

        btnFinalizarEscaneo.setOnClickListener {
            finishScanningAndNavigate()
        }
    }

    private fun loadMLModels() {
        Thread {
            try {
                Log.d(TAG, "Cargando modelos ML...")

                interpreter1 = Interpreter(loadModelFile(MODEL_1))
                Log.d(TAG, "Modelo 1 cargado: $MODEL_1")

                interpreter2 = Interpreter(loadModelFile(MODEL_2))
                Log.d(TAG, "Modelo 2 cargado: $MODEL_2")

                modelsLoaded = true

                runOnUiThread {
                    tvEstadoEscaneo.text = "Listo para escanear: $selectedFieldName"
                    btnIniciarEscaneo.isEnabled = true
                    showToast("Campo: $selectedFieldName - Listo para escanear")
                }

                Log.d(TAG, "Todos los modelos ML cargados exitosamente")

            } catch (e: Exception) {
                Log.e(TAG, "Error cargando modelos ML", e)
                runOnUiThread {
                    showErrorAndFinish("Error cargando modelos: ${e.message}")
                }
            }
        }.start()
    }

    private fun loadModelFile(modelName: String): ByteBuffer {
        val assetFileDescriptor = assets.openFd(modelName)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun hasRequiredPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun setupCamera() {
        val cameraProviderFuture: ListenableFuture<ProcessCameraProvider> =
            ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (e: Exception) {
                Log.e(TAG, "Error al configurar la cámara", e)
                showErrorAndFinish("Error al inicializar la cámara")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return

        preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this as LifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error al enlazar casos de uso", e)
            showErrorAndFinish("Error al configurar la cámara")
        }
    }

    private fun getCurrentTime(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }

    private fun startScanning() {
        if (isScanning || !modelsLoaded || selectedFieldId == -1) return

        Log.d(TAG, "Iniciando escaneo automático para campo: $selectedFieldName (ID: $selectedFieldId)")

        isScanning = true
        scanStartTime = System.currentTimeMillis()
        scanStartTimeFormatted = getCurrentTime()  // 🔥 Guardar hora de inicio
        lastAnalysisTime = 0L

        framesAnalyzed = 0
        plagasDetected = 0
        detectedImagesPaths.clear()
        processingTimes.clear()
        confidences.clear()

        updateUI()

        imageAnalysis?.setAnalyzer(cameraExecutor!!) { imageProxy ->
            val currentTime = System.currentTimeMillis()

            if (isScanning && (currentTime - lastAnalysisTime) >= FRAME_ANALYSIS_INTERVAL_MS) {
                lastAnalysisTime = currentTime
                processFrameWithML(imageProxy)
            }

            imageProxy.close()
        }

        Log.d(TAG, "Escaneo iniciado para campo: $selectedFieldName")

        runOnUiThread {
            Toast.makeText(this, "Escaneando $selectedFieldName - Mueve la cámara lentamente", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopScanning() {
        isScanning = false
        imageAnalysis?.clearAnalyzer()
        updateUI()
        Log.d(TAG, "Escaneo detenido - Campo: $selectedFieldName, Frames: $framesAnalyzed, Plagas: $plagasDetected")

        runOnUiThread {
            Toast.makeText(this, "Escaneo detenido", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processFrameWithML(imageProxy: ImageProxy) {
        if (!isScanning || !modelsLoaded) {
            imageProxy.close()
            return
        }

        val frameStartTime = System.currentTimeMillis()

        try {
            val bitmap = imageProxyToBitmap(imageProxy)

            if (bitmap != null) {
                val analysisResult = analyzeImageWithML(bitmap)
                framesAnalyzed++

                if (analysisResult.isPlagaDetected) {
                    plagasDetected++

                    val processingTime = (System.currentTimeMillis() - frameStartTime) / 1000.0
                    processingTimes.add(processingTime)

                    // 🔥 CRÍTICO: Guardar la confidence ANTES de guardar la imagen
                    // para que el índice coincida
                    confidences.add(analysisResult.confidence)

                    // Guardar imagen (esto añade a detectedImagesPaths)
                    saveDetectedImage(bitmap, analysisResult)

                    // Log para debugging
                    Log.d(TAG, "✅ Plaga guardada - Index: ${confidences.size - 1}, Confidence: ${analysisResult.confidence} (${(analysisResult.confidence * 100).toInt()}%)")
                    Log.d(TAG, "   Total confidences: ${confidences.size}, Total imágenes: ${detectedImagesPaths.size}")

                    runOnUiThread {
                        Toast.makeText(this,
                            "Plaga detectada en $selectedFieldName! Confianza: ${(analysisResult.confidence * 100).toInt()}%",
                            Toast.LENGTH_SHORT).show()
                    }

                    Log.d(TAG, "Plaga detectada en campo $selectedFieldName - Frame: $framesAnalyzed, Confianza: ${analysisResult.confidence}")
                }

                runOnUiThread {
                    updateCounters()
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error al procesar frame con ML", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val yBuffer = imageProxy.planes[0].buffer
            val uBuffer = imageProxy.planes[1].buffer
            val vBuffer = imageProxy.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)

            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 90, out)
            val imageBytes = out.toByteArray()
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

        } catch (e: Exception) {
            Log.e(TAG, "Error convirtiendo ImageProxy a Bitmap", e)
            null
        }
    }

    private fun analyzeImageWithML(bitmap: Bitmap): MLAnalysisResult {
        try {
            val inputBuffer = preprocessImage(bitmap, INPUT_SIZE)

            val leafResult = predictLeaves(inputBuffer.duplicate())
            Log.d(TAG, "Análisis hojas - Etiqueta: ${leafResult.label}, Confianza: ${leafResult.confidence}")

            if (leafResult.label != "hojas" || leafResult.confidence < MIN_CONFIDENCE_THRESHOLD) {
                return MLAnalysisResult(
                    isPlagaDetected = false,
                    confidence = leafResult.confidence,
                    leafDetected = leafResult.label == "hojas"
                )
            }

            val pestResult = predictPest(inputBuffer.duplicate())
            Log.d(TAG, "Análisis plaga - Confianza: ${pestResult.confidence}")

            return MLAnalysisResult(
                isPlagaDetected = pestResult.isPlagaDetected,
                confidence = pestResult.confidence,
                leafDetected = true
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error en análisis ML", e)
            return MLAnalysisResult(isPlagaDetected = false, confidence = 0f, leafDetected = false)
        }
    }

    private fun predictLeaves(inputBuffer: ByteBuffer): LeafPredictionResult {
        val output = Array(1) { FloatArray(3) }
        interpreter1?.run(inputBuffer, output)

        val probabilities = output[0]
        val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: -1
        val classLabels = listOf("hojas", "otros", "animales")
        val predictedLabel = if (maxIndex != -1) classLabels[maxIndex] else "desconocido"
        val confidence = if (maxIndex != -1) probabilities[maxIndex] else 0f

        return LeafPredictionResult(predictedLabel, confidence)
    }

    private fun predictPest(inputBuffer: ByteBuffer): PestPredictionResult {
        val output = Array(1) { FloatArray(1) }
        interpreter2?.run(inputBuffer, output)

        val plagaConfidence = output[0][0]
        return PestPredictionResult(plagaConfidence, plagaConfidence > PLAGA_THRESHOLD)
    }

    private fun preprocessImage(bitmap: Bitmap, inputSize: Int): ByteBuffer {
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

    private fun saveDetectedImage(bitmap: Bitmap, result: MLAnalysisResult) {
        try {
            val fileName = generateUniqueFileName(result.confidence)
            val file = createImageFile(fileName)

            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            detectedImagesPaths.add(file.absolutePath)
            saveImageToGallery(file)

            Log.d(TAG, "Imagen guardada para campo $selectedFieldName: ${file.absolutePath}")

        } catch (e: Exception) {
            Log.e(TAG, "Error al guardar imagen detectada", e)
        }
    }

    private fun createImageFile(fileName: String): File {
        val imagesDir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "$IMAGES_FOLDER/$selectedFieldName")
        if (!imagesDir.exists()) {
            imagesDir.mkdirs()
        }
        return File(imagesDir, fileName)
    }

    private fun saveImageToGallery(imageFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveImageUsingMediaStore(imageFile)
        } else {
            saveImageToPublicDirectory(imageFile)
        }
    }

    private fun saveImageUsingMediaStore(imageFile: File) {
        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, imageFile.name)
                put(MediaStore.MediaColumns.MIME_TYPE, IMAGE_MIME_TYPE)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$IMAGES_FOLDER/$selectedFieldName")
            }

            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let { imageUri ->
                contentResolver.openOutputStream(imageUri)?.use { outputStream ->
                    FileInputStream(imageFile).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al guardar con MediaStore", e)
        }
    }

    private fun saveImageToPublicDirectory(imageFile: File) {
        try {
            MediaScannerConnection.scanFile(
                this,
                arrayOf(imageFile.absolutePath),
                arrayOf(IMAGE_MIME_TYPE)
            ) { path, uri ->
                Log.d(TAG, "Imagen escaneada: $path")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en MediaScanner", e)
        }
    }

    private fun generateUniqueFileName(confidence: Float): String {
        val timestamp = System.currentTimeMillis()
        val confidencePercent = (confidence * 100).toInt()
        return "PLAGA_${selectedFieldName}_${timestamp}_CONF${confidencePercent}$FILE_EXTENSION"
    }

    private fun updateUI() {
        btnIniciarEscaneo.isEnabled = !isScanning && modelsLoaded && selectedFieldId != -1
        btnDetenerEscaneo.isEnabled = isScanning
        btnFinalizarEscaneo.isEnabled = !isScanning

        progressBar.visibility = if (isScanning) View.VISIBLE else View.GONE

        tvEstadoEscaneo.text = when {
            selectedFieldId == -1 -> "Error: Campo no válido"
            !modelsLoaded -> "Cargando modelos para: $selectedFieldName..."
            isScanning -> "Escaneando: $selectedFieldName..."
            detectedImagesPaths.isNotEmpty() -> "Escaneo completado en: $selectedFieldName"
            else -> "Listo para escanear: $selectedFieldName"
        }
    }

    private fun updateCounters() {
        tvFramesAnalizados.text = "Frames analizados: $framesAnalyzed"
        tvPlagasDetectadas.text = "Plagas detectadas: $plagasDetected"
    }

    private fun finishScanningAndNavigate() {
        val scanDuration = (System.currentTimeMillis() - scanStartTime) / 1000.0
        val scanEndTimeFormatted = getCurrentTime()  // 🔥 Capturar hora de fin

        // 🔥 VALIDACIÓN: Verificar que haya la misma cantidad de imágenes y confidences
        if (detectedImagesPaths.size != confidences.size) {
            Log.e(TAG, "❌ ERROR: Desincronización de datos!")
            Log.e(TAG, "   Imágenes: ${detectedImagesPaths.size}, Confidences: ${confidences.size}")

            while (confidences.size < detectedImagesPaths.size) {
                confidences.add(0.5f)
                Log.w(TAG, "   Agregando confidence por defecto: 0.5")
            }
        }


        // Mostrar algunas confidences para verificar
        confidences.take(5).forEachIndexed { index, confidence ->
            Log.d(TAG, "   Confidence[$index] = $confidence (${(confidence * 100).toInt()}%)")
        }

        val intent = Intent(this, ReporteActivity::class.java).apply {
            putStringArrayListExtra(EXTRA_DETECTED_IMAGES_PATHS, ArrayList(detectedImagesPaths))
            putExtra(EXTRA_TOTAL_FRAMES_ANALYZED, framesAnalyzed)
            putExtra(EXTRA_PLAGAS_DETECTED, plagasDetected)
            putExtra(EXTRA_SCAN_DURATION, scanDuration)
            putExtra("field_id", selectedFieldId)
            putExtra("field_name", selectedFieldName)

            putExtra(ReporteActivity.EXTRA_TIME_INITIAL, scanStartTimeFormatted)
            putExtra(ReporteActivity.EXTRA_TIME_FINAL, scanEndTimeFormatted)


            val processingTimesArray = processingTimes.toDoubleArray()
            val confidencesArray = confidences.toFloatArray()

            Log.d(TAG, "   Enviando ${confidencesArray.size} confidences a ReporteActivity")

            putExtra(EXTRA_PROCESSING_TIMES, processingTimesArray)
            putExtra(EXTRA_CONFIDENCES, confidencesArray)
        }

        Log.d(TAG, "🚀 Navegando a ReporteActivity")

        startActivity(intent)
        finish()
    }

    private fun showErrorAndFinish(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScanning()
        cameraExecutor?.shutdown()
        cameraProvider?.unbindAll()
        interpreter1?.close()
        interpreter2?.close()
    }

    data class MLAnalysisResult(
        val isPlagaDetected: Boolean,
        val confidence: Float,
        val leafDetected: Boolean
    )

    data class LeafPredictionResult(
        val label: String,
        val confidence: Float
    )

    data class PestPredictionResult(
        val confidence: Float,
        val isPlagaDetected: Boolean
    )
}