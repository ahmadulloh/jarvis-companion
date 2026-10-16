package com.jarvis.companion.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object UzbekVoiceApiClient {

    private const val TAG = "UzbekVoiceApiClient"
    private const val TTS_URL = "https://uzbekvoice.ai/api/v1/tts"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    /**
     * Synthesizes Uzbek text using UzbekVoice.ai API and returns direct CDN audio URL.
     * Model "sevinch" provides high quality authentic Uzbek female voice.
     */
    suspend fun synthesizeSpeech(
        apiKey: String,
        text: String,
        model: String = "sevinch"
    ): Result<String> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isEmpty()) {
            return@withContext Result.failure(Exception("UzbekVoice API kaliti topilmadi."))
        }

        try {
            val payload = JSONObject().apply {
                put("text", text)
                put("model", model)
            }

            val request = Request.Builder()
                .url(TTS_URL)
                .addHeader("Authorization", trimmedKey)
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.w(TAG, "UzbekVoice HTTP error ${response.code}: $responseBody")
                return@withContext Result.failure(Exception("UzbekVoice xatolik: ${response.code} $responseBody"))
            }

            val json = JSONObject(responseBody)
            val status = json.optString("status", "")

            if (status.equals("SUCCESS", ignoreCase = true) || status.equals("COMPLETED", ignoreCase = true)) {
                val resultObj = json.optJSONObject("result")
                val audioUrl = resultObj?.optString("url") ?: json.optString("url", "")
                if (audioUrl.isNotEmpty()) {
                    Log.d(TAG, "UzbekVoice audio URL ready: $audioUrl")
                    return@withContext Result.success(audioUrl)
                }
            }

            // In case of async processing or other status
            val err = json.optString("error", "Ovoz generatsiyasida xatolik")
            Result.failure(Exception(err))
        } catch (e: Exception) {
            Log.e(TAG, "UzbekVoice request exception: ${e.message}", e)
            Result.failure(e)
        }
    }
}
