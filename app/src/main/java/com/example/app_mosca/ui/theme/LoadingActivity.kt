package com.example.app_mosca.ui.theme

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import com.example.app_mosca.R
import com.example.app_mosca.api.apiClient.ApiClient
import com.example.app_mosca.models.DetectionResponse
import com.example.app_mosca.models.UploadResponse
import java.util.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.io.InputStream
import java.text.SimpleDateFormat

class LoadingActivity : ComponentActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var loadingText: TextView
    private lateinit var imageFile: File
    private lateinit var interpreter: Interpreter

    private var startTime: Long = 0
    private var confidenceGlobal: Float = 0f
    private var predictedLabelGlobal: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loading)

        // Inicializa los componentes de la interfaz
        progressBar = findViewById(R.id.progressBar)
        loadingText = findViewById(R.id.loadingText)

        // Obtén la URI de la imagen que fue pasada desde la actividad anterior
        val imagePath = intent.getStringExtra("imagePath")
        val imageUriString = intent.getStringExtra("imageUri")

        if (imagePath == null) {
            loadingText.text = "Error: No se recibió la ruta de la imagen."
            return
        }

        imageFile = File(imagePath)
        val imageUri: Uri = Uri.parse(imageUriString)

        if (!imageFile.exists()) {
            loadingText.text = "Error: El archivo no existe en: $imagePath"
            Log.e("LoadingActivity", "Archivo no encontrado: $imagePath")
            return
        }

        // Cargar el modelo TFLite
        try {
            interpreter = Interpreter(loadModelFile("modelo.tflite"))
        } catch (e: Exception) {
            loadingText.text = "Error cargando modelo: ${e.message}"
            return
        }

        // Inicia el proceso de predicción
        startTime = System.currentTimeMillis()
        progressBar.visibility = ProgressBar.VISIBLE

        // Procesar la imagen y hacer la predicción
        processImageAndPredict(imageUri)
    }

    private fun loadModelFile(modelName: String): ByteBuffer {
        val assetFileDescriptor = assets.openFd(modelName)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun processImageAndPredict(imageUri: Uri) {
        val bitmap = getBitmapFromUri(imageUri)
        val inputBuffer = preprocessImage(bitmap)
        val output = Array(1) { FloatArray(4) }  // Cambiado a 4 clases

        interpreter.run(inputBuffer, output)

        val endTime = System.currentTimeMillis()
        val processingTimeSeconds = (endTime - startTime) / 1000.0

        val probabilities = output[0]
        val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: -1
        val classLabels = listOf("plaga", "sana", "otros", "animales")  // Nuevas etiquetas agregadas
        val predictedLabel = if (maxIndex != -1) classLabels[maxIndex] else "desconocido"
        val confidence = if (maxIndex != -1) probabilities[maxIndex] else 0f

        Log.d("LoadingActivity", "Predicción: $predictedLabel con confianza $confidence")

        // Guardamos globales para usar luego
        confidenceGlobal = confidence
        predictedLabelGlobal = predictedLabel

        // Subir imagen y guardar detección
        uploadImageAndSaveDetection(processingTimeSeconds)
    }

    private fun getBitmapFromUri(uri: Uri): Bitmap {
        val inputStream: InputStream = contentResolver.openInputStream(uri)!!
        return BitmapFactory.decodeStream(inputStream)
    }

    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val inputSize = 224  // Tamaño esperado por el modelo
        val imgData = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        imgData.order(ByteOrder.nativeOrder())

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val intValues = IntArray(inputSize * inputSize)
        scaledBitmap.getPixels(intValues, 0, scaledBitmap.width, 0, 0, scaledBitmap.width, scaledBitmap.height)

        for (pixel in intValues) {
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f
            imgData.putFloat(r)
            imgData.putFloat(g)
            imgData.putFloat(b)
        }

        imgData.rewind()
        return imgData
    }

    private fun uploadImageAndSaveDetection(processingTimeSeconds: Double) {
        loadingText.text = "Cargando...."
        val requestFile = RequestBody.create("image/jpeg".toMediaType(), imageFile)
        val body = MultipartBody.Part.createFormData("file", imageFile.name, requestFile)

        val apiService = ApiClient.apiService
        val callUpload = apiService.uploadImage(body)

        callUpload.enqueue(object : Callback<UploadResponse> {
            override fun onResponse(call: Call<UploadResponse>, response: Response<UploadResponse>) {
                if (response.isSuccessful) {
                    val uploadResponse = response.body()
                    val imageId = uploadResponse?.id_image

                    if (imageId != null) {
                        saveDetectionTime(imageId, processingTimeSeconds)
                    } else {
                        progressBar.visibility = ProgressBar.GONE
                        loadingText.text = "Error: No se obtuvo ID de imagen."
                    }
                } else {
                    progressBar.visibility = ProgressBar.GONE
                    loadingText.text = "Error al subir imagen: ${response.message()}"
                }
            }

            override fun onFailure(call: Call<UploadResponse>, t: Throwable) {
                progressBar.visibility = ProgressBar.GONE
                loadingText.text = "Error de red al subir imagen: ${t.message}"
            }
        })
    }

    private fun saveDetectionTime(imageId: Int, processingTimeSeconds: Double) {
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateDetection = sdfDate.format(Date())

        val sdfTime = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        val timeInitial = sdfTime.format(Date(startTime))
        val timeFinal = sdfTime.format(Date(startTime + (processingTimeSeconds * 1000).toLong()))

        val apiService = ApiClient.apiService
        val callSave = apiService.saveDetectionTime(
            image_id = imageId,
            result = (predictedLabelGlobal == "plaga").toString(),
            prediction_value = confidenceGlobal.toString(),
            time_initial = timeInitial,
            time_final = timeFinal,
            date_detection = dateDetection
        )

        callSave.enqueue(object : Callback<DetectionResponse> {
            override fun onResponse(call: Call<DetectionResponse>, response: Response<DetectionResponse>) {
                progressBar.visibility = ProgressBar.GONE
                if (response.isSuccessful) {
                    // Redirigir a pantalla según resultado
                    if (predictedLabelGlobal == "plaga") {
                        val intent = Intent(this@LoadingActivity, PlagaEncontrada::class.java)
                        intent.putExtra("imageFilePath", imageFile.absolutePath)
                        intent.putExtra("prediction", confidenceGlobal)
                        intent.putExtra("processingTime", processingTimeSeconds)
                        startActivity(intent)
                    } else {
                        val intent = Intent(this@LoadingActivity, PlagaNoEncontrada::class.java)
                        intent.putExtra("imageFilePath", imageFile.absolutePath)
                        intent.putExtra("processingTime", processingTimeSeconds)
                        startActivity(intent)
                    }
                } else {
                    loadingText.text = "Error al guardar detección: ${response.message()}"
                }
            }

            override fun onFailure(call: Call<DetectionResponse>, t: Throwable) {
                progressBar.visibility = ProgressBar.GONE
                loadingText.text = "Error de red al guardar detección: ${t.message}"

                // Igual mostrar pantalla según resultado
                if (predictedLabelGlobal == "plaga" && confidenceGlobal >= 0.5f) {
                    val intent = Intent(this@LoadingActivity, PlagaEncontrada::class.java)
                    intent.putExtra("imageFilePath", imageFile.absolutePath)
                    intent.putExtra("prediction", confidenceGlobal)
                    intent.putExtra("processingTime", processingTimeSeconds)
                    startActivity(intent)
                } else {
                    val intent = Intent(this@LoadingActivity, PlagaNoEncontrada::class.java)
                    intent.putExtra("imageFilePath", imageFile.absolutePath)
                    intent.putExtra("processingTime", processingTimeSeconds)
                    startActivity(intent)
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        interpreter.close()
    }
}
