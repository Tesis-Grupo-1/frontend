package com.example.app_mosca.models

data class DetectionResponse(
    val image_id: Int,
    val result: String,
    val prediction_value: String,
    val time_initial: String,
    val time_final: String,
    val date_detection: String
)
