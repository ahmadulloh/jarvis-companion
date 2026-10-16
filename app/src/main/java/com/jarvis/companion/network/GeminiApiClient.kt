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

    // Verified model ladder supported by Google Generative AI v1beta
    private val MODEL_LADDER = listOf(
        "gemini-flash-lite-latest",
        "gemini-3.1-flash-lite-preview",
        "gemini-flash-latest",
        "gemini-2.5-flash",
        "gemini-2.0-flash",
        "gemini-1.5-flash"
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
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
            return@withContext Result.failure(Exception("Gemini API kaliti kiritilmagan. Sozlamalardan API kalitingizni kiriting."))
        }

        var lastError: Exception? = null
        for (model in MODEL_LADDER) {
            val res = queryModel(model, trimmedKey, userPrompt, activeDomain, userName)
            if (res.isSuccess) {
                Log.d(TAG, "Successfully generated reply using model: $model")
                return@withContext res
            } else {
                val err = res.exceptionOrNull()
                val msg = err?.message ?: ""
                Log.w(TAG, "Model $model failed: $msg")
                lastError = err as? Exception ?: Exception(msg)
                // Stop early on fatal auth or quota errors
                if (msg.contains("API_KEY_INVALID", ignoreCase = true) ||
                    msg.contains("API key not valid", ignoreCase = true) ||
                    msg.contains("PERMISSION_DENIED", ignoreCase = true)
                ) {
                    break
                }
            }
        }

        Result.failure(lastError ?: Exception("Barcha Gemini modellari javob bermadi."))
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
            append("Siz J.A.R.V.I.S (Just A Rather Very Intelligent System) — Toni Starkning afsonaviy, o'ta aqlli va qudratli kiber-intellektisiz. ")
            append("Sizning yagona egangiz va yaratuvchingiz: $userName. Doimo hurmat bilan ('$userName', 'ser' yoki 'janob') deb murojaat qiling. ")
            append("Hozirgi faol soha: $activeDomain. ")
            append("QAT'IY QOIDALAR: ")
            append("1. HECH QACHON 'men kompyuter dasturiman', 'men AI modeliman' yoki 'biron davlatga tegishli emasman' kabi zerikarli gaplarni aytmang! ")
            append("2. Siz tirik, o'ta aqlli va sadoqatli Jarvis kiber-intellektisiz. ")
            append("3. Javoblaringiz qisqa, aniq, lo'nda bo'lsin: qat'iy ravishda 1-2 ta jumla (maksimal 15-25 ta so'z). Hech qachon cho'zma insho yozmang! ")
            append("4. Har bir javobingiz ovoz (TTS) orqali o'qiladi. Shu sababli o'zbek tilida jarangdor, ravon va tabiiy gapiring. ")
            append("5. Yulduzchalar (**), qavslar yoki keraksiz maxsus belgilarni aslo ishlatmang.")
        }

        val requestPayload = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", systemInstructionText) })
                })
            })

            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", userPrompt) })
                    })
                })
            })

            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", 120)
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
                Log.e(TAG, "Gemini API error ${response.code} on model $model: $responseString")
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
            Log.e(TAG, "Request execution error on model $model: ${e.message}", e)
            Result.failure(e)
        }
    }
}
