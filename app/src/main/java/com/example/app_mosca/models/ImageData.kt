package com.example.app_mosca.models

import com.google.gson.annotations.SerializedName

data class ImageData(
    @SerializedName("id_image")
    val id_image: Int,

    @SerializedName("image_path")
    val image_path: String,

    @SerializedName("porcentaje_plaga")
    val porcentaje_plaga: Float,

    @SerializedName("created_at")
    val created_at: String,

    @SerializedName("is_validated")
    val is_validated: Boolean,

    @SerializedName("is_false_positive")
    val is_false_positive: Boolean,

    @SerializedName("validated_at")
    val validated_at: String?,

    @SerializedName("detection_id")
    val detection_id: Int
)