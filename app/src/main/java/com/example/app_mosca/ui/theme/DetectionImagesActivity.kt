package com.example.app_mosca.ui.theme

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.app_mosca.R
import com.example.app_mosca.adapters.ImageGalleryAdapter
import com.example.app_mosca.adapters.SelectableImageAdapter
import com.example.app_mosca.api.apiClient.ApiClient
import com.example.app_mosca.models.DetectionImagesResponse
import com.example.app_mosca.models.ImageData
import com.example.app_mosca.repositories.ImageRepository
import com.example.app_mosca.utils.TokenManager
import kotlinx.coroutines.launch

class DetectionImagesActivity : AppCompatActivity() {

    private lateinit var tokenManager: TokenManager
    private lateinit var imageRepository: ImageRepository

    // UI Components
    private lateinit var backButton: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var contentLayout: LinearLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var imageAdapter: ImageGalleryAdapter

    // Header info
    private lateinit var tvFieldName: TextView
    private lateinit var tvDetectionDate: TextView
    private lateinit var tvTotalImages: TextView
    private lateinit var tvValidatedImages: TextView
    private lateinit var tvFalsePositives: TextView

    // Stats
    private lateinit var progressValidation: ProgressBar
    private lateinit var tvValidationPercentage: TextView

    // Botones de acción masiva
    private lateinit var btnValidateAll: Button
    private lateinit var btnMarkFalsePositives: Button

    private var detectionId: Int = -1
    private var detectionData: DetectionImagesResponse? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detection_images)

        detectionId = intent.getIntExtra("DETECTION_ID", -1)
        if (detectionId == -1) {
            showToast("Error: ID de detección no válido")
            finish()
            return
        }

        initializeComponents()
        initializeViews()
        setupClickListeners()
        setupRecyclerView()

        loadDetectionImages()
    }

    private fun initializeComponents() {
        tokenManager = TokenManager(this)
        ApiClient.initialize(tokenManager)
        imageRepository = ImageRepository(ApiClient.apiService, tokenManager)
    }

    private fun initializeViews() {
        backButton = findViewById(R.id.back_button)
        progressBar = findViewById(R.id.progress_bar)
        contentLayout = findViewById(R.id.content_layout)
        recyclerView = findViewById(R.id.recycler_images)

        tvFieldName = findViewById(R.id.tv_field_name)
        tvDetectionDate = findViewById(R.id.tv_detection_date)
        tvTotalImages = findViewById(R.id.tv_total_images)
        tvValidatedImages = findViewById(R.id.tv_validated_images)
        tvFalsePositives = findViewById(R.id.tv_false_positives)
        progressValidation = findViewById(R.id.progress_validation)
        tvValidationPercentage = findViewById(R.id.tv_validation_percentage)

        btnValidateAll = findViewById(R.id.btn_validate_all)
        btnMarkFalsePositives = findViewById(R.id.btn_mark_false_positives)
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener { finish() }

        btnValidateAll.setOnClickListener {
            showValidateAllDialog()
        }

        btnMarkFalsePositives.setOnClickListener {
            showFalsePositivesSelectionDialog()
        }
    }

    private fun setupRecyclerView() {
        imageAdapter = ImageGalleryAdapter(
            onImageClick = { image -> showImageDetail(image) },
            onValidateClick = { image -> showValidationDialog(image) }
        )

        recyclerView.apply {
            layoutManager = GridLayoutManager(this@DetectionImagesActivity, 2)
            adapter = imageAdapter
        }
    }

    private fun loadDetectionImages() {
        showLoading(true)

        lifecycleScope.launch {
            try {
                val result = imageRepository.getDetectionImages(detectionId)
                result.fold(
                    onSuccess = { response ->
                        detectionData = response
                        displayImages(response)
                        updateStatistics(response)
                        showLoading(false)
                    },
                    onFailure = { exception ->
                        showToast("Error cargando imágenes: ${exception.message}")
                        showLoading(false)
                        finish()
                    }
                )
            } catch (e: Exception) {
                showToast("Error inesperado: ${e.message}")
                showLoading(false)
                finish()
            }
        }
    }

    private fun displayImages(response: DetectionImagesResponse) {
        tvFieldName.text = response.field_name
        tvDetectionDate.text = response.detection_date
        imageAdapter.submitList(response.images)
    }

    private fun updateStatistics(response: DetectionImagesResponse) {
        tvTotalImages.text = "${response.total_images}"
        tvValidatedImages.text = "${response.validated_images}"
        tvFalsePositives.text = "${response.false_positives}"

        val validationPercentage = if (response.total_images > 0) {
            (response.validated_images.toFloat() / response.total_images * 100).toInt()
        } else 0

        progressValidation.progress = validationPercentage
        tvValidationPercentage.text = "$validationPercentage%"
    }

    private fun showImageDetail(image: ImageData) {
        AlertDialog.Builder(this)
            .setTitle("Detalle de Imagen")
            .setMessage("""
                Porcentaje de plaga: ${String.format("%.2f", image.porcentaje_plaga)}%
                Estado: ${if (image.is_validated) "Validada" else "Sin validar"}
                ${if (image.is_false_positive) "⚠️ Marcada como falso positivo" else ""}
            """.trimIndent())
            .setPositiveButton("Cerrar", null)
            .show()
    }

    private fun showValidationDialog(image: ImageData) {
        if (image.is_validated) {
            showToast("Esta imagen ya fue validada")
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Validar Detección")
            .setMessage("¿Es correcta esta detección de plaga?")
            .setPositiveButton("✓ Correcta") { _, _ ->
                validateImage(image, isFalsePositive = false)
            }
            .setNegativeButton("✗ Falso Positivo") { _, _ ->
                validateImage(image, isFalsePositive = true)
            }
            .setNeutralButton("Cancelar", null)
            .show()
    }

    private fun showValidateAllDialog() {
        val unvalidatedImages = detectionData?.images?.filter { !it.is_validated } ?: emptyList()

        if (unvalidatedImages.isEmpty()) {
            showToast("No hay imágenes sin validar")
            return
        }

        AlertDialog.Builder(this)
            .setTitle("⚠️ Validar Todas")
            .setMessage("¿Deseas validar las ${unvalidatedImages.size} imágenes no verificadas como correctas?")
            .setPositiveButton("Sí, validar todas") { _, _ ->
                validateAllImages(unvalidatedImages)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showFalsePositivesSelectionDialog() {
        val unvalidatedImages = detectionData?.images?.filter { !it.is_validated } ?: emptyList()

        if (unvalidatedImages.isEmpty()) {
            showToast("No hay imágenes sin validar")
            return
        }

        // Inflar el layout del diálogo personalizado
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_select_false_positives, null)
        val recyclerSelectable: RecyclerView = dialogView.findViewById(R.id.recycler_selectable_images)
        val btnSelectAll: Button = dialogView.findViewById(R.id.btn_select_all)
        val btnCancel: Button = dialogView.findViewById(R.id.btn_cancel)
        val btnConfirm: Button = dialogView.findViewById(R.id.btn_confirm)

        // Crear el adaptador para las imágenes seleccionables
        var selectedCount = 0
        val selectableAdapter = SelectableImageAdapter(unvalidatedImages) { count ->
            selectedCount = count
            btnConfirm.text = if (count > 0) "Marcar ($count)" else "Marcar"
        }

        recyclerSelectable.apply {
            layoutManager = GridLayoutManager(this@DetectionImagesActivity, 2)
            adapter = selectableAdapter
        }

        // Crear el diálogo
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // Configurar botones
        var allSelected = false
        btnSelectAll.setOnClickListener {
            if (allSelected) {
                selectableAdapter.deselectAll()
                btnSelectAll.text = "Todas"
                allSelected = false
            } else {
                selectableAdapter.selectAll()
                btnSelectAll.text = "Ninguna"
                allSelected = true
            }
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            val selectedImages = selectableAdapter.getSelectedImages()
            if (selectedImages.isEmpty()) {
                showToast("No se seleccionó ninguna imagen")
            } else {
                dialog.dismiss()
                confirmMarkAsFalsePositives(selectedImages)
            }
        }

        dialog.show()
    }

    private fun confirmMarkAsFalsePositives(images: List<ImageData>) {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Confirmar Falsos Positivos")
            .setMessage("¿Marcar ${images.size} imagen(es) como falso positivo?")
            .setPositiveButton("Sí, marcar") { _, _ ->
                markImagesAsFalsePositives(images)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun validateAllImages(images: List<ImageData>) {
        showLoading(true)
        var completedCount = 0
        var errorCount = 0

        lifecycleScope.launch {
            for (image in images) {
                try {
                    val result = imageRepository.validateImage(image.id_image, isFalsePositive = false)
                    result.fold(
                        onSuccess = { completedCount++ },
                        onFailure = { errorCount++ }
                    )
                } catch (e: Exception) {
                    errorCount++
                }
            }

            showLoading(false)

            if (errorCount == 0) {
                showToast("✓ ${completedCount} imágenes validadas correctamente")
            } else {
                showToast("⚠️ ${completedCount} validadas, ${errorCount} con error")
            }

            loadDetectionImages()
        }
    }

    private fun markImagesAsFalsePositives(images: List<ImageData>) {
        showLoading(true)
        var completedCount = 0
        var errorCount = 0

        lifecycleScope.launch {
            for (image in images) {
                try {
                    val result = imageRepository.validateImage(image.id_image, isFalsePositive = true)
                    result.fold(
                        onSuccess = { completedCount++ },
                        onFailure = { errorCount++ }
                    )
                } catch (e: Exception) {
                    errorCount++
                }
            }

            showLoading(false)

            if (errorCount == 0) {
                showToast("✓ ${completedCount} imágenes marcadas como falsos positivos")
            } else {
                showToast("⚠️ ${completedCount} marcadas, ${errorCount} con error")
            }

            loadDetectionImages()
        }
    }

    private fun validateImage(image: ImageData, isFalsePositive: Boolean) {
        lifecycleScope.launch {
            try {
                val result = imageRepository.validateImage(image.id_image, isFalsePositive)
                result.fold(
                    onSuccess = {
                        showToast(if (isFalsePositive)
                            "Marcada como falso positivo"
                        else
                            "Validada como correcta"
                        )
                        loadDetectionImages()
                    },
                    onFailure = { exception ->
                        showToast("Error al validar: ${exception.message}")
                    }
                )
            } catch (e: Exception) {
                showToast("Error inesperado: ${e.message}")
            }
        }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        contentLayout.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}