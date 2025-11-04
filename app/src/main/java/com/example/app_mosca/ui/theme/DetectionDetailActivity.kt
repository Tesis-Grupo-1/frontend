package com.example.app_mosca.ui.theme

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.app_mosca.R
import com.example.app_mosca.api.apiClient.ApiClient
import com.example.app_mosca.models.DetectionResponse
import com.example.app_mosca.repositories.DetectionRepository
import com.example.app_mosca.utils.TokenManager
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class DetectionDetailActivity : AppCompatActivity() {

    private lateinit var tokenManager: TokenManager
    private lateinit var detectionRepository: DetectionRepository

    // UI Components
    private lateinit var backButton: ImageView
    private lateinit var deleteButton: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var contentLayout: LinearLayout

    // Información del header
    private lateinit var tvFieldName: TextView

    // Información de fecha y hora
    private lateinit var tvDetectionDate: TextView
    private lateinit var tvDetectionTime: TextView

    // Información de afectación
    private lateinit var tvAffectedPercentage: TextView
    private lateinit var progressBarAffected: ProgressBar

    // Información del campo
    private lateinit var tvPlantCount: TextView
    private lateinit var tvPredictionValue: TextView

    // Estado
    private lateinit var viewStatusColor: View
    private lateinit var tvStatusTitle: TextView
    private lateinit var tvResult: TextView
    private lateinit var tvStatusDescription: TextView

    // Botón
    private lateinit var btnViewImages: Button

    private var detectionId: Int = -1
    private var currentDetection: DetectionResponse? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detection_detail)

        detectionId = intent.getIntExtra("DETECTION_ID", -1)
        if (detectionId == -1) {
            showToast("Error: ID de detección no válido")
            finish()
            return
        }

        initializeComponents()
        initializeViews()
        setupClickListeners()
        loadDetectionDetail()
    }

    private fun initializeComponents() {
        tokenManager = TokenManager(this)
        ApiClient.initialize(tokenManager)
        detectionRepository = DetectionRepository(ApiClient.apiService, tokenManager)
    }

    private fun initializeViews() {
        backButton = findViewById(R.id.back_button)
        deleteButton = findViewById(R.id.delete_button)
        progressBar = findViewById(R.id.progress_bar)
        contentLayout = findViewById(R.id.content_layout)

        tvFieldName = findViewById(R.id.tv_field_name)
        tvDetectionDate = findViewById(R.id.tv_detection_date)
        tvDetectionTime = findViewById(R.id.tv_detection_time)
        tvAffectedPercentage = findViewById(R.id.tv_affected_percentage)
        progressBarAffected = findViewById(R.id.progress_bar_affected)
        tvPlantCount = findViewById(R.id.tv_plant_count)
        tvPredictionValue = findViewById(R.id.tv_prediction_value)
        viewStatusColor = findViewById(R.id.view_status_color)
        tvStatusTitle = findViewById(R.id.tv_status_title)
        tvResult = findViewById(R.id.tv_result)
        tvStatusDescription = findViewById(R.id.tv_status_description)
        btnViewImages = findViewById(R.id.btn_view_images)
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener { finish() }

        deleteButton.setOnClickListener {
            showDeleteConfirmationDialog()
        }

        btnViewImages.setOnClickListener {
            val intent = Intent(this, DetectionImagesActivity::class.java)
            intent.putExtra("DETECTION_ID", detectionId)
            startActivity(intent)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun loadDetectionDetail() {
        showLoading(true)

        lifecycleScope.launch {
            try {
                val result = detectionRepository.getDetection(detectionId)
                result.fold(
                    onSuccess = { detection ->
                        currentDetection = detection
                        displayDetectionDetail(detection)
                        showLoading(false)
                    },
                    onFailure = { exception ->
                        showToast("Error cargando detalles: ${exception.message}")
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

    @RequiresApi(Build.VERSION_CODES.O)
    private fun displayDetectionDetail(detection: DetectionResponse) {
        // Header - Nombre del campo
        tvFieldName.text = detection.field_name ?: "Campo desconocido"

        // Fecha de detección
        try {
            val date = LocalDate.parse(
                detection.detection_date,
                DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())
            )
            val dateFormatter = DateTimeFormatter.ofPattern(
                "EEEE, dd 'de' MMMM 'de' yyyy",
                Locale("es", "ES")
            )
            tvDetectionDate.text = date.format(dateFormatter)
        } catch (e: Exception) {
            tvDetectionDate.text = detection.detection_date
        }

        // Horario de escaneo
        tvDetectionTime.text = "${detection.time_initial} - ${detection.time_final}"

        // Porcentaje afectado
        val percentage = detection.affected_percentage
        tvAffectedPercentage.text = String.format("%.1f%%", percentage)
        progressBarAffected.progress = percentage.toInt()

        // Colorear el porcentaje según el nivel
        val percentageColor = when {
            percentage >= 50f -> R.color.error_red
            percentage >= 25f -> R.color.warning_orange
            percentage > 0f -> R.color.info_blue
            else -> R.color.success_green
        }
        tvAffectedPercentage.setTextColor(getColor(percentageColor))

        // Información del campo
        tvPlantCount.text = detection.plant_count.toString()
        tvPredictionValue.text = detection.prediction_value

        // Resultado
        tvResult.text = detection.result

        // Estado visual según porcentaje
        updateStatusView(percentage)
    }

    private fun updateStatusView(percentage: Float) {
        when {
            percentage >= 50f -> {
                viewStatusColor.setBackgroundColor(getColor(R.color.error_red))
                tvStatusTitle.text = "🚨 Estado Crítico"
                tvStatusTitle.setTextColor(getColor(R.color.error_red))
                tvStatusDescription.text = "Se requiere atención urgente. El nivel de afectación es alto y puede comprometer seriamente la producción del campo. Se recomienda implementar medidas de control inmediatas."
            }
            percentage >= 25f -> {
                viewStatusColor.setBackgroundColor(getColor(R.color.warning_orange))
                tvStatusTitle.text = "⚠️ Requiere Atención"
                tvStatusTitle.setTextColor(getColor(R.color.warning_orange))
                tvStatusDescription.text = "Nivel moderado de afectación detectado. Se recomienda implementar medidas de control preventivas y monitorear la evolución del campo en los próximos días."
            }
            percentage > 0f -> {
                viewStatusColor.setBackgroundColor(getColor(R.color.info_blue))
                tvStatusTitle.text = "ℹ️ Detección Leve"
                tvStatusTitle.setTextColor(getColor(R.color.info_blue))
                tvStatusDescription.text = "Afectación leve detectada. Mantener monitoreo regular del campo y aplicar tratamientos preventivos según sea necesario."
            }
            else -> {
                viewStatusColor.setBackgroundColor(getColor(R.color.success_green))
                tvStatusTitle.text = "✅ Campo Saludable"
                tvStatusTitle.setTextColor(getColor(R.color.success_green))
                tvStatusDescription.text = "No se detectaron plagas en este campo. El cultivo se encuentra en buen estado. Continuar con el cuidado preventivo habitual."
            }
        }
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Eliminar Detección")
            .setMessage("¿Estás seguro que deseas eliminar esta detección? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ -> deleteDetection() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteDetection() {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val result = detectionRepository.deleteDetection(detectionId)
                result.fold(
                    onSuccess = {
                        showToast("Detección eliminada exitosamente")
                        setResult(RESULT_OK)
                        finish()
                    },
                    onFailure = { exception ->
                        showToast("Error al eliminar: ${exception.message}")
                        showLoading(false)
                    }
                )
            } catch (e: Exception) {
                showToast("Error inesperado: ${e.message}")
                showLoading(false)
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