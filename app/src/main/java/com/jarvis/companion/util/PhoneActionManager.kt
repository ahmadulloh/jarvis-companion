package com.jarvis.companion.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.jarvis.companion.service.JarvisNotificationListener
import java.util.Calendar
import java.util.regex.Pattern

class PhoneActionManager(private val context: Context) {

    /**
     * Search contacts by name, query or relationship in Uzbek.
     */
    @SuppressLint("Range")
    fun searchContact(query: String): Pair<String, String>? {
        val qClean = query.trim().lowercase()
        if (qClean.isEmpty()) return null

        // Synonyms for common Uzbek relationships
        val searchVariants = when {
            qClean in listOf("onam", "ona", "oyim", "oyi", "onajon", "mom", "mother") ->
                listOf("ona", "onam", "oyim", "oyi", "onajon", "mom", "mother", "мама")
            qClean in listOf("dadam", "dada", "otam", "ota", "dadajon", "dad", "father") ->
                listOf("dada", "dadam", "otam", "ota", "dadajon", "dad", "father", "папа")
            qClean in listOf("akam", "aka", "akajon", "brother") ->
                listOf("aka", "akam", "akajon", "brother")
            qClean in listOf("ukam", "uka", "ukajon") ->
                listOf("uka", "ukam", "ukajon")
            qClean in listOf("opam", "opa", "opajon", "sister") ->
                listOf("opa", "opam", "opajon", "sister")
            qClean in listOf("singlim", "singil", "singiljon") ->
                listOf("singil", "singlim", "singiljon")
            else -> listOf(qClean)
        }

        val hasReadContacts = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasReadContacts) return null

        try {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            ) ?: return null

            val allContacts = mutableListOf<Pair<String, String>>()
            cursor.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)) ?: ""
                    val num = c.getString(c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)) ?: ""
                    if (name.isNotBlank() && num.isNotBlank()) {
                        allContacts.add(Pair(name, num.replace(" ", "").replace("-", "")))
                    }
                }
            }

            // 1. Exact match with search variants
            for (v in searchVariants) {
                val exact = allContacts.firstOrNull { it.first.trim().equals(v, ignoreCase = true) }
                if (exact != null) return exact
            }

            // 2. Contains match with search variants
            for (v in searchVariants) {
                val found = allContacts.firstOrNull { it.first.contains(v, ignoreCase = true) }
                if (found != null) return found
            }

            // 3. Normalized query contains
            val generic = allContacts.firstOrNull {
                it.first.contains(qClean, ignoreCase = true) || qClean.contains(it.first.lowercase())
            }
            if (generic != null) return generic

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    /**
     * Call contact or phone number.
     */
    fun makeCall(queryOrNumber: String): String {
        val q = queryOrNumber.trim()
        val digitsOnly = q.replace("+", "").replace(" ", "").replace("-", "")

        val (targetName, targetNumber) = if (digitsOnly.length >= 7 && digitsOnly.all { it.isDigit() }) {
            Pair(q, q)
        } else {
            val contact = searchContact(q)
            if (contact != null) {
                contact
            } else {
                return "Kechirasiz, Axmadulloh sir, kontaktlaringiz orasidan '$q' topilmadi."
            }
        }

        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val intent = if (hasCallPermission) {
            Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(targetNumber)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(targetNumber)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        try {
            context.startActivity(intent)
            return "Axmadulloh sir, $targetName ($targetNumber) raqamiga qo'ng'iroq qilinmoqda..."
        } catch (e: Exception) {
            return "Qo'ng'iroqni amalga oshirib bo'lmadi: ${e.message}"
        }
    }

    /**
     * Send SMS to contact or number.
     */
    fun sendSms(queryOrNumber: String, message: String): String {
        val q = queryOrNumber.trim()
        val digitsOnly = q.replace("+", "").replace(" ", "").replace("-", "")

        val (targetName, targetNumber) = if (digitsOnly.length >= 7 && digitsOnly.all { it.isDigit() }) {
            Pair(q, q)
        } else {
            val contact = searchContact(q)
            if (contact != null) {
                contact
            } else {
                return "Kechirasiz, Axmadulloh sir, SMS yuborish uchun '$q' topilmadi."
            }
        }

        val hasSmsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasSmsPermission) {
            return "SMS yuborish uchun dasturga SMS ruxsati berilmagan, sir."
        }

        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(targetNumber, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(targetNumber, null, message, null, null)
            }
            return "Axmadulloh sir, $targetName ($targetNumber) ga SMS yuborildi: '$message'"
        } catch (e: Exception) {
            return "SMS yuborishda xatolik yuz berdi: ${e.message}"
        }
    }

    /**
     * Set a reminder or alarm.
     */
    fun setReminder(title: String, timeOrMinutes: String): String {
        val t = timeOrMinutes.trim().lowercase()
        val label = if (title.isNotBlank()) title else "JARVIS Eslatmasi"

        // Check if minutes timer: e.g. "10 daqiqa", "30 minut", "15"
        val minMatcher = Pattern.compile("(\\d+)\\s*(daqiqa|minut|min)").matcher(t)
        if (minMatcher.find()) {
            val minutes = minMatcher.group(1)?.toIntOrNull() ?: 10
            val seconds = minutes * 60
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
                return "Axmadulloh sir, '$label' uchun $minutes daqiqaga taymer o'rnatildi."
            } catch (e: Exception) {
                return "Taymer o'rnatishda xatolik: ${e.message}"
            }
        }

        // Check if specific time: e.g. "08:30", "14:00", "8:00"
        val timeMatcher = Pattern.compile("(\\d{1,2})[:.](\\d{2})").matcher(t)
        val (hour, minute) = if (timeMatcher.find()) {
            val h = timeMatcher.group(1)?.toIntOrNull() ?: 8
            val m = timeMatcher.group(2)?.toIntOrNull() ?: 0
            Pair(h, m)
        } else {
            // Default: 1 hour from now
            val cal = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }
            Pair(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        }

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
            val timeStr = String.format("%02d:%02d", hour, minute)
            return "Axmadulloh sir, soat $timeStr ga '$label' nomli budilnik/eslatma o'rnatildi."
        } catch (e: Exception) {
            return "Budilnik o'rnatishda xatolik: ${e.message}"
        }
    }

    /**
     * Get recent personal Telegram messages.
     */
    fun getTelegramMessages(limit: Int = 5): String {
        if (!JarvisNotificationListener.isNotificationAccessGranted(context)) {
            return "Axmadulloh sir, Telegram xabarlarini eshittirish uchun telefon sozlamalaridan Bildirishnomalarga ruxsat (Notification Access) berishingiz kerak."
        }

        val messages = JarvisNotificationListener.getRecentMessages(limit)
        if (messages.isEmpty()) {
            return "Axmadulloh sir, hozircha Telegram orqali yangi shaxsiy xabarlar kelmadi."
        }

        val sb = StringBuilder("Axmadulloh sir, Telegramdan kelgan so'nggi xabarlar:\n")
        messages.forEachIndexed { idx, msg ->
            sb.append("${idx + 1}. ${msg.sender}: \"${msg.text}\"\n")
        }
        return sb.toString().trim()
    }
}
