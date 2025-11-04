package com.example.app_mosca.services

import android.util.Log
import com.example.app_mosca.api.apiEndpoints.ApiService
import com.example.app_mosca.models.ImageUploadResponse
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class ImageUploadService(private val apiService: ApiService) {

    companion object {
        private const val TAG = "ImageUploadService"
        private const val BATCH_SIZE = 5 // Subir 5 imágenes en paralelo
        private const val MAX_RETRIES = 3
        private const val INITIAL_RETRY_DELAY_MS = 1000L
    }

    /**
     * 🔥 Sube múltiples imágenes en lotes asíncronos
     * @param imagePaths Lista de rutas de las imágenes a subir
     * @param confidences Lista de confianzas correspondientes a cada imagen
     * @return Result con lista de IDs de las imágenes subidas exitosamente
     */
    suspend fun uploadImagesInBatches(
        imagePaths: List<String>,
        confidences: List<Float>
    ): Result<List<Int>> = withContext(Dispatchers.IO) {
        try {
            if (imagePaths.isEmpty()) {
                Log.w(TAG, "⚠️ No hay imágenes para subir")
                return@withContext Result.success(emptyList())
            }

            val uploadedImageIds = mutableListOf<Int>()
            val chunkedImages = imagePaths.chunked(BATCH_SIZE)
            val chunkedConfidences = confidences.chunked(BATCH_SIZE)

            Log.d(TAG, "🚀 Iniciando subida de ${imagePaths.size} imágenes en ${chunkedImages.size} lotes")

            chunkedImages.forEachIndexed { batchIndex, batch ->
                val batchConfidences = chunkedConfidences.getOrNull(batchIndex) ?: listOf()

                Log.d(TAG, "📦 Procesando lote ${batchIndex + 1}/${chunkedImages.size} con ${batch.size} imágenes")

                // Subir lote en paralelo usando async
                val batchResults = batch.mapIndexed { index, imagePath ->
                    async {
                        val confidence = batchConfidences.getOrNull(index) ?: 0f
                        uploadImageWithRetry(imagePath, confidence)
                    }
                }.awaitAll()

                // Recolectar IDs exitosos
                batchResults.forEach { result ->
                    result.fold(
                        onSuccess = { imageId ->
                            uploadedImageIds.add(imageId)
                            Log.d(TAG, "  ✓ Imagen subida: ID=$imageId (Total: ${uploadedImageIds.size}/${imagePaths.size})")
                        },
                        onFailure = { error ->
                            Log.e(TAG, "  ✗ Error: ${error.message}")
                        }
                    )
                }

                Log.d(TAG, "✅ Lote ${batchIndex + 1} completado. Acumulado: ${uploadedImageIds.size}/${imagePaths.size}")

                // Pequeño delay entre lotes para no saturar el servidor
                if (batchIndex < chunkedImages.size - 1) {
                    delay(500L)
                }
            }

            Log.d(TAG, "🎉 Subida completa: ${uploadedImageIds.size}/${imagePaths.size} imágenes exitosas")

            if (uploadedImageIds.isEmpty()) {
                Result.failure(Exception("No se pudo subir ninguna imagen"))
            } else {
                Result.success(uploadedImageIds)
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error crítico en uploadImagesInBatches", e)
            Result.failure(e)
        }
    }

    /**
     * Sube una imagen individual con reintentos automáticos
     */
    private suspend fun uploadImageWithRetry(
        imagePath: String,
        plaguePercentage: Float,
        retries: Int = MAX_RETRIES
    ): Result<Int> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null

        repeat(retries) { attempt ->
            try {
                Log.d(TAG, "🔄 Intento ${attempt + 1}/$retries: $imagePath")

                val result = uploadSingleImage(imagePath, plaguePercentage)

                // Si es exitoso, retornar inmediatamente
                return@withContext result

            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "⚠️ Intento ${attempt + 1}/$retries falló: ${e.message}")

                // Si no es el último intento, esperar antes de reintentar
                if (attempt < retries - 1) {
                    val delayMs = INITIAL_RETRY_DELAY_MS * (attempt + 1)
                    Log.d(TAG, "   Esperando ${delayMs}ms antes del siguiente intento...")
                    delay(delayMs)
                }
            }
        }

        // Si llegamos aquí, todos los intentos fallaron
        Log.e(TAG, "❌ Todos los intentos fallaron para: $imagePath")
        Result.failure(lastException ?: Exception("Error desconocido al subir imagen"))
    }

    /**
     * Sube una imagen individual al servidor
     */
    private suspend fun uploadSingleImage(
        imagePath: String,
        plaguePercentage: Float
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val file = File(imagePath)

            // Validaciones
            if (!file.exists()) {
                throw Exception("Archivo no encontrado: $imagePath")
            }

            if (!file.canRead()) {
                throw Exception("No se puede leer el archivo: $imagePath")
            }

            val fileSizeKB = file.length() / 1024
            Log.d(TAG, "📤 Subiendo: ${file.name} (${fileSizeKB}KB, conf: ${(plaguePercentage * 100).toInt()}%)")

            // Crear el cuerpo del archivo (imagen)
            val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", file.name, requestFile)

            // Crear parte para el porcentaje de plaga
            val porcentajePlaga = plaguePercentage.toString()
            val porcentajeBody = porcentajePlaga.toRequestBody("text/plain".toMediaTypeOrNull())

            val response = apiService.uploadImage(filePart, porcentajeBody)

            if (response.isSuccessful) {
                val imageResponse = response.body()

                if (imageResponse != null) {
                    Log.d(TAG, "✅ ${file.name} → ID=${imageResponse.id_image}")
                    Result.success(imageResponse.id_image)
                } else {
                    throw Exception("Respuesta vacía del servidor")
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "Sin detalles"
                throw Exception("HTTP ${response.code()}: ${response.message()} - $errorBody")
            }

        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "❌ Sin conexión a internet")
            Result.failure(Exception("Sin conexión a internet"))

        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "❌ Tiempo de espera agotado")
            Result.failure(Exception("Tiempo de espera agotado"))

        } catch (e: java.io.IOException) {
            Log.e(TAG, "❌ Error de E/S: ${e.message}")
            Result.failure(Exception("Error de conexión: ${e.message}"))

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error subiendo $imagePath", e)
            Result.failure(e)
        }
    }

    /**
     * Método auxiliar para validar un archivo de imagen
     */
    private fun validateImageFile(file: File): Boolean {
        return file.exists() &&
                file.canRead() &&
                file.length() > 0 &&
                file.extension.lowercase() in listOf("jpg", "jpeg", "png")
    }
}