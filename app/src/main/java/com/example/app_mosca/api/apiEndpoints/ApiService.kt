package com.example.app_mosca.api.apiEndpoints

import com.example.app_mosca.models.DetectionResponse
import com.example.app_mosca.models.PlagaResponse
import com.example.app_mosca.models.UploadResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {

    // Endpoints para usuarios
    @Multipart
    @POST("/detection/predict")
    fun predictPlaga(@Part("id_image") idImage: RequestBody, @Part file: MultipartBody.Part): Call<PlagaResponse>


    @Multipart
    @POST("/photo/upload")
    fun uploadImage(@Part file: MultipartBody.Part): Call<UploadResponse>

    @FormUrlEncoded
    @POST("/detection/save_detection_time")
    fun saveDetectionTime(
        @Field("id_detection") idDetection: Int,
        @Field("start_time2") timeInitial: String,
        @Field("end_time2") timeFinal: String,
        @Field("time_detection") timeDetection: Float
    ): Call<DetectionResponse>

}