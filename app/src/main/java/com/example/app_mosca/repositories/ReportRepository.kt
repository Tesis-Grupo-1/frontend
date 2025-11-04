package com.example.app_mosca.repositories

import android.util.Log
import com.example.app_mosca.api.apiEndpoints.ApiService
import com.example.app_mosca.models.ReportGenerateRequest
import com.example.app_mosca.models.ReportResponse
import com.example.app_mosca.utils.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ReportRepository(
    private val apiService: ApiService,
    private val tokenManager: TokenManager
) {
    suspend fun generateAIReport(request: ReportGenerateRequest): Result<ReportResponse> {
        return try {
            val response = apiService.generateAIReport(request)

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Error desconocido"
                Log.e("ReportRepository", "Error generando reporte: $errorMsg")
                Result.failure(Exception("Error ${response.code()}: $errorMsg"))
            }
        } catch (e: Exception) {
            Log.e("ReportRepository", "Excepción generando reporte", e)
            Result.failure(e)
        }
    }

    suspend fun exportReportToPdf(reportId: Int): Result<ReportResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val token = tokenManager.getToken()
                if (token.isNullOrEmpty()) {
                    return@withContext Result.failure(Exception("No hay token de autenticación"))
                }

                val response = apiService.exportReportToPdf(
                    reportId = reportId,
                )

                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    val errorBody = response.errorBody()?.string()
                    Result.failure(Exception("Error ${response.code()}: $errorBody"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}