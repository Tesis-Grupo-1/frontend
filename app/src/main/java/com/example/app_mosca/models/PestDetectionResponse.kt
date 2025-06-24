package com.example.app_mosca.models

data class PestDetectionResponse(
    val success: Boolean,
    val message: String?,
    val processed_image_base64: String, // Corregido: la API devuelve la imagen en base64
    val detections: List<Detection>? = null,
    val image_width: Int? = null, // Agregado: ancho de la imagen original
    val image_height: Int? = null // Agregado: alto de la imagen original
)


data class Detection(
    val confidence: Float,
    val bbox: List<Float>? = null, // [x1, y1, x2, y2] en coordenadas de la imagen original
    val class_name: String? = null // Nombre de la clase detectada (ej: "leaf-liriomyza")
)