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
import android.util.Log
import androidx.core.content.ContextCompat
import com.jarvis.companion.service.JarvisNotificationListener
import java.util.Calendar
import java.util.regex.Pattern

class PhoneActionManager(private val context: Context) {

    companion object {
        private const val TAG = "PhoneActionManager"

        /**
         * Uzbek Cyrillic to Latin transliteration.
         */
        fun cyrillicToLatin(input: String): String {
            var s = input.lowercase()
            val pairs = listOf(
                "ў" to "o'", "ғ" to "g'", "ш" to "sh", "ч" to "ch", "ю" to "yu",
                "я" to "ya", "ё" to "yo", "ж" to "j", "х" to "x", "ҳ" to "h",
                "қ" to "q", "ц" to "ts", "щ" to "sh", "э" to "e", "ъ" to "'",
                "а" to "a", "б" to "b", "в" to "v", "г" to "g", "д" to "d",
                "е" to "e", "з" to "z", "и" to "i", "й" to "y", "к" to "k",
                "л" to "l", "м" to "m", "н" to "n", "о" to "o", "п" to "p",
                "р" to "r", "с" to "s", "т" to "t", "у" to "u", "ф" to "f",
                "ы" to "i", "ь" to ""
            )
            for ((cyr, lat) in pairs) {
                s = s.replace(cyr, lat)
            }
            return s
        }

        /**
         * Uzbek Latin to Cyrillic transliteration.
         */
        fun latinToCyrillic(input: String): String {
            var s = input.lowercase()
            val pairs = listOf(
                "o'" to "ў", "oʻ" to "ў", "o`" to "ў", "o’" to "ў",
                "g'" to "ғ", "gʻ" to "ғ", "g`" to "ғ", "g’" to "ғ",
                "sh" to "ш", "ch" to "ч", "yu" to "ю", "ya" to "я", "yo" to "ё",
                "ts" to "ц",
                "a" to "а", "b" to "б", "v" to "в", "g" to "г", "d" to "д",
                "e" to "е", "j" to "ж", "z" to "з", "i" to "и", "y" to "й",
                "k" to "к", "l" to "л", "m" to "м", "n" to "н", "o" to "о",
                "p" to "п", "r" to "р", "s" to "с", "t" to "т", "u" to "у",
                "f" to "ф", "x" to "х", "h" to "ҳ", "q" to "қ"
            )
            for ((lat, cyr) in pairs) {
                s = s.replace(lat, cyr)
            }
            return s
        }

        /**
         * Normalize text for searching: lowercase, transliterate Cyrillic to Latin, remove non-alphanumeric.
         */
        fun normalizeForSearch(text: String): String {
            val lat = cyrillicToLatin(text.trim())
            return lat.replace(Regex("[^a-z0-9]"), "")
        }
    }

    /**
     * Common relationship synonym groups across Uzbek Latin, Uzbek Cyrillic, Russian and English.
     */
    private val relationshipGroups: List<Set<String>> = listOf(
        // ONA
        setOf(
            "ona", "onam", "onajon", "onajonim", "oyim", "oyi", "oyijon", "oyijonim", "valida", "validam",
            "она", "онам", "онажон", "онажоним", "ойым", "ойи", "ойижон", "волида", "волидам",
            "мама", "мамочка", "мамуля", "мать", "мамка", "mother", "mom", "mum"
        ),
        // DADA / OTA
        setOf(
            "dada", "dadam", "dadajon", "dadajonim", "ota", "otam", "otajon", "otajonim", "padar", "padarim",
            "дада", "дадам", "дадажон", "дадажоним", "ота", "отам", "отажон", "отажоним", "падар", "падарим",
            "папа", "папочка", "папуля", "отец", "батя", "папка", "father", "dad"
        ),
        // AKA
        setOf(
            "aka", "akam", "akajon", "akajonim", "akaxon",
            "ака", "акам", "акажон", "акажоним", "акахон",
            "брат", "братик", "братишка", "старший брат", "brother", "bro"
        ),
        // UKA
        setOf(
            "uka", "ukam", "ukajon", "ukajonim", "inim",
            "ука", "укам", "укажон", "укажоним", "иним",
            "младший брат", "братишка"
        ),
        // OPA
        setOf(
            "opa", "opam", "opajon", "opajonim", "opaxon",
            "опа", "опам", "опажон", "опажоним", "опахон",
            "сестра", "сестренка", "сестричка", "старшая сестра", "sister", "sis"
        ),
        // SINGIL
        setOf(
            "singil", "singlim", "singiljon", "singiljonim",
            "сингил", "синглим", "сингилжон", "сингилжоним",
            "младшая сестра"
        ),
        // AYOL / XOTIN
        setOf(
            "ayol", "ayolim", "rafiqam", "xotin", "xotinim", "juftim", "umryoldoshim",
            "аёл", "аёлим", "рафиқам", "хотин", "хотиним", "жуфтим", "умрйўлдошим",
            "жена", "женушка", "супруга", "wife"
        ),
        // ER / XO'JAYIN
        setOf(
            "er", "erim", "xojam", "xojayinim", "turmush ortogim",
            "эр", "эрим", "хўжам", "хўжайиним", "турмуш ўртоғим",
            "муж", "супруг", "husband"
        ),
        // TOG'A
        setOf(
            "toga", "togam", "togajon", "to'g'a", "to'g'am", "to'g'ajon",
            "тоға", "тоғам", "тоғажон", "тога", "тогам", "дядя"
        ),
        // AMAKI
        setOf(
            "amaki", "amakim", "amakijon",
            "амаки", "амаким", "амакижон", "дядя"
        ),
        // XOLA
        setOf(
            "xola", "xolam", "xolajon",
            "хола", "холам", "холажон", "тётя", "тетя"
        ),
        // AMMA
        setOf(
            "amma", "ammam", "ammajon",
            "амма", "аммам", "аммажон", "тётя", "тетя"
        ),
        // BUVI
        setOf(
            "buvi", "buvim", "buvijon", "momo", "momom",
            "буви", "бувим", "бувижон", "момо", "момом",
            "бабушка", "бабуля", "grandma", "grandmother"
        ),
        // BOBO
        setOf(
            "bobo", "bobom", "bobojon", "katta dada", "katta ota",
            "бобо", "бобом", "бобожон",
            "дедушка", "дедуля", "дед", "grandpa", "grandfather"
        )
    )

    private fun stripUzbekSuffixes(word: String): List<String> {
        val w = word.trim().lowercase()
        val results = mutableListOf(w)
        val suffixes = listOf(
            "larga", "larim", "lar", "ning", "mga", "ngga", "imizga",
            "ga", "ka", "qa", "ni", "dan", "da", "im", "m"
        )
        for (suff in suffixes) {
            if (w.endsWith(suff) && w.length > suff.length + 2) {
                val stripped = w.substring(0, w.length - suff.length)
                results.add(stripped)
            }
        }
        return results.distinct()
    }

    private fun generateSearchVariants(query: String): List<String> {
        val qClean = query.trim().lowercase()
        val stems = stripUzbekSuffixes(qClean)
        val variants = mutableSetOf<String>()

        for (stem in stems) {
            variants.add(stem)
            variants.add(latinToCyrillic(stem))
            variants.add(cyrillicToLatin(stem))

            for (group in relationshipGroups) {
                val matchesGroup = group.any {
                    it.equals(stem, ignoreCase = true) ||
                    normalizeForSearch(it) == normalizeForSearch(stem)
                }
                if (matchesGroup) {
                    variants.addAll(group)
                }
            }
        }
        return variants.toList()
    }

    /**
     * Search contacts by name, query or relationship in Uzbek Latin, Uzbek Cyrillic or Russian.
     */
    @SuppressLint("Range")
    fun searchContact(query: String): Pair<String, String>? {
        val qClean = query.trim().lowercase()
        if (qClean.isEmpty()) return null

        val hasReadContacts = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasReadContacts) {
            Log.w(TAG, "READ_CONTACTS permission is missing!")
            return null
        }

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

            val variants = generateSearchVariants(qClean)
            Log.d(TAG, "searchContact: query='$qClean', variants=$variants, totalContacts=${allContacts.size}")

            val candidates = allContacts.map { contact ->
                val name = contact.first.trim()
                val normName = normalizeForSearch(name)

                var bestScore = 0
                for (v in variants) {
                    val vClean = v.trim().lowercase()
                    val normV = normalizeForSearch(vClean)

                    if (name.equals(vClean, ignoreCase = true)) {
                        bestScore = maxOf(bestScore, 100) // Exact match in text
                    } else if (normName.isNotEmpty() && normName == normV) {
                        bestScore = maxOf(bestScore, 95) // Exact match normalized (Cyrillic <-> Latin)
                    } else if (name.split(Regex("[\\s()_\\-.]+")).any { it.equals(vClean, ignoreCase = true) }) {
                        bestScore = maxOf(bestScore, 90) // Whole word exact match: e.g. "Онам (Uy)"
                    } else if (name.split(Regex("[\\s()_\\-.]+")).any { normalizeForSearch(it) == normV }) {
                        bestScore = maxOf(bestScore, 85) // Whole word normalized match
                    } else if (name.contains(vClean, ignoreCase = true)) {
                        bestScore = maxOf(bestScore, 75) // Text contains
                    } else if (normName.contains(normV) && normV.length >= 3) {
                        bestScore = maxOf(bestScore, 65) // Normalized contains
                    } else if (normV.contains(normName) && normName.length >= 3) {
                        bestScore = maxOf(bestScore, 55) // Reverse contains
                    }
                }
                Triple(contact, bestScore, name)
            }

            val best = candidates.filter { it.second > 0 }.maxByOrNull { it.second }
            if (best != null) {
                Log.d(TAG, "Found contact: ${best.third} (score=${best.second})")
                return best.first
            }

        } catch (e: Exception) {
            Log.e(TAG, "searchContact exception: ${e.message}", e)
        }

        return null
    }

    /**
     * Resolve contact name by incoming phone number.
     */
    @SuppressLint("Range")
    fun searchContactByNumber(rawNumber: String): String? {
        val digits = rawNumber.replace("+", "").replace(" ", "").replace("-", "")
        if (digits.length < 7) return null

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
                null
            ) ?: return null

            cursor.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)) ?: ""
                    val num = c.getString(c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)) ?: ""
                    val numDigits = num.replace("+", "").replace(" ", "").replace("-", "")
                    if (numDigits.isNotEmpty() && (numDigits.endsWith(digits.takeLast(7)) || digits.endsWith(numDigits.takeLast(7)))) {
                        return name
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "searchContactByNumber error: ${e.message}")
        }
        return null
    }

    /**
     * Call contact or phone number.
     */
    fun makeCall(queryOrNumber: String): String {
        val q = queryOrNumber.trim()
        val digitsOnly = q.replace("+", "").replace(" ", "").replace("-", "")

        val hasReadContacts = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        val (targetName, targetNumber) = if (digitsOnly.length >= 7 && digitsOnly.all { it.isDigit() }) {
            Pair(q, q)
        } else {
            if (!hasReadContacts) {
                return "Kechirasiz, Axmadulloh sir, kontaktlaringizni o'qish uchun ilovaga 'Kontaktlar' (Contacts) ruxsatini berishingiz kerak."
            }
            val contact = searchContact(q)
            if (contact != null) {
                contact
            } else {
                return "Kechirasiz, Axmadulloh sir, kontaktlaringiz orasidan '$q' (lotin, kirill yoki ruscha shakllarida) topilmadi."
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

        val hasReadContacts = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        val (targetName, targetNumber) = if (digitsOnly.length >= 7 && digitsOnly.all { it.isDigit() }) {
            Pair(q, q)
        } else {
            if (!hasReadContacts) {
                return "Kechirasiz, Axmadulloh sir, kontaktlaringizni o'qish uchun ilovaga 'Kontaktlar' (Contacts) ruxsatini berishingiz kerak."
            }
            val contact = searchContact(q)
            if (contact != null) {
                contact
            } else {
                return "Kechirasiz, Axmadulloh sir, SMS yuborish uchun '$q' kontaktlar orasidan topilmadi."
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
