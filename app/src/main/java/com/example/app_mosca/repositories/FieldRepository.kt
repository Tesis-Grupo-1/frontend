package com.example.app_mosca.repositories

import android.util.Log
import com.example.app_mosca.api.apiEndpoints.ApiService
import com.example.app_mosca.models.*
import com.example.app_mosca.utils.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

// ============= FIELD REPOSITORY =============
class FieldRepository(
    private val apiService: ApiService,
    private val tokenManager: TokenManager
) {
    companion object {
        private const val TAG = "FieldRepository"
    }

    suspend fun createField(fieldCreate: FieldCreate): Result<FieldResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Creando campo: ${fieldCreate.name}")
            val response = apiService.createField(fieldCreate)

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val error = response.errorBody()?.string() ?: "Error desconocido"
                Log.e(TAG, "Error creando campo: $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción creando campo", e)
            Result.failure(e)
        }
    }

    suspend fun getFields(): Result<List<FieldResponse>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getFields()

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Error obteniendo campos"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción obteniendo campos", e)
            Result.failure(e)
        }
    }

    suspend fun getFieldId(fieldId: Int): Result<FieldResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getField(fieldId)
            Log.d(TAG, "Obteniendo campo ID: $fieldId")
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Error obteniendo campo"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción obteniendo campo", e)
            Result.failure(e)
        }
    }
}

// ============= DETECTION REPOSITORY =============
class DetectionRepository(
    private val apiService: ApiService,
    private val tokenManager: TokenManager
) {
    companion object {
        private const val TAG = "DetectionRepository"
    }

    suspend fun createDetection(detection: DetectionCreate): Result<DetectionResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Creando detección para field_id: ${detection.field_id}")
            val response = apiService.createDetection(detection)

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val error = response.errorBody()?.string() ?: "Error desconocido"
                Log.e(TAG, "Error creando detección: $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción creando detección", e)
            Result.failure(e)
        }
    }

    suspend fun getDetections(): Result<List<DetectionResponse>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getDetections()

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Error obteniendo detecciones"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción obteniendo detecciones", e)
            Result.failure(e)
        }
    }

    suspend fun getDetection(detectionId: Int): Result<DetectionResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getDetection(detectionId)

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Error obteniendo detección"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción obteniendo detección", e)
            Result.failure(e)
        }
    }

    suspend fun deleteDetection(detectionId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Eliminando detección ID: $detectionId")
            val response = apiService.deleteDetection(detectionId)

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val error = response.errorBody()?.string() ?: "Error desconocido"
                Log.e(TAG, "Error eliminando detección: $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción eliminando detección", e)
            Result.failure(e)
        }
    }

}




