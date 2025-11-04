package com.example.app_mosca.models

import com.google.gson.annotations.SerializedName

data class DetectionImagesResponse(
    @SerializedName("detection_id")
    val detection_id: Int,

    @SerializedName("field_name")
    val field_name: String,

    @SerializedName("detection_date")
    val detection_date: String,

    @SerializedName("total_images")
    val total_images: Int,

    @SerializedName("validated_images")
    val validated_images: Int,

    @SerializedName("false_positives")
    val false_positives: Int,

    @SerializedName("images")
    val images: List<ImageData>
)
