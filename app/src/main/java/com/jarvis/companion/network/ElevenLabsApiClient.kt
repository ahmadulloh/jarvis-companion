package com.jarvis.companion.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object ElevenLabsApiClient {

    private const val TAG = "ElevenLabsApiClient"
    private const val BASE_URL = "https://api.elevenlabs.io/v1/text-to-speech"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    // Default female voice ID (Rachel - multilingual)
    const val DEFAULT_VOICE_ID = "21m00Tcm4TlvDq8ikWAM"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun synthesizeSpeechToFile(
        apiKey: String,
        text: String,
        voiceId: String = DEFAULT_VOICE_ID,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isEmpty()) {
            return@withContext Result.failure(Exception("ElevenLabs API kaliti kiritilmagan."))
        }

        try {
            val url = "$BASE_URL/$voiceId"
            val payload = JSONObject().apply {
                put("text", text)
                put("model_id", "eleven_multilingual_v2")
                put("voice_settings", JSONObject().apply {
                    put("stability", 0.5)
                    put("similarity_boost", 0.8)
                })
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("xi-api-key", trimmedKey)
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                Log.w(TAG, "ElevenLabs error ${response.code}: $errBody")
                return@withContext Result.failure(Exception("ElevenLabs xatolik ${response.code}: $errBody"))
            }

            val inputStream = response.body?.byteStream()
                ?: return@withContext Result.failure(Exception("Bo'sh audio javob olindi."))

            FileOutputStream(outputFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }

            Log.d(TAG, "ElevenLabs audio saved successfully: ${outputFile.length()} bytes")
            Result.success(outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "ElevenLabs exception: ${e.message}", e)
            Result.failure(e)
        }
    }
}
