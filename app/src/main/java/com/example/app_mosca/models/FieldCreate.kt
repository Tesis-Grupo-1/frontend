package com.example.app_mosca.models

import com.google.gson.annotations.SerializedName

// ============= FIELD MODELS =============
data class FieldCreate(
    val name: String,
    val size_hectares: Float,
    val cant_plants: Int,
    val location: String,
    val description: String
)

data class FieldResponse(
    val id: Int,
    val name: String,
    val size_hectares: Float,
    val cant_plants: Int,
    val location: String,
    val description: String,
    val user_id: Int,
    val created_at: String,
    val updated_at: String? = null
)


// ============= REPORT MODELS =============
data class ReportCreate(
    val field_id: Int,
    val title: String,
    val content: String
)

data class ReportGenerateRequest(
    val field_id: Int,
    val detection_id: Int,
    val title: String? = null
)

data class ReportResponse(
    val id: Int,
    val field_id: Int,
    val user_id: Int,
    val title: String,
    val content: String,
    val ai_content: String?,
    val created_at: String,
    val field: FieldResponse?
)

data class ReportUpdate(
    val title: String? = null,
    val content: String? = null
)