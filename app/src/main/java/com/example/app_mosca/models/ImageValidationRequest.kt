package com.example.app_mosca.models

import com.google.gson.annotations.SerializedName

data class ImageValidationRequest(
    @SerializedName("is_false_positive")
    val is_false_positive: Boolean
)
