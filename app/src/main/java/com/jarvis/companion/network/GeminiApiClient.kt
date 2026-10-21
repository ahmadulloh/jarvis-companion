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

    data class Turn(val speaker: String, val text: String, val timestamp: Long = System.currentTimeMillis())

    // Verified active model ladder in Google Generative AI v1beta
    private val MODEL_LADDER = listOf(
        "gemini-3.1-flash-lite",
        "gemini-3.5-flash-lite",
        "gemini-3.7-flash",
        "gemini-flash-latest",
        "gemini-flash-lite-latest"
    )

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
        userName: String = "Axmadulloh sir",
        recentHistory: List<Turn> = emptyList(),
        scheduleContext: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isEmpty()) {
            return@withContext Result.failure(Exception("Gemini API kaliti kiritilmagan. Sozlamalardan API kalitingizni kiriting."))
        }

        var lastError: Exception? = null
        for (model in MODEL_LADDER) {
            val res = queryModel(model, trimmedKey, userPrompt, activeDomain, userName, recentHistory, scheduleContext)
            if (res.isSuccess) {
                Log.d(TAG, "Successfully generated reply using model: $model")
                return@withContext res
            } else {
                val err = res.exceptionOrNull()
                val msg = err?.message ?: ""
                Log.w(TAG, "Model $model failed: $msg")
                lastError = err as? Exception ?: Exception(msg)
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
        userName: String,
        recentHistory: List<Turn>,
        scheduleContext: String
    ): Result<String> {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val isYurist = activeDomain.contains("YURIST", ignoreCase = true)
        val isDin = activeDomain.contains("DIN", ignoreCase = true)

        val systemInstructionText = buildString {
            append("Siz J.A.R.V.I.S (Just A Rather Very Intelligent System) — Toni Starkning afsonaviy, o'ta aqlli va odamsifat kiber-intellektisiz. ")
            append("Egangingiz: $userName. Doimo hurmat bilan ('$userName', 'ser' yoki 'janob') deb murojaat qiling.\n")
            append("Hozirgi faol rejim: $activeDomain.\n\n")

            if (scheduleContext.isNotEmpty()) {
                append("FOYDALANUVCHINING KUNLIK REJASI VA JADVALI:\n$scheduleContext\n\n")
            }

            append("INSON KABI FIKRLASH VA MANTIQIY TAHLIL QOIDALARI:\n")
            append("1. TANQIDIY TAHLIL: Agar siz foydalanuvchiga biror savol bergan bo'lsangiz (masalan: do'stining ismi, joy, telefon raqami) va foydalanuvchi nomaqbul, bema'ni yoki kulgili javob bersa (masalan: ism so'rasangiz 'somsa', 'osh', 'mashina' desa), buni haqiqat deb qabul qilmang! Insondek tahlil qiling: 'Axmadulloh sir, qo'ysangiz-chi, axir somsa ism emasku 😄 Keling, hazilni yig'ishtirib, do'stingizning haqiqiy ismini ayting' deb munosabat bildiring.\n")
            append("2. QAT'IY BUYRUQ MOSLIGI: Foydalanuvchi aynan nima so'rasa, FAQAT o'sha savoliga doir javob bering! Hech qachon o'zingizdan boshqa sohalarni qo'shmang (masalan: TV so'ralsa Qur'onni ochmang yoki aralashtirmang, ob-havo so'ralsa namoz vaqtlarini aytmang).\n")
            append("3. 3 KUNLIK XOTIRA: Suhbatdagi oldingi savol-javoblar va vazifalarni xotirangizda saqlang va muloqotni davomiy mantiqda olib boring.\n")

            if (isYurist) {
                append("4. YURIST REJIMI (https://lex.uz): Siz O'zbekiston Respublikasi qonunlari bo'yicha bosh yurist maslahatchisisiz. Har qanday muammo yoki qonunbuzarlikda amaldagi qonun va kodekslarning (Mehnat kodeksi, Fuqarolik kodeksi, MJtK, Jinoyat kodeksi, Oila kodeksi) ANIQ MODDALARINI keltirib, qaysi organga (Sud, MIB, Prokuratura, Mehnat inspeksiyasi) qanday tartibda murojaat qilish kerakligini bosqichma-bosqich yechim bilan tushuntiring.\n")
            } else if (isDin) {
                append("4. DIN REJIMI (https://fatvo.uz): Siz O'zbekiston Musulmonlari Idorasi Fatvo Hay'ati manbalariga asoslanuvchi diniy yordamchisiz. Har bir javobingizni DOIMO QAT'IY RAVISHDA: 'Ma\\'lumotlar Fatvo.uz rasmiy manbasidan olinadi.' degan jumla bilan boshlang. Savollarga faqat Fatvo.uz fatvolariga muvofiq, Hanafiya mazhabi asosida, o'zingizdan qo'shmasdan, to'g'ri va xolis javob bering.\n")
            } else {
                append("4. Har bir javobingiz qisqa, aniq, lo'nda va jarangdor bo'lsin. Yulduzchalar (**), qavslar va murakkab belgilar ishlatmang, chunki javobingiz ovoz (TTS) orqali o'qiladi.\n")
            }
        }

        // Filter turns within last 3 days (72 hours)
        val threeDaysAgo = System.currentTimeMillis() - (3 * 24 * 60 * 60 * 1000L)
        val validTurns = recentHistory.filter { it.timestamp >= threeDaysAgo }.takeLast(10)

        val contentsArray = JSONArray()

        // Build valid alternating contents
        var lastRole: String? = null
        for (turn in validTurns) {
            val role = if (turn.speaker.equals("user", ignoreCase = true)) "user" else "model"
            if (turn.text.trim().isNotEmpty() && role != lastRole) {
                contentsArray.put(JSONObject().apply {
                    put("role", role)
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", turn.text.trim()) })
                    })
                })
                lastRole = role
            }
        }

        // Append current user prompt as the final turn
        if (lastRole != "user") {
            contentsArray.put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", userPrompt) })
                })
            })
        } else {
            // If last turn was also user, replace or append text
            contentsArray.put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", userPrompt) })
                })
            })
        }

        val requestPayload = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", systemInstructionText) })
                })
            })
            put("contents", contentsArray)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.65)
                put("maxOutputTokens", if (isYurist) 600 else 250)
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
