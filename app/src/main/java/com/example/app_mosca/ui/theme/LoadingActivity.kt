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
import com.example.app_mosca.models.DetectionResponse
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LoadingActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var loadingText: TextView
    private lateinit var imageFile: File
    private var startTime: Long = 0
    private lateinit var startTime2: String
    private lateinit var endTime2: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_loading)

        progressBar = findViewById(R.id.progressBar)

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        startTime2 = sdf.format(Date())

        loadingText = findViewById(R.id.loadingText)

        // Recibimos la imagen desde la actividad anterior
        val imageFilePath = intent.getStringExtra("imageFilePath") ?: return
        imageFile = File(imageFilePath)

        // Iniciar el proceso de carga de la imagen
        startTime = System.currentTimeMillis()
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
                    val idDetection = plagaResponse?.idDetection
                    val plaga = plagaResponse?.plaga

                    val endTime = System.currentTimeMillis()
                    val processingTimeInSeconds = (endTime - startTime) / 1000.0



                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val startTime2 = sdf.format(Date(startTime)) // formateas el inicio
                    val endTime2 = sdf.format(Date(endTime)) // formateas el fin
                    val timeDetection = processingTimeInSeconds.toFloat()

                    sendDetectionTime(idDetection!!, startTime2, endTime2, timeDetection,
                        onSuccess = {
                            // Ahora sí inicias la siguiente pantalla
                            if (plaga == true) {
                                val precision = plagaResponse.prediction_value
                                Log.d("LoadingActivity", "Valor de precision: $precision")
                                val intent = Intent(this@LoadingActivity, PlagaEncontrada::class.java)
                                intent.putExtra("imageFilePath", imageFile.absolutePath)
                                intent.putExtra("prediction", precision)
                                intent.putExtra("processingTime", processingTimeInSeconds)
                                startActivity(intent)
                            } else {
                                val intent2 = Intent(this@LoadingActivity, PlagaNoEncontrada::class.java)
                                intent2.putExtra("imageFilePath", imageFile.absolutePath)
                                intent2.putExtra("processingTime", processingTimeInSeconds)
                                startActivity(intent2)
                            }
                        },
                        onFailure = { error ->
                            // Manejo de error guardando tiempo (puedes loguear o mostrar mensaje)
                            Log.e("LoadingActivity", "Error guardando tiempo de detección: ${error.message}")
                            // Aunque falle el guardado, igual seguimos con la pantalla
                            if (plaga == true) {
                                val precision = plagaResponse.prediction_value
                                val intent = Intent(this@LoadingActivity, PlagaEncontrada::class.java)
                                intent.putExtra("imageFilePath", imageFile.absolutePath)
                                intent.putExtra("prediction", precision)
                                intent.putExtra("processingTime", processingTimeInSeconds)
                                startActivity(intent)
                            } else {
                                val intent2 = Intent(this@LoadingActivity, PlagaNoEncontrada::class.java)
                                intent2.putExtra("imageFilePath", imageFile.absolutePath)
                                intent2.putExtra("processingTime", processingTimeInSeconds)
                                startActivity(intent2)
                            }
                        }
                    )
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

    private fun sendDetectionTime(
        idDetection: Int,
        startTime2: String,
        endTime2: String,
        timeDetection: Float,
        onSuccess: () -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        val apiService = ApiClient.apiService
        val call = apiService.saveDetectionTime(idDetection, startTime2, endTime2, timeDetection)

        call.enqueue(object : Callback<DetectionResponse> {
            override fun onResponse(call: Call<DetectionResponse>, response: Response<DetectionResponse>) {
                if (response.isSuccessful) {
                    onSuccess()
                } else {
                    onFailure(Throwable("Error guardando tiempo: ${response.message()}"))
                }
            }

            override fun onFailure(call: Call<DetectionResponse>, t: Throwable) {
                onFailure(t)
            }
        })
    }
}