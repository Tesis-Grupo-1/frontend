package com.example.app_mosca.utils

import android.util.Log
import kotlin.math.*

data class ModelMetrics(
    val accuracy: Float,
    val f1Score: Float,
    val auc: Float,
    val precision: Float,
    val recall: Float
) {

    /**
     * Convierte las métricas a un formato legible para mostrar en UI
     */
    fun toDisplayString(): String {
        val sb = StringBuilder()
        sb.append("Accuracy: ${(accuracy * 100).toInt()}%\n")
        sb.append("F1-Score: ${String.format("%.4f", f1Score)}\n")
        sb.append("AUC: ${String.format("%.4f", auc)}\n")
        sb.append("Precision: ${String.format("%.4f", precision)}\n")
        sb.append("Recall: ${String.format("%.4f", recall)}")


        return sb.toString()
    }
}