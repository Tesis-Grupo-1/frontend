package com.example.app_mosca.models

import com.google.gson.annotations.SerializedName

data class DetectionResponse(
    @SerializedName("id") val id_detection: Int,
    @SerializedName("field_id") val field_id: Int,
    @SerializedName("user_id") val user_id: Int,
    @SerializedName("detection_date") val detection_date: String,
    @SerializedName("affected_percentage") val affected_percentage: Float,
    @SerializedName("result") val result: String,
    @SerializedName("prediction_value") val prediction_value: String,
    @SerializedName("time_initial") val time_initial: String,
    @SerializedName("time_final") val time_final: String,
    @SerializedName("field_name") val field_name: String?,
    @SerializedName("plant_count") val plant_count: Int
)

// Modelo de Field que incluye cant_plants
data class FieldResponseWithPlants(
    val id: Int,
    val name: String,
    val size_hectares: Float,
    val location: String?,
    val description: String?,
    val cant_plants: Int,
    val user_id: Int,
    val created_at: String,
    val updated_at: String?
)

// Si no está definido, agregar también:
data class DetectionCreate(
    val image_ids: List<Int>,
    val field_id: Int,
    val result: String,
    val prediction_value: String,
    val time_initial: String,
    val time_final: String,
    val date_detection: String,
    val plague_percentage: Float
)