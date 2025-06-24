package com.example.app_mosca.models

import android.content.Context
import com.example.app_mosca.utils.ModelMetrics


class MetricsManager(private val context: Context) {

    /**
     * Calcula las métricas del modelo de hojas (Modelo 1)
     */
    fun calculateLeafModelMetrics(): ModelMetrics {
        return ModelMetrics(
            accuracy = 0.9833f,
            f1Score = 0.9833f,
            auc = 0.9875f,
            precision = 0.9834f,
            recall = 0.9833f
        )
    }

    /**
     * Calcula las métricas del modelo de detección de plagas (Modelo 2)
     */
    fun calculatePestModelMetrics(): ModelMetrics {
        return ModelMetrics(
            accuracy = 0.9646f,
            f1Score = 0.9630f,
            auc = 0.9959f,
            precision = 0.9556f,
            recall = 0.9707f
        )
    }

    /**
     * Calcula las métricas del modelo de clasificación (Modelo 3)
     * Nota: Agrega aquí las métricas cuando las tengas disponibles
     */
    fun calculateClassificationModelMetrics(): ModelMetrics {
        return ModelMetrics(
            accuracy = 0.95f, // Placeholder - reemplazar con métricas reales
            f1Score = 0.95f,
            auc = 0.95f,
            precision = 0.95f,
            recall = 0.95f
        )
    }

    /**
     * Obtiene las métricas según el resultado final del procesamiento
     */
    fun getMetricsForResult(finalLabel: String): ModelMetrics {
        return when (finalLabel) {
            "hojas" -> calculateLeafModelMetrics()
            "no_hojas", "otros", "animales" -> calculateLeafModelMetrics()
            "plaga" -> calculatePestModelMetrics()
            "sana" -> calculatePestModelMetrics()
            else -> calculatePestModelMetrics() // Por defecto usar métricas del modelo 2
        }
    }
}