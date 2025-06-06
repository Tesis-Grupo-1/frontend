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

    @Multipart
    @POST("/photo/upload")
    fun uploadImage(@Part file: MultipartBody.Part): Call<UploadResponse>

    @FormUrlEncoded
    @POST("/detection/save_detection")
    fun saveDetectionTime(
        @Field("image_id") image_id: Int,
        @Field("result") result: String,
        @Field("prediction_value") prediction_value: String,
        @Field("time_initial") time_initial: String,
        @Field("time_final") time_final: String,
        @Field("date_detection") date_detection: String,
    ): Call<DetectionResponse>

}