package com.example.app_mosca.repositories

import com.example.app_mosca.api.apiEndpoints.ApiService
import com.example.app_mosca.models.DetectionImagesResponse
import com.example.app_mosca.models.ImageData
import com.example.app_mosca.models.ImageValidationRequest
import com.example.app_mosca.models.FalsePositiveStats
import com.example.app_mosca.utils.TokenManager

class ImageRepository(
    private val apiService: ApiService,
    private val tokenManager: TokenManager
) {

    suspend fun getDetectionImages(detectionId: Int): Result<DetectionImagesResponse> {
        return try {
            val token = tokenManager.getToken() ?: return Result.failure(
                Exception("Token no disponible")
            )

            val response = apiService.getDetectionImages(
                detectionId = detectionId
            )

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.message() ?: "Error desconocido"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun validateImage(
        imageId: Int,
        isFalsePositive: Boolean
    ): Result<ImageData> {
        return try {
            val token = tokenManager.getToken() ?: return Result.failure(
                Exception("Token no disponible")
            )

            val request = ImageValidationRequest(is_false_positive = isFalsePositive)

            val response = apiService.validateImage(
                imageId = imageId,
                validation = request
            )

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.message() ?: "Error al validar"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFalsePositiveStats(): Result<FalsePositiveStats> {
        return try {
            val token = tokenManager.getToken() ?: return Result.failure(
                Exception("Token no disponible")
            )

            val response = apiService.getFalsePositiveStats()

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.message() ?: "Error al obtener estadísticas"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}