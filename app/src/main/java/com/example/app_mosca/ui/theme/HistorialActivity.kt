package com.example.app_mosca.ui.theme

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.app_mosca.R
import com.example.app_mosca.api.apiClient.ApiClient
import com.example.app_mosca.models.DetectionResponse
import com.example.app_mosca.repositories.DetectionRepository
import com.example.app_mosca.adapters.DetectionsAdapter
import com.example.app_mosca.utils.TokenManager
import kotlinx.coroutines.launch

class HistorialActivity : AppCompatActivity() {

    private lateinit var tokenManager: TokenManager
    private lateinit var detectionRepository: DetectionRepository

    // UI Components
    private lateinit var backButton: ImageView
    private lateinit var filterButton: ImageView
    private lateinit var tvTotalDetections: TextView
    private lateinit var tvMonthlyDetections: TextView
    private lateinit var tvPestsFound: TextView
    private lateinit var rvDetections: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyState: LinearLayout
    private lateinit var btnStartDetection: com.google.android.material.button.MaterialButton

    private lateinit var detectionsAdapter: DetectionsAdapter
    private val detectionsList = mutableListOf<DetectionResponse>()

    companion object {
        private const val REQUEST_DETECTION_DETAIL = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_historial)

        initializeComponents()
        initializeViews()
        setupRecyclerView()
        setupClickListeners()
        loadDetections()
    }

    private fun initializeComponents() {
        tokenManager = TokenManager(this)
        ApiClient.initialize(tokenManager)
        detectionRepository = DetectionRepository(ApiClient.apiService, tokenManager)
    }

    private fun initializeViews() {
        backButton = findViewById(R.id.back_button)
        filterButton = findViewById(R.id.filter_button)
        tvTotalDetections = findViewById(R.id.total_detections)
        tvMonthlyDetections = findViewById(R.id.monthly_detections)
        tvPestsFound = findViewById(R.id.pests_found)
        rvDetections = findViewById(R.id.detections_recycler_view)
        progressBar = findViewById(R.id.progress_detections)
        emptyState = findViewById(R.id.empty_state)
        btnStartDetection = findViewById(R.id.start_detection_button)
    }

    private fun setupRecyclerView() {
        detectionsAdapter = DetectionsAdapter(detectionsList) { detection ->
            openDetectionDetail(detection)
        }

        rvDetections.apply {
            layoutManager = LinearLayoutManager(this@HistorialActivity)
            adapter = detectionsAdapter
        }
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener { finish() }

        filterButton.setOnClickListener {
            showToast("Filtros próximamente")
        }

        btnStartDetection.setOnClickListener { navigateToMain() }
    }

    private fun loadDetections() {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val result = detectionRepository.getDetections()

                result.fold(
                    onSuccess = { detections ->
                        detectionsList.clear()
                        detectionsList.addAll(detections)
                        detectionsAdapter.notifyDataSetChanged()

                        updateStatistics(detections)
                        updateVisibility()
                        showLoading(false)
                    },
                    onFailure = { exception ->
                        showToast("Error cargando historial: ${exception.message}")
                        showLoading(false)
                        updateVisibility()
                    }
                )
            } catch (e: Exception) {
                showToast("Error inesperado: ${e.message}")
                showLoading(false)
                updateVisibility()
            }
        }
    }

    private fun updateStatistics(detections: List<DetectionResponse>) {
        tvTotalDetections.text = detections.size.toString()

        val currentMonth = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1
        val monthlyCount = detections.count { detection ->
            try {
                val month = detection.detection_date.split("-")[1].toInt()
                month == currentMonth
            } catch (e: Exception) {
                false
            }
        }
        tvMonthlyDetections.text = monthlyCount.toString()

        val pestsCount = detections.count { it.affected_percentage > 0 }
        tvPestsFound.text = pestsCount.toString()
    }

    private fun updateVisibility() {
        if (detectionsList.isEmpty()) {
            rvDetections.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
        } else {
            rvDetections.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
        }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        rvDetections.visibility = if (show) View.GONE else View.VISIBLE
        emptyState.visibility = View.GONE
    }

    private fun openDetectionDetail(detection: DetectionResponse) {
        val intent = Intent(this, DetectionDetailActivity::class.java).apply {
            putExtra("DETECTION_ID", detection.id_detection)
        }
        startActivityForResult(intent, REQUEST_DETECTION_DETAIL)
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_DETECTION_DETAIL && resultCode == RESULT_OK) {
            // Recargar la lista si se eliminó una detección
            loadDetections()
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}