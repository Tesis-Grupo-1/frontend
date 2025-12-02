package com.example.app_mosca.ui.theme

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.app_mosca.R
import com.example.app_mosca.api.apiClient.ApiClient
import com.example.app_mosca.models.DetectionCreate
import com.example.app_mosca.repositories.DetectionRepository
import com.example.app_mosca.repositories.FieldRepository
import com.example.app_mosca.services.ImageUploadService
import com.example.app_mosca.utils.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class ReporteActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ReporteActivity"
        const val EXTRA_TIME_INITIAL = "time_initial"
        const val EXTRA_TIME_FINAL = "time_final"
    }

    // UI Components
    private lateinit var btnFinalizar: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvTotalFrames: TextView
    private lateinit var tvPlagasDetectadas: TextView
    private lateinit var tvDuracionEscaneo: TextView
    private lateinit var tvConfianzaPromedio: TextView
    private lateinit var tvTiempoPromedio: TextView
    private lateinit var tvUploadProgress: TextView
    private lateinit var imagesRecyclerView: RecyclerView
    private lateinit var cardEstadisticas: CardView

    // Repositories & Services
    private lateinit var detectionRepository: DetectionRepository
    private lateinit var imageUploadService: ImageUploadService
    private lateinit var tokenManager: TokenManager

    private lateinit var fieldRepository: FieldRepository

    // Data
    private var detectedImagesPaths = listOf<String>()
    private var fieldCantPlants = 0
    private var totalFrames = 0
    private var plagasDetected = 0
    private var scanDuration = 0.0
    private var processingTimes = doubleArrayOf()
    private var confidences = floatArrayOf()
    private var fieldId: Int? = null
    private var uploadedImageIds = mutableListOf<Int>()
    private var savedDetectionIds = mutableListOf<Int>()

    private var timeInitial: String = ""
    private var timeFinal: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reporte)

        initializeComponents()
        loadDataFromIntent()
        setupUI()
        setupClickListeners()

        if (fieldId != null) {
            loadFieldDataAndUploadImages()
        } else {
            Log.w(TAG, "❌ Field ID no proporcionado")
        }
    }

    private fun initializeComponents() {
        tokenManager = TokenManager(this)
        ApiClient.initialize(tokenManager)
        detectionRepository = DetectionRepository(ApiClient.apiService, tokenManager)
        imageUploadService = ImageUploadService(ApiClient.apiService)
        fieldRepository = FieldRepository(ApiClient.apiService, tokenManager)

        btnFinalizar = findViewById(R.id.btn_finalizar)
        progressBar = findViewById(R.id.progress_bar)
        tvTotalFrames = findViewById(R.id.tv_total_frames)
        tvPlagasDetectadas = findViewById(R.id.tv_plagas_detectadas)
        tvDuracionEscaneo = findViewById(R.id.tv_duracion_escaneo)
        tvConfianzaPromedio = findViewById(R.id.tv_confianza_promedio)
        tvTiempoPromedio = findViewById(R.id.tv_tiempo_promedio)
        tvUploadProgress = findViewById(R.id.tv_upload_progress)
        imagesRecyclerView = findViewById(R.id.images_recycler_view)
        cardEstadisticas = findViewById(R.id.card_estadisticas)
    }

    private fun loadDataFromIntent() {
        intent?.let {
            detectedImagesPaths = it.getStringArrayListExtra(EscanearPlagas.EXTRA_DETECTED_IMAGES_PATHS)?.toList() ?: listOf()
            totalFrames = it.getIntExtra(EscanearPlagas.EXTRA_TOTAL_FRAMES_ANALYZED, 0)
            plagasDetected = it.getIntExtra(EscanearPlagas.EXTRA_PLAGAS_DETECTED, 0)
            scanDuration = it.getDoubleExtra(EscanearPlagas.EXTRA_SCAN_DURATION, 0.0)
            processingTimes = it.getDoubleArrayExtra(EscanearPlagas.EXTRA_PROCESSING_TIMES) ?: doubleArrayOf()
            confidences = it.getFloatArrayExtra(EscanearPlagas.EXTRA_CONFIDENCES) ?: floatArrayOf()
            fieldId = it.getIntExtra("field_id", -1).takeIf { id -> id != -1 }
            timeInitial = it.getStringExtra(EXTRA_TIME_INITIAL) ?: getCurrentTime()
            timeFinal = it.getStringExtra(EXTRA_TIME_FINAL) ?: getCurrentTime()
        }
    }

    private fun setupUI() {
        tvTotalFrames.text = totalFrames.toString()
        tvPlagasDetectadas.text = plagasDetected.toString()
        tvDuracionEscaneo.text = String.format("%.1fs", scanDuration)

        val avgConfidence = if (confidences.isNotEmpty()) confidences.average() else 0.0
        tvConfianzaPromedio.text = String.format("%.1f%%", avgConfidence * 100)

        val avgTime = if (processingTimes.isNotEmpty()) processingTimes.average() else 0.0
        tvTiempoPromedio.text = String.format("%.2fs", avgTime)

        imagesRecyclerView.layoutManager = GridLayoutManager(this, 2)
        imagesRecyclerView.adapter = DetectedImagesAdapter(detectedImagesPaths)

        btnFinalizar.isEnabled = false
        tvUploadProgress.visibility = View.VISIBLE
        tvUploadProgress.text = "Preparando subida..."
    }

    private fun setupClickListeners() {
        btnFinalizar.setOnClickListener {
            navigateToHome()
        }
    }

    private fun uploadImagesAndSaveDetections() {
        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE

                if (detectedImagesPaths.isEmpty()) {
                    Log.w(TAG, "⚠️ No hay imágenes detectadas, se creará detección vacía")
                    withContext(Dispatchers.Main) {
                        tvUploadProgress.text = "No se detectaron plagas, creando registro..."
                    }

                    // Crear detección sin imágenes
                    createSingleDetection(emptyList())
                    return@launch
                }

                Log.d(TAG, "Iniciando subida de ${detectedImagesPaths.size} imágenes...")
                tvUploadProgress.text = "Subiendo imágenes..."

                val result = imageUploadService.uploadImagesInBatches(
                    imagePaths = detectedImagesPaths,
                    confidences = confidences.toList()
                )

                result.fold(
                    onSuccess = { imageIds ->
                        uploadedImageIds.addAll(imageIds)
                        Log.d(TAG, "Imágenes subidas exitosamente: ${imageIds.size}")

                        withContext(Dispatchers.Main) {
                            tvUploadProgress.text = "${imageIds.size} imágenes subidas correctamente"
                        }

                        createSingleDetection(imageIds)
                    },
                    onFailure = { error ->
                        withContext(Dispatchers.Main) {
                            progressBar.visibility = View.GONE
                            tvUploadProgress.text = "Error al subir imágenes: ${error.message}"
                            btnFinalizar.isEnabled = true
                        }
                    }
                )

            } catch (e: Exception) {
                Log.e(TAG, "Excepción en uploadImagesAndSaveDetections", e)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    tvUploadProgress.text = "Error inesperado: ${e.message}"
                    btnFinalizar.isEnabled = true
                }
            }
        }
    }

    private suspend fun createSingleDetection(imageIds: List<Int>) = withContext(Dispatchers.IO) {
        try {
            val avgConfidence = if (confidences.isNotEmpty()) confidences.average().toFloat() else 0.0f
            val plaguePercentage = if (plagasDetected > 0) calculatePlaguePercentage(plagasDetected) else 0f
            val hasPlague = plagasDetected > 0

            val detection = DetectionCreate(
                image_ids = imageIds,
                field_id = fieldId!!,
                result = if (hasPlague) "Mosca Minadora detectada" else "Sin plagas detectadas",
                prediction_value = String.format("%.2f", avgConfidence),
                time_initial = timeInitial,
                time_final = timeFinal,
                date_detection = getCurrentDate(),
                plague_percentage = plaguePercentage
            )

            val result = detectionRepository.createDetection(detection)

            result.fold(
                onSuccess = { response ->
                    savedDetectionIds.add(response.id_detection)
                    Log.d(TAG, "Detección creada correctamente (ID ${response.id_detection})")

                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        tvUploadProgress.text =
                            if (hasPlague) "Detección guardada exitosamente"
                            else "Registro sin plagas guardado"
                        btnFinalizar.isEnabled = true
                        showToast(if (hasPlague) "¡Detección guardada con éxito!" else "No se detectaron plagas.")
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "Error creando detección: ${error.message}")
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        tvUploadProgress.text = "Error al crear detección: ${error.message}"
                        btnFinalizar.isEnabled = true
                    }
                }
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error en createSingleDetection", e)
            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                tvUploadProgress.text = "Error al crear detección: ${e.message}"
                btnFinalizar.isEnabled = true
            }
        }
    }

    private fun navigateToHome() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }

    private fun getCurrentTime(): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

    private fun getCurrentDate(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    inner class DetectedImagesAdapter(
        private val imagePaths: List<String>
    ) : RecyclerView.Adapter<DetectedImagesAdapter.ImageViewHolder>() {

        inner class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imageView: ImageView = view.findViewById(R.id.detected_image)
            val confidenceText: TextView = view.findViewById(R.id.confidence_text)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ImageViewHolder {
            val view = layoutInflater.inflate(R.layout.item_detected_image, parent, false)
            return ImageViewHolder(view)
        }

        override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
            val imagePath = imagePaths[position]
            val confidence = confidences.getOrNull(position) ?: 0f

            val bitmap = BitmapFactory.decodeFile(imagePath)
            holder.imageView.setImageBitmap(bitmap)
            holder.confidenceText.text = "${(confidence * 100).toInt()}%"
        }

        override fun getItemCount() = imagePaths.size
    }

    private fun loadFieldDataAndUploadImages() {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Cargando datos del campo ID: $fieldId")

                val result = fieldRepository.getFieldId(fieldId!!)
                result.fold(
                    onSuccess = { field ->
                        fieldCantPlants = field.cant_plants
                        Log.d(TAG, "Campo cargado: ${field.name}, Plantas: $fieldCantPlants")

                        // Ahora sí proceder con la subida de imágenes
                        uploadImagesAndSaveDetections()
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Error cargando campo: ${error.message}")
                        withContext(Dispatchers.Main) {
                            showToast("Error cargando información del campo")
                            // Usar un valor por defecto si falla
                            fieldCantPlants = 100
                            uploadImagesAndSaveDetections()
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Excepción cargando campo", e)
                withContext(Dispatchers.Main) {
                    showToast("Error inesperado cargando campo")
                    fieldCantPlants = 100
                    uploadImagesAndSaveDetections()
                }
            }
        }
    }

    private fun calculatePlaguePercentage(detectedImages: Int): Float {
        if (fieldCantPlants <= 0) {
            Log.w(TAG, "⚠️ Cantidad de plantas es 0, usando valor por defecto")
            return 0f
        }

        // Porcentaje = (imágenes con plaga / total plantas) * 100
        val percentage = (detectedImages.toFloat() / fieldCantPlants) * 100

        // Limitar a un máximo de 100%
        val finalPercentage = minOf(percentage, 100f)

        Log.d(TAG, "📊 Cálculo de porcentaje:")
        Log.d(TAG, "   Imágenes detectadas: $detectedImages")
        Log.d(TAG, "   Plantas en campo: $fieldCantPlants")
        Log.d(TAG, "   Porcentaje afectado: %.2f%%".format(finalPercentage))

        return finalPercentage
    }
}
