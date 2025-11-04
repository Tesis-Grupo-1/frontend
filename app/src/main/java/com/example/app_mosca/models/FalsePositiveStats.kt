package com.example.app_mosca.models

import com.google.gson.annotations.SerializedName

data class FalsePositiveStats(
    @SerializedName("total_validated_images")
    val total_validated_images: Int,

    @SerializedName("total_false_positives")
    val total_false_positives: Int,

    @SerializedName("false_positive_rate")
    val false_positive_rate: Float,

    @SerializedName("accuracy_rate")
    val accuracy_rate: Float
)