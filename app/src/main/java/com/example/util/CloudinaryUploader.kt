package com.example.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object CloudinaryUploader {
    private const val TAG = "CloudinaryUploader"
    private const val CLOUD_NAME = "jg8dnjho"
    private const val DEFAULT_PRESET = "ml_default"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    suspend fun uploadImage(
        imageBytes: ByteArray,
        filename: String,
        folder: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (imageBytes.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Image bytes cannot be empty"))
            }

            val url = "https://api.cloudinary.com/v1_1/$CLOUD_NAME/image/upload"
            val mediaType = "image/jpeg".toMediaTypeOrNull()
            val fileBody = imageBytes.toRequestBody(mediaType)

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", filename, fileBody)
                .addFormDataPart("upload_preset", DEFAULT_PRESET)
                .addFormDataPart("folder", folder)
                .build()

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "Cloudinary upload failed (HTTP ${response.code}): $responseBody")
                return@withContext Result.failure(Exception("Cloudinary upload failed (${response.code}): $responseBody"))
            }

            val json = JSONObject(responseBody)
            val secureUrl = json.optString("secure_url", "")
            if (secureUrl.isNotBlank()) {
                Result.success(secureUrl)
            } else {
                val urlField = json.optString("url", "")
                if (urlField.isNotBlank()) {
                    Result.success(urlField.replace("http://", "https://"))
                } else {
                    Result.failure(Exception("Cloudinary response missing secure_url: $responseBody"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cloudinary upload error", e)
            Result.failure(e)
        }
    }
}
