package com.example.app_mosca.models

data class PlagaResponse(
    val idDetection: Int,
    val prediction_value: Double,
    val plaga: Boolean
)