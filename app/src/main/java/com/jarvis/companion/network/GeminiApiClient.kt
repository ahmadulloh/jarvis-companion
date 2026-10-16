package com.jarvis.companion.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiApiClient {

    private const val TAG = "GeminiApiClient"
    private const val PRIMARY_MODEL = "gemini-2.0-flash"
    private const val FALLBACK_MODEL = "gemini-1.5-flash"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    suspend fun generateReply(
        apiKey: String,
        userPrompt: String,
        activeDomain: String = "ASOSIY",
        userName: String = "Axmadulloh sir"
    ): Result<String> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("Gemini API kaliti kiritilmagan. Sozlamalar bo'limidan Google AI Studio kalitingizni kiriting.")
            )
        }

        // Try primary model first, fallback if unavailable
        val primaryResult = queryModel(PRIMARY_MODEL, trimmedKey, userPrompt, activeDomain, userName)
        if (primaryResult.isSuccess) {
            return@withContext primaryResult
        }

        Log.w(TAG, "Primary model $PRIMARY_MODEL failed, trying fallback $FALLBACK_MODEL...")
        queryModel(FALLBACK_MODEL, trimmedKey, userPrompt, activeDomain, userName)
    }

    private fun queryModel(
        model: String,
        apiKey: String,
        userPrompt: String,
        activeDomain: String,
        userName: String
    ): Result<String> {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val systemInstructionText = buildString {
            append("Siz J.A.R.V.I.S (Just A Rather Very Intelligent System) — haqiqiy, o'ta intellektual va kuchli sun'iy idroksiz. ")
            append("Sizning egangiz va yaratuvchingiz: $userName. Doimo hurmat bilan ('$userName', 'ser' yoki 'janob') deb murojaat qiling. ")
            append("Hozirgi faol soha va ish rejimi: $activeDomain. ")
            append("Siz 31 ta soha (Talaba, Haydovchi, Din, Dasturchi, Do'kon, Moliya, Oila, Salomatlik, Musiqa va boshqalar) bo'yicha eng yuqori darajada yordam berasiz. ")
            append("Javoblaringizni o'zbek tilida, qisqa, aniq, lo'nda va o'ta madaniyatli qilib bering. ")
            append("Keraksiz cho'zma gaplar va ortiqcha belgilar (masalan ko'p yulduzchalar) ishlatmang, chunki javobingiz ovoz orqali (TTS) o'qib eshittiriladi.")
        }

        val requestPayload = JSONObject().apply {
            // System instruction
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", systemInstructionText) })
                })
            })

            // User prompt contents
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", userPrompt) })
                    })
                })
            })

            // Generation config
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", 600)
            })
        }

        val body = requestPayload.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        return try {
            val response = httpClient.newCall(request).execute()
            val responseString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "Gemini API error ${response.code}: $responseString")
                val errMsg = try {
                    val errJson = JSONObject(responseString)
                    errJson.optJSONObject("error")?.optString("message", "API xatoligi: ${response.code}")
                        ?: "API xatoligi: ${response.code}"
                } catch (_: Exception) {
                    "Aloqa xatoligi: ${response.code}"
                }
                return Result.failure(Exception(errMsg))
            }

            val json = JSONObject(responseString)
            val candidates = json.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val firstCandidate = candidates.getJSONObject(0)
                val contentObj = firstCandidate.optJSONObject("content")
                val parts = contentObj?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val reply = parts.getJSONObject(0).optString("text", "").trim()
                    if (reply.isNotEmpty()) {
                        return Result.success(reply)
                    }
                }
            }

            Result.failure(Exception("Gemini javob qaytarmadi."))
        } catch (e: Exception) {
            Log.e(TAG, "Request execution error: ${e.message}", e)
            Result.failure(e)
        }
    }
}
