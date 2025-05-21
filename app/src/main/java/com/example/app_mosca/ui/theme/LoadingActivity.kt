package com.example.app_mosca.ui.theme

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.app_mosca.R
import com.example.app_mosca.api.apiClient.ApiClient
import com.example.app_mosca.models.PlagaResponse
import com.example.app_mosca.models.UploadResponse
import com.example.app_mosca.ui.theme.PlagaNoEncontrada
import com.example.app_mosca.ui.theme.PlagaEncontrada
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File

class LoadingActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var loadingText: TextView
    private lateinit var imageFile: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_loading)

        progressBar = findViewById(R.id.progressBar)


        loadingText = findViewById(R.id.loadingText)

        // Recibimos la imagen desde la actividad anterior
        val imageFilePath = intent.getStringExtra("imageFilePath") ?: return
        imageFile = File(imageFilePath)

        // Iniciar el proceso de carga de la imagen
        processImage(imageFile)
    }

    // Método para iniciar el proceso de carga
    private fun processImage(imageFile: File) {
        progressBar.visibility = ProgressBar.VISIBLE

        // Llamamos al método para subir la imagen
        sendImageToApi(imageFile)
    }

    // Método para subir la imagen
    private fun sendImageToApi(imageFile: File) {
        val requestFile = RequestBody.create("image/jpeg".toMediaType(), imageFile)
        val body = MultipartBody.Part.createFormData("file", imageFile.name, requestFile)

        val apiService = ApiClient.apiService
        val uploadCall = apiService.uploadImage(body)

        uploadCall.enqueue(object : Callback<UploadResponse> {
            override fun onResponse(call: Call<UploadResponse>, response: Response<UploadResponse>) {
                if (response.isSuccessful) {
                    val uploadResponse = response.body()
                    val idImage = uploadResponse?.id_image

                    if (idImage != null) {
                        sendPredictionRequest(idImage, imageFile)
                    } else {
                        progressBar.visibility = ProgressBar.GONE
                        loadingText.text = "Error al obtener el ID de la imagen"
                    }
                } else {
                    progressBar.visibility = ProgressBar.GONE
                    loadingText.text = "Error al subir la imagen: ${response.message()}"
                }
            }

            override fun onFailure(call: Call<UploadResponse>, t: Throwable) {
                progressBar.visibility = ProgressBar.GONE
                loadingText.text = "Error de red: ${t.message}"
            }
        })
    }

    // Método para realizar la predicción
    private fun sendPredictionRequest(idImage: Int, imageFile: File) {
        val idImageRequestBody = RequestBody.create("text/plain".toMediaType(), idImage.toString())
        val requestFile = RequestBody.create("image/jpeg".toMediaType(), imageFile)
        val body = MultipartBody.Part.createFormData("file", imageFile.name, requestFile)

        val apiService = ApiClient.apiService
        val predictionCall = apiService.predictPlaga(idImageRequestBody, body)

        predictionCall.enqueue(object : Callback<PlagaResponse> {
            override fun onResponse(call: Call<PlagaResponse>, response: Response<PlagaResponse>) {
                progressBar.visibility = ProgressBar.GONE

                if (response.isSuccessful) {
                    val plagaResponse = response.body()
                    val plaga = plagaResponse?.plaga

                    if (plaga == true) {
                        val precision = plagaResponse.prediction_value
                        Log.d("LoadingActivity", "Valor de precision: ${precision}")
                        val intent = Intent(this@LoadingActivity, PlagaEncontrada::class.java)
                        intent.putExtra("imageFilePath", imageFile.absolutePath)  // También pasa la imagen si quieres mostrarla
                        intent.putExtra("prediction", precision)
                        startActivity(intent)
                    } else {
                        val intent2 = Intent(this@LoadingActivity, PlagaNoEncontrada::class.java)
                        intent2.putExtra("imageFilePath", imageFile.absolutePath)
                        startActivity(intent2)
                    }
                } else {
                    loadingText.text = "Error al predecir la imagen: ${response.message()}"
                }
            }

            override fun onFailure(call: Call<PlagaResponse>, t: Throwable) {
                progressBar.visibility = ProgressBar.GONE
                loadingText.text = "Error de red en la predicción: ${t.message}"
            }
        })
    }
}
