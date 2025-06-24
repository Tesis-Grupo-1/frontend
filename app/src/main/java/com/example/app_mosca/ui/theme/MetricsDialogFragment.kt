package com.example.app_mosca.ui.theme

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.app_mosca.R
import com.example.app_mosca.models.MetricsManager
import com.example.app_mosca.utils.ModelMetrics
import kotlinx.coroutines.launch

class MetricsDialogFragment : DialogFragment() {

    companion object {
        const val EXTRA_F1_SCORE = "f1Score"
        const val EXTRA_ACCURACY = "accuracy"
        const val EXTRA_AUC = "auc"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_metrics_modal, container, false)

        val f1ScoreTextView = view.findViewById<TextView>(R.id.f1ScoreTextView)
        val accuracyTextView = view.findViewById<TextView>(R.id.accuracyTextView)
        val aucTextView = view.findViewById<TextView>(R.id.aucTextView)
        val closeButton = view.findViewById<Button>(R.id.closeButton)

        val f1Score = arguments?.getDouble(EXTRA_F1_SCORE, 0.0) ?: 0.0
        val accuracy = arguments?.getDouble(EXTRA_ACCURACY, 0.0) ?: 0.0
        val auc = arguments?.getDouble(EXTRA_AUC, 0.0) ?: 0.0

        //"AUC: $auc"
        f1ScoreTextView.text =  "F1-Score: ${formatPercentage(f1Score)}"
        accuracyTextView.text = "Accuracy: ${formatPercentage(accuracy)}"
        aucTextView.text = "AUC: ${formatPercentage(auc)}"

        closeButton.setOnClickListener {
            dismiss()  // Cerrar el modal
        }

        return view
    }

    /**
     * Formatea un valor como porcentaje con decimales inteligentes
     * Ejemplos: 98.5%, 99.45%, 100.0%
     */
    private fun formatPercentage(value: Double): String {
        return when {
            // Si es un número entero o con .0, mostrar sin decimales
            value % 1.0 == 0.0 -> "${value.toInt()}%"
            // Si tiene 1 decimal significativo, mostrar 1 decimal
            (value * 10) % 1.0 == 0.0 -> "%.1f%%".format(value)
            // Si tiene 2 decimales, mostrar 2 decimales
            else -> "%.2f%%".format(value)
        }
    }
}