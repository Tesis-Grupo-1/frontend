package com.example.app_mosca.api.apiEndpoints

import com.example.app_mosca.models.DetectionResponse
import com.example.app_mosca.models.PestDetectionResponse
import com.example.app_mosca.models.PlagaResponse
import com.example.app_mosca.models.UploadResponse
import com.example.app_mosca.models.UserResponse
import com.example.app_mosca.models.Token
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import com.example.app_mosca.models.*
import retrofit2.http.*

interface ApiService {

    // ============= AUTHENTICATION =============
    @FormUrlEncoded
    @POST("auth/login")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): Response<LoginResponse>

    @POST("auth/register")
    suspend fun register(
        @Body registerRequest: RegisterRequest
    ): Response<UserResponse>

    @GET("auth/me")
    suspend fun getCurrentUser(): Response<UserResponse>


    // ============= IMAGES =============
    @Multipart
    @POST("photo/upload")
    suspend fun uploadImage(
        @Part file: MultipartBody.Part,
        @Part("porcentaje_plaga") porcentajePlaga: RequestBody
    ): Response<ImageUploadResponse>

    // ============= FIELDS =============
    @POST("fields/")
    suspend fun createField(@Body field: FieldCreate): Response<FieldResponse>

    @GET("fields/")
    suspend fun getFields(): Response<List<FieldResponse>>

    @GET("fields/{field_id}")
    suspend fun getField(@Path("field_id") fieldId: Int): Response<FieldResponse>

    @PUT("fields/{field_id}")
    suspend fun updateField(
        @Path("field_id") fieldId: Int,
        @Body field: FieldCreate
    ): Response<FieldResponse>

    @DELETE("fields/{field_id}")
    suspend fun deleteField(@Path("field_id") fieldId: Int): Response<Unit>

    // ============= DETECTIONS =============
    @POST("detection/create")
    suspend fun createDetection(@Body detection: DetectionCreate): Response<DetectionResponse>

    @GET("detection/")
    suspend fun getDetections(): Response<List<DetectionResponse>>

    @GET("detection/{detection_id}")
    suspend fun getDetection(@Path("detection_id") detectionId: Int): Response<DetectionResponse>

    @PUT("detection/{detection_id}")
    suspend fun updateDetection(
        @Path("detection_id") detectionId: Int,
        @Body detection: DetectionCreate
    ): Response<DetectionResponse>

    @DELETE("detection/{detection_id}")
    suspend fun deleteDetection(@Path("detection_id") detectionId: Int): Response<Unit>

    @Multipart
    @POST("detection/detect-pests")
    fun detectPests(
        @Part file: MultipartBody.Part,
        @Part("return_image") returnImage: RequestBody
    ): retrofit2.Call<PestDetectionResponse>

    // ============= REPORTS =============
    @POST("reports/")
    suspend fun createReport(@Body report: ReportCreate): Response<ReportResponse>

    @POST("reports/generate-ai")
    suspend fun generateAIReport(@Body request: ReportGenerateRequest): Response<ReportResponse>

    @GET("reports/")
    suspend fun getReports(): Response<List<ReportResponse>>

    @GET("reports/{report_id}")
    suspend fun getReport(@Path("report_id") reportId: Int): Response<ReportResponse>

    @PUT("reports/{report_id}")
    suspend fun updateReport(
        @Path("report_id") reportId: Int,
        @Body report: ReportUpdate
    ): Response<ReportResponse>

    @POST("reports/{report_id}/export-pdf")
    suspend fun exportReportToPdf(
        @Path("report_id") reportId: Int,
    ): Response<ReportResponse>

    @DELETE("reports/{report_id}")
    suspend fun deleteReport(@Path("report_id") reportId: Int): Response<Unit>

    // ============= LEGACY (Deprecated) =============
    @Deprecated("Use createDetection instead")
    @FormUrlEncoded
    @POST("/detection/save_detection")
    fun saveDetectionTime(
        @Field("image_id") image_id: Int,
        @Field("result") result: String,
        @Field("prediction_value") prediction_value: String,
        @Field("time_initial") time_initial: String,
        @Field("time_final") time_final: String,
        @Field("date_detection") date_detection: String,
    ): retrofit2.Call<DetectionResponse>


    @GET("photo/detection/{detection_id}")
    suspend fun getDetectionImages(
        @Path("detection_id") detectionId: Int
    ): Response<DetectionImagesResponse>

    @POST("photo/validate/{image_id}")
    suspend fun validateImage(
        @Path("image_id") imageId: Int,
        @Body validation: ImageValidationRequest
    ): Response<ImageData>

    @GET("photo/stats/false-positives")
    suspend fun getFalsePositiveStats(): Response<FalsePositiveStats>
}