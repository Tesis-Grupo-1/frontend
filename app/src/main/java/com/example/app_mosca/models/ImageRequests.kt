package com.example.app_mosca.models

import okhttp3.MultipartBody

data class ImageRequests(
    val file: MultipartBody.Part
)
