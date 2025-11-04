package com.example.app_mosca.models

data class ImageUploadResponse(
    val id_image: Int,
    val name: String,
    val url_image: String,
    val porcentaje_plaga: Float,
    val uploaded_at: String
)