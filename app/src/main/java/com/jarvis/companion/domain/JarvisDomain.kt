package com.jarvis.companion.domain

import android.graphics.Color

data class DomainAction(
    val id: String,
    val title: String,
    val icon: String,
    val promptCommand: String
)

data class JarvisDomain(
    val id: String,
    val titleUz: String,
    val icon: String,
    val category: String,
    val primaryColor: Int,
    val glowColor: Int,
    val secondaryColor: Int,
    val description: String,
    val motto: String,
    val keywords: List<String>,
    val actions: List<DomainAction>,
    val systemPersonaPrompt: String
)

object JarvisDomainRegistry {

    val ASOSIY = JarvisDomain(
        id = "ASOSIY",
        titleUz = "Asosiy Neyron AI",
        icon = "⚡",
        category = "Universal",
        primaryColor = Color.parseColor("#00E5FF"),
        glowColor = Color.parseColor("#00B0FF"),
        secondaryColor = Color.parseColor("#80D8FF"),
        description = "Universal kiber-intellekt: qo'ng'iroqlar, xabarlar, eslatmalar, qidiruv va doimiy ovozli yordamchi.",
        motto = "Universal kiber-intellekt faol. Labbay, Axmadulloh sir!",
        keywords = listOf("asosiy", "standart", "bosh", "oddiy", "bosh sahifa", "asosiy rejim"),
        actions = listOf(
            DomainAction("act_call", "Onamga telefon", "📞", "Onamga telefon qil"),
            DomainAction("act_tg", "Telegram", "💬", "Telegramdagi shaxsiy xabarlarni o'qib ber"),
            DomainAction("act_reminder", "Eslatma", "⏰", "Eslatma o'rnat"),
            DomainAction("act_status", "Tizim holati", "⚡", "JARVIS tizim holati qanday?")
        ),
        systemPersonaPrompt = "You are JARVIS, Tony Stark's advanced personal AI assistant for Axmadulloh sir. Be concise, respectful, ultra-intelligent, and execute tasks directly."
    )

    val TALABA = JarvisDomain(
        id = "TALABA",
        titleUz = "Talaba",
        icon = "🎓",
        category = "Ta'lim",
        primaryColor = Color.parseColor("#7C4DFF"),
        glowColor = Color.parseColor("#651FFF"),
        secondaryColor = Color.parseColor("#B388FF"),
        description = "Dars jadvali, deadline eslatmalari; ma'ruza audiosini konspektga aylantirish; YouTube dars/PDF xulosasi; quiz va flashcard; imtihongacha kunlik o'qish rejasi.",
        motto = "Talaba rejimi faol. Ma'ruzalar, dars jadvali va konspektlar nazoratda.",
        keywords = listOf("talaba", "talabalik", "student", "dars", "oqish", "ma'ruza", "konspekt", "imtihon"),
        actions = listOf(
            DomainAction("act_conspect", "Audio Konspekt", "🎙️", "Ma'ruza yozishni boshla va asosiy konspekt qilib ber"),
            DomainAction("act_schedule", "Dars Jadvali", "📅", "Bugungi dars jadvali va deadline'larimni ayt"),
            DomainAction("act_quiz", "Tezkor Quiz", "⚡", "Oxirgi mavzu bo'yicha 5 ta test tuz"),
            DomainAction("act_summary", "PDF/Video Xulosa", "📄", "Ushbu darsning qisqa xulosasini tayyorla")
        ),
        systemPersonaPrompt = "You are JARVIS Academic Mentor. Help Axmadulloh sir study efficiently: summarize lectures, format crisp study notes, generate flashcards/quizzes, and track exam schedules."
    )

    val HAYDOVCHI = JarvisDomain(
        id = "HAYDOVCHI",
        titleUz = "Haydovchi",
        icon = "🚗",
        category = "Transport",
        primaryColor = Color.parseColor("#FFB300"),
        glowColor = Color.parseColor("#FFA000"),
        secondaryColor = Color.parseColor("#FFE082"),
        description = "To'liq qo'llarsiz ovozli boshqaruv; marshrut va tirbandlik; yoqilg'i, daromad va xarajat hisobi; buyurtmalar navbati; texnik ko'rik/sug'urta eslatmasi.",
        motto = "Haydovchi rejimi faol. Xavfsiz safar tilayman, Axmadulloh sir!",
        keywords = listOf("haydovchi", "haydovchilik", "mashina", "shofyor", "taksi", "yo'l", "rul"),
        actions = listOf(
            DomainAction("act_drive_call", "Ovozda Qo'ng'iroq", "📞", "Menga eng so'nggi qo'ng'iroq qilgan odamga qaytar"),
            DomainAction("act_traffic", "Tirbandlik & Yo'l", "🗺️", "Hozirgi yo'nalish bo'yicha tirbandlik va marshrut qanday?"),
            DomainAction("act_fuel", "Yoqilg'i & Xarajat", "⛽", "Yoqilg'i quyish xarajatini qayd et"),
            DomainAction("act_speed", "Qoidalar & SOS", "🚨", "Tezlik chegarasi va radarlar haqida ogohlantir")
        ),
        systemPersonaPrompt = "You are JARVIS Co-Pilot in Driver Mode. Axmadulloh sir is driving. Keep responses strictly concise, crisp, and 100% hands-free. Help with navigation, safe calling, expense tracking, and traffic."
    )

    val DIN = JarvisDomain(
        id = "DIN",
        titleUz = "Din / Qur'on",
        icon = "🕌",
        category = "Ma'naviyat",
        primaryColor = Color.parseColor("#00E5FF"),
        glowColor = Color.parseColor("#00E676"),
        secondaryColor = Color.parseColor("#69F0AE"),
        description = "Qur'on ustozi: tajvid, real vaqt xatolarni topish, 0 dan arab alifbosi, qog'oz Mushaf kamera nazorati; namoz vaqtlari.",
        motto = "Din va Qur'on rejimi faol. Qalblar zikr ila orom olur.",
        keywords = listOf("din", "quron", "qur'on", "namoz", "islom", "azon", "zikr", "roza", "ro'za", "mushaf", "tajvid", "alifbo"),
        actions = listOf(
            DomainAction("act_quron_open", "Qur'on Ustozi", "📖", "Qur'on ustozi ilovasini och"),
            DomainAction("act_quron_0dan", "0 dan Harflar", "🔤", "Qur'onni 0 dan harflardan boshlab o'rganamiz"),
            DomainAction("act_quron_takror", "Qiroat & Xatolar", "🎙️", "Fotiha surasini qiroat qilib xatolarimni tekshir"),
            DomainAction("act_quron_kamera", "Kamerada Mushaf", "📷", "Kamerani yoqib qog'oz Mushafimni kuzatib xatolarni aytib tur")
        ),
        systemPersonaPrompt = "You are JARVIS Spiritual Companion and Quran Teacher for Axmadulloh sir. Assist with Quran recitations, tajweed error detection, 0-dan Arabic alphabet learning, and camera tracking for physical Mushaf."
    )

    val DASTURCHI = JarvisDomain(
        id = "DASTURCHI",
        titleUz = "Dasturchi",
        icon = "💻",
        category = "IT & Dasturlash",
        primaryColor = Color.parseColor("#00E676"),
        glowColor = Color.parseColor("#00FF9D"),
        secondaryColor = Color.parseColor("#B9F6CA"),
        description = "Kod tushuntirish va xatoni topish; Git holati va PR xulosasi; log tahlili; ovozda terminal buyruqlari (tasdiq bilan).",
        motto = "Dasturchi kiber-rejimi faol. Terminal, Git va kod analizatori tayyor.",
        keywords = listOf("dasturchi", "kod", "programist", "it", "developer", "git", "terminal", "python", "kotlin"),
        actions = listOf(
            DomainAction("act_debug", "Kod Xatosi", "🐞", "Bu kod xatosini tahlil qil va tuzatib ber"),
            DomainAction("act_git", "Git Holati", "🌿", "Git status va oxirgi commitlarni tekshir"),
            DomainAction("act_term", "Terminal", "💻", "Server holatini tekshiruvchi buyruq ber"),
            DomainAction("act_log", "Log Tahlili", "📊", "Xatolik loglarini filtrlab xulosa qil")
        ),
        systemPersonaPrompt = "You are JARVIS Senior Software Architect. Provide precise, production-grade code explanations, debug stack traces, review Git PRs, and suggest architectural best practices for Axmadulloh sir."
    )

    val DOKON = JarvisDomain(
        id = "DOKON",
        titleUz = "Do'kon",
        icon = "🛒",
        category = "Biznes",
        primaryColor = Color.parseColor("#FFD600"),
        glowColor = Color.parseColor("#FF9100"),
        secondaryColor = Color.parseColor("#FFE57F"),
        description = "“5 ta non sotildi” desa ombor avtomatik kamayadi; qoldiq tugashidan oldin buyurtma; kunlik tushum/foyda hisoboti; qarzdorlar ro'yxati va eslatma; chek rasmidan xarajat yozish.",
        motto = "Do'kon boshqaruvi faol. Ombor, savdo va kassa hisob-kitobi nazoratda.",
        keywords = listOf("dokon", "do'kon", "savdo", "magazin", "ombor", "kassa", "qarz", "tushum"),
        actions = listOf(
            DomainAction("act_sale", "Savdo Qayd Etish", "🛒", "5 ta non sotildi deb yozib qo'y"),
            DomainAction("act_stock", "Ombor Qoldig'i", "📦", "Qaysi mahsulotlar tugab qolayotganini ko'rsat"),
            DomainAction("act_revenue", "Kunlik Tushum", "💰", "Bugungi kassa va sof foyda hisoboti qanday?"),
            DomainAction("act_debts", "Qarzdorlar", "📋", "Qarzdorlar ro'yxati va eslatma yuborish")
        ),
        systemPersonaPrompt = "You are JARVIS Retail & Inventory Manager. Log quick voice sales, calculate stock decrements, track debts, and summarize daily cash flow for Axmadulloh sir's shop."
    )

    val OQITUVCHI = JarvisDomain(
        id = "OQITUVCHI",
        titleUz = "O'qituvchi",
        icon = "📚",
        category = "Ta'lim",
        primaryColor = Color.parseColor("#2979FF"),
        glowColor = Color.parseColor("#00B0FF"),
        secondaryColor = Color.parseColor("#82B1FF"),
        description = "Dars rejasi va test generatsiyasi; javoblarni avtomatik tekshirish; davomat va baho jurnali; ota-onalarga avtomatik hisobot; zaif o'quvchilarni aniqlash.",
        motto = "O'qituvchi yordamchisi faol. Metodika va testlar tayyor.",
        keywords = listOf("oqituvchi", "o'qituvchi", "ustoz", "muallim", "maktab", "darslik", "sinf"),
        actions = listOf(
            DomainAction("act_lesson_plan", "Dars Rejasi", "📝", "Bugungi mavzu uchun 45 daqiqalik dars rejasi tuz"),
            DomainAction("act_test_gen", "Test Tuzish", "⚡", "Mavzu bo'yicha 10 ta variantli test yarat"),
            DomainAction("act_check_answers", "Javob Tekshirish", "✅", "O'quvchilar test javoblarini solishtir"),
            DomainAction("act_report_parents", "Hisobot", "📊", "Ota-onalar uchun davomat hisobotini tayyorla")
        ),
        systemPersonaPrompt = "You are JARVIS Pedagogical Assistant. Generate structured curriculum lesson plans, multi-difficulty test questions, and student progress summaries for Axmadulloh sir."
    )

    val TIL = JarvisDomain(
        id = "TIL",
        titleUz = "Til O'rganish",
        icon = "🗣️",
        category = "Ta'lim",
        primaryColor = Color.parseColor("#FF4081"),
        glowColor = Color.parseColor("#F50057"),
        secondaryColor = Color.parseColor("#FF80AB"),
        description = "Ovozda jonli suhbat va talaffuz tuzatish; kunlik so'z (interval bilan takrorlash); xatolar daftari; kamera orqali matn tarjimasi.",
        motto = "Til o'rganish rejimi faol. Jonli suhbat va talaffuz tahlili boshlandi.",
        keywords = listOf("til", "ingliz", "arab", "rus", "tarjima", "speaking", "vocabulary", "so'z"),
        actions = listOf(
            DomainAction("act_speak", "Jonli Suhbat", "🗣️", "Ingliz tilida jonli muloqot qilamiz"),
            DomainAction("act_daily_word", "Kunlik So'zlar", "📖", "Bugungi 5 ta yangi so'zni o'rganamiz"),
            DomainAction("act_grammar_fix", "Xatoni Tuzat", "✏️", "Gapimni grammatik tekshir va to'g'irla"),
            DomainAction("act_translate", "Ovozli Tarjima", "🌐", "Ovozda tezkor sinxron tarjima qil")
        ),
        systemPersonaPrompt = "You are JARVIS Language Coach. Engage in interactive conversations, gently correct pronunciation and grammar slips, explain vocabulary nuances, and manage spaced repetition."
    )

    val ONLAYN_SAVDO = JarvisDomain(
        id = "ONLAYN_SAVDO",
        titleUz = "Onlayn Savdo",
        icon = "🛍️",
        category = "Biznes",
        primaryColor = Color.parseColor("#E040FB"),
        glowColor = Color.parseColor("#D500F9"),
        secondaryColor = Color.parseColor("#EA80FC"),
        description = "Telegram/Instagram buyurtmalarini avtomatik qabul qilish; mijozga avtomatik javob; mahsulot tavsifi va sotuvchi post yozish; yetkazish holati kuzatuvi.",
        motto = "Onlayn savdo rejimi faol. Mijozlar va buyurtmalar qabuli yoniq.",
        keywords = listOf("onlayn savdo", "marketpleys", "instagram", "e-commerce", "buyurtma", "yetkazish"),
        actions = listOf(
            DomainAction("act_order_accept", "Buyurtmalar", "🛍️", "Yangi kelgan buyurtmalarni tekshir"),
            DomainAction("act_post_write", "Sotuvchi Post", "✍️", "Ushbu mahsulot uchun Instagram/Telegram sotuvchi post yoz"),
            DomainAction("act_client_chat", "Mijozga Javob", "💬", "Mijoz savollariga avto-javob shabloni"),
            DomainAction("act_delivery", "Kuryer / Yetkazish", "🚚", "Yetkazib berish holatini tekshir")
        ),
        systemPersonaPrompt = "You are JARVIS E-Commerce Strategist. Help Axmadulloh sir manage online customer inquiries, write viral sales copywriting, and track deliveries smoothly."
    )

    val KAFE = JarvisDomain(
        id = "KAFE",
        titleUz = "Kafe & Restoran",
        icon = "☕",
        category = "Xizmat",
        primaryColor = Color.parseColor("#FF6F00"),
        glowColor = Color.parseColor("#FF8F00"),
        secondaryColor = Color.parseColor("#FFA000"),
        description = "Ovozda buyurtma va stol hisobi; menyu va narx boshqaruvi; masalliq qoldig'i ogohlantirishi; kunlik tushum; mijoz fikrlarini tahlil qilish.",
        motto = "Kafe & Restoran rejimi faol. Stol hisobi va menyu tayyor.",
        keywords = listOf("kafe", "restoran", "oshxona", "stol", "menyu", "ofitsiant", "taom"),
        actions = listOf(
            DomainAction("act_table_order", "Stol Buyurtmasi", "☕", "5-stol uchun 2 ta kofe va pitsa yoz"),
            DomainAction("act_check_calc", "Hisob Chiqarish", "🧾", "5-stol hisobini hisoblab ber"),
            DomainAction("act_menu_edit", "Menyu & Narx", "📋", "Bugungi stop-list va qoldiqlar"),
            DomainAction("act_feedback", "Mijoz Fikrlari", "⭐", "Mijoz sharhlari va xizmat sifatini tahlil qil")
        ),
        systemPersonaPrompt = "You are JARVIS Hospitality Manager. Manage table orders, POS bill calculations, ingredient stock alerts, and customer sentiment for Axmadulloh sir."
    )

    val SALON = JarvisDomain(
        id = "SALON",
        titleUz = "Go'zallik Saloni",
        icon = "💇",
        category = "Xizmat",
        primaryColor = Color.parseColor("#FF80AB"),
        glowColor = Color.parseColor("#FF4081"),
        secondaryColor = Color.parseColor("#FF4081"),
        description = "Navbatga yozish va band vaqtni taqsimlash; mijozga ertasi kuni eslatma; usta jadvali; qaytmagan mijozga “sog'indik” xabari.",
        motto = "Salon rejimi faol. Navbatlar va ustalar bandligi nazoratda.",
        keywords = listOf("salon", "sartaroshxona", "gozallik", "soch", "navbat", "vizaj", "manikyur"),
        actions = listOf(
            DomainAction("act_book_slot", "Navbatga Yozish", "💇", "Ertaga soat 14:00 ga yangi mijoz yoz"),
            DomainAction("act_master_sched", "Usta Jadvali", "📅", "Bugungi ustalar bandlik jadvali"),
            DomainAction("act_client_remind", "Mijozga Eslatma", "⏰", "Ertangi mijozlarga avtomatik SMS eslatma jo'nat"),
            DomainAction("act_miss_client", "Qaytmagan Mijozlar", "💌", "1 oydan beri kelmagan mijozlarga aksiya xabari")
        ),
        systemPersonaPrompt = "You are JARVIS Salon Coordinator. Organize appointments, minimize no-shows via automated reminders, and maintain master schedules for Axmadulloh sir."
    )

    val AVTOUSTA = JarvisDomain(
        id = "AVTOUSTA",
        titleUz = "Avtousta (Avtoservis)",
        icon = "🔧",
        category = "Xizmat",
        primaryColor = Color.parseColor("#3D5AFE"),
        glowColor = Color.parseColor("#536DFE"),
        secondaryColor = Color.parseColor("#8C9EFF"),
        description = "Mashina tarixi va mijoz kartochkasi; moy/ehtiyot qism almashtirish eslatmasi; ehtiyot qism narxini qidirish; ish tayyor bo'lganda mijozga avtomatik xabar.",
        motto = "Avtoservis rejimi faol. Diagnostika va zapchastlar hisobi tayyor.",
        keywords = listOf("avtousta", "usta", "mashina ustasi", "zapchast", "moy", "servis", "remont"),
        actions = listOf(
            DomainAction("act_car_card", "Mijoz Kartochkasi", "🚗", "Cobalt 01A777AA mashinasi tarixi va qilingan ishlar"),
            DomainAction("act_parts_search", "Zapchast Qidirish", "🔍", "Ushbu model uchun original zapchast narxini top"),
            DomainAction("act_oil_remind", "Moy Almashtirish", "🛢️", "Keyingi moy almashtirish kilometrajini yoz"),
            DomainAction("act_done_msg", "Mashina Tayyor Xabari", "📱", "Mijozga mashinangiz tayyor deb Telegramdan xabar yubor")
        ),
        systemPersonaPrompt = "You are JARVIS Automotive Service Assistant. Track vehicle service logs, part procurement prices, maintenance intervals, and customer notifications for Axmadulloh sir."
    )

    val USTA = JarvisDomain(
        id = "USTA",
        titleUz = "Qurilish / Ta'mir Ustasi",
        icon = "🔨",
        category = "Qurilish",
        primaryColor = Color.parseColor("#FF9800"),
        glowColor = Color.parseColor("#F57C00"),
        secondaryColor = Color.parseColor("#FFE082"),
        description = "Material va smeta hisob-kitobi; ishchilar kuni va maoshi; bosqichma-bosqich hisobot (rasm bilan) mijozga; xarid ro'yxati.",
        motto = "Qurilish & Ta'mir rejimi faol. Smeta va brigada hisobi nazoratda.",
        keywords = listOf("usta", "qurilish", "smeta", "remont", "tamir", "sement", "gipsokarton", "koshin"),
        actions = listOf(
            DomainAction("act_smeta", "Smeta Hisoblash", "📐", "100 kv.m xona ta'miri uchun materiallar smetasini tuz"),
            DomainAction("act_worker_pay", "Ishchilar Maoshi", "👷", "Brigadaning kunlik ishbay hisob-kitobi"),
            DomainAction("act_material_list", "Xarid Ro'yxati", "📝", "Bozordan olinadigan qurilish mollari ro'yxati"),
            DomainAction("act_site_report", "Buyurtmachiga Hisobot", "📸", "Bugun qilingan ishlar bo'yicha hisobot tayyorla")
        ),
        systemPersonaPrompt = "You are JARVIS Construction & Renovation Estimator. Calculate material requirements, budget estimates, labor payrolls, and milestone reports for Axmadulloh sir."
    )

    val MULK = JarvisDomain(
        id = "MULK",
        titleUz = "Rieltor / Ko'chmas Mulk",
        icon = "🏢",
        category = "Ko'chmas Mulk",
        primaryColor = Color.parseColor("#00BFA5"),
        glowColor = Color.parseColor("#1DE9B6"),
        secondaryColor = Color.parseColor("#A7FFEB"),
        description = "E'lonlar bazasi; mijoz talabiga mos uyni saralash; ko'rish uchrashuvlari kalendari; narx taqqoslash; shartnoma qoralamasi.",
        motto = "Ko'chmas mulk rejimi faol. Obyektlar va mijozlar filtri tayyor.",
        keywords = listOf("mulk", "rieltor", "uy", "kvartira", "ijara", "hovli", "yer", "arenda"),
        actions = listOf(
            DomainAction("act_find_home", "Mijozga Uy Topish", "🔍", "Mijoz byudjetiga mos 3 xonali kvartiralarni sarala"),
            DomainAction("act_meet_cal", "Uchrashuv Kalendari", "📅", "Bugungi uy ko'rish uchrashuvlarini ayt"),
            DomainAction("act_price_compare", "Bozor Narxi", "📊", "Tuman bo'yicha o'rtacha kvadrat metr narxi qancha?"),
            DomainAction("act_contract_draft", "Shartnoma Qoralamasi", "📑", "Ijara yoki oldi-sotdi shartnoma shablonini och")
        ),
        systemPersonaPrompt = "You are JARVIS Real Estate Agent. Match listings to buyer preferences, structure rental/sale agreements, and schedule property showings for Axmadulloh sir."
    )

    val FRILANSER = JarvisDomain(
        id = "FRILANSER",
        titleUz = "Frilanser",
        icon = "💼",
        category = "Biznes",
        primaryColor = Color.parseColor("#7C4DFF"),
        glowColor = Color.parseColor("#651FFF"),
        secondaryColor = Color.parseColor("#D1C4E9"),
        description = "Mijoz va loyiha boshqaruvi; vaqt hisobi; invoys yaratish va yuborish; taklif (proposal) yozish; to'lov kechiksa eslatma.",
        motto = "Frilanser rejimi faol. Loyihalar, taymer va invoyslar tayyor.",
        keywords = listOf("frilans", "frilanser", "freelance", "upwork", "fiverr", "invoys", "loyixa"),
        actions = listOf(
            DomainAction("act_proposal", "Taklif (Proposal) Yoz", "✍️", "Ushbu loyiha uchun kuchli Cover Letter yoz"),
            DomainAction("act_invoice", "Invoys Chiqarish", "🧾", "Mijoz nomiga 300 dollarlik invoys tayyorla"),
            DomainAction("act_time_track", "Vaqt Hisobi", "⏱️", "Ushbu loyiha uchun vaqt hisoblagichni yoq"),
            DomainAction("act_payment_ping", "To'lov Eslatmasi", "💳", "To'lov muddati kechikkan mijozga xushmuomala eslatma")
        ),
        systemPersonaPrompt = "You are JARVIS Freelance Business Manager. Draft winning project proposals, manage client deadlines, generate invoices, and handle professional follow-ups for Axmadulloh sir."
    )

    val MENEJER = JarvisDomain(
        id = "MENEJER",
        titleUz = "Menejer / Boshqaruv",
        icon = "👔",
        category = "Boshqaruv",
        primaryColor = Color.parseColor("#00B0FF"),
        glowColor = Color.parseColor("#0091EA"),
        secondaryColor = Color.parseColor("#80D8FF"),
        description = "Yig'ilishni yozib xulosa va vazifalarga ajratish; vazifa taqsimlash va kuzatish; haftalik avtomatik hisobot; jamoaga e'lon.",
        motto = "Menejment rejimi faol. Yig'ilish protokoli va vazifalar nazoratda.",
        keywords = listOf("menejer", "boshqaruv", "direktor", "yigilish", "meeting", "vazifa", "jamoa"),
        actions = listOf(
            DomainAction("act_meeting_sum", "Yig'ilish Xulosasi", "🎙️", "Yig'ilish audio yozuvidan asosiy qarorlar va vazifalarni ajrat"),
            DomainAction("act_task_assign", "Vazifa Taqsimlash", "📋", "Jamoa a'zolariga topshiriqlar ro'yxatini tuz"),
            DomainAction("act_week_report", "Haftalik Hisobot", "📊", "Kompaniya bo'yicha haftalik KPI hisobotini tayyorla"),
            DomainAction("act_team_ann", "Jamoaga E'lon", "📢", "Kompaniya Telegram guruhiga muhim e'lon matnini yoz")
        ),
        systemPersonaPrompt = "You are JARVIS Executive Chief of Staff. Transcribe leadership meetings, extract actionable deliverables with owners/deadlines, and synthesize weekly KPI summaries for Axmadulloh sir."
    )

    val HR = JarvisDomain(
        id = "HR",
        titleUz = "HR / Recruiter",
        icon = "👥",
        category = "Boshqaruv",
        primaryColor = Color.parseColor("#00E676"),
        glowColor = Color.parseColor("#69F0AE"),
        secondaryColor = Color.parseColor("#B9F6CA"),
        description = "Rezyumelarni avtomatik saralash va baholash; intervyu savollari; nomzodlar jadvali; taklif xati (offer) va rad javobi yozish.",
        motto = "HR & Rekruting rejimi faol. Nomzodlar tahlili va intervyular tayyor.",
        keywords = listOf("hr", "rekruting", "nomzod", "intervyu", "rezyume", "vakansiya", "xodim"),
        actions = listOf(
            DomainAction("act_cv_parse", "Rezyume Tahlili", "📄", "Ushbu CV nomzodini vakansiyaga mosligini bahola"),
            DomainAction("act_questions", "Intervyu Savollari", "❓", "Nomzodning tajribasini tekshirish uchun 7 ta savol tuz"),
            DomainAction("act_offer_letter", "Job Offer Yozish", "✉️", "Qabul qilingan nomzod uchun rasmiy Job Offer tayyorla"),
            DomainAction("act_reject_letter", "Xushmuomala Rad Xati", "📝", "Nomzodga motivatsion rad javobi yoz")
        ),
        systemPersonaPrompt = "You are JARVIS Talent Acquisition Specialist. Screen resumes, formulate targeted behavioral interview questions, and draft professional communication for Axmadulloh sir."
    )

    val YURIST = JarvisDomain(
        id = "YURIST",
        titleUz = "Yurist & Soliq Maslahatchisi",
        icon = "⚖️",
        category = "Huquq va Soliq",
        primaryColor = Color.parseColor("#D4AF37"),
        glowColor = Color.parseColor("#FFD700"),
        secondaryColor = Color.parseColor("#1E3A8A"),
        description = "Korxona shartnomalari auditi, sud va da'vo arizalari, O'zbekiston Soliq Kodeksi (QQS, aylanmadan soliq, imtiyozlar), mehnat nizolari va qonuniy himoya.",
        motto = "Huquqiy va soliq tahlili faol. Shartnomalar, moddalar va soliq normalari nazoratda, Axmadulloh sir!",
        keywords = listOf("yurist", "advokat", "soliq", "shartnoma", "qonun", "sud", "huquq", "qqs", "aylanma", "soliqchi", "yuridik"),
        actions = listOf(
            DomainAction("act_contract_audit", "Shartnoma Auditi", "⚖️", "Ushbu shartnomadagi xavfli va noaniq bandlarni top"),
            DomainAction("act_tax_plan", "Soliq Tizimi (QQS)", "💰", "Qaysi soliq rejimi korxonamga foydali: QQS yoki aylanma?"),
            DomainAction("act_labor_law", "Mehnat Nizolari", "👥", "Xodimni qonuniy ishdan bo'shatish yoki intizomiy jazo tartibi"),
            DomainAction("act_court_draft", "Sudga Da'vo", "📑", "Iqtisodiy sudga qarzdorlikni undirish bo'yicha da'vo arizasi tuz")
        ),
        systemPersonaPrompt = "You are JARVIS Legal & Tax Advisor for Axmadulloh sir. Provide sharp counsel on Uzbek law (Civil, Labor, Tax codes), evaluate contract clauses for risks, optimize tax regimes (VAT vs turnover tax), and format court petitions."
    )

    val TADBIRKOR = JarvisDomain(
        id = "TADBIRKOR",
        titleUz = "Tadbirkorlik & Firma Ochish",
        icon = "🚀",
        category = "Biznes",
        primaryColor = Color.parseColor("#059669"),
        glowColor = Color.parseColor("#10B981"),
        secondaryColor = Color.parseColor("#047857"),
        description = "O'zbekistonda MChJ yoki YaTT ochish (fo.birdarcha.uz), ustav fondi, E-imzo, bank hisob raqami, soliq rejimini tanlash va biznes-reja.",
        motto = "Tadbirkorlik rejimi faol. Firma ochish va biznes yo'l-yo'riqlari tayyor, Axmadulloh sir!",
        keywords = listOf("tadbirkor", "tadbirkorlik", "firma", "firma ochish", "mchj", "yatt", "biznes", "startap", "birdarcha", "hisob raqam"),
        actions = listOf(
            DomainAction("act_open_mchj", "MChJ Ochish Bosqichlari", "🚀", "O'zbekistonda MChJ ochish uchun nimalar kerak va tartibi qanday?"),
            DomainAction("act_mchj_vs_yatt", "MChJ yoki YaTT?", "⚖️", "Mening biznesim uchun MChJ ma'qulmi yoki YaTT?"),
            DomainAction("act_biz_plan", "Biznes Reja Tuzish", "📊", "Yangi loyiha uchun 1 yillik biznes-reja va xarajatlar smetasini tuz"),
            DomainAction("act_bank_eimzo", "Bank & E-imzo", "🏦", "Firmaga bank hisob raqami va E-imzo olish qadamlari")
        ),
        systemPersonaPrompt = "You are JARVIS Business Startup Mentor for Axmadulloh sir. Guide him step-by-step in launching enterprises in Uzbekistan: registration via fo.birdarcha.uz, drafting charters, opening corporate bank accounts, choosing tax brackets, and structuring financial plans."
    )

    val IT_ADMIN = JarvisDomain(
        id = "IT_ADMIN",
        titleUz = "IT-admin / DevOps",
        icon = "🖥️",
        category = "IT & Dasturlash",
        primaryColor = Color.parseColor("#00E676"),
        glowColor = Color.parseColor("#00C853"),
        secondaryColor = Color.parseColor("#69F0AE"),
        description = "Server holati monitoringi; xavfsizlik va xato ogohlantirishlari; backup tekshiruvi va eslatmasi; hodisa hisoboti.",
        motto = "IT-Admin rejimi faol. Serverlar, xavfsizlik va backup nazoratda.",
        keywords = listOf("it-admin", "admin", "devops", "server", "linux", "monitoring", "backup", "tarmoq"),
        actions = listOf(
            DomainAction("act_srv_health", "Server Monitoringi", "🖥️", "Serverlarning CPU, RAM va disk holatini ayt"),
            DomainAction("act_backup_check", "Backup Tekshiruvi", "💾", "Oxirgi ma'lumotlar zaxirasi muvaffaqiyatli o'tdimi?"),
            DomainAction("act_sec_alert", "Xavfsizlik Auditi", "🛡️", "Ochiq portlar va xavfli urinishlarni tekshir"),
            DomainAction("act_incident", "Hodisa Hisoboti", "🚨", "Nosozlik sabablari bo'yicha Post-Mortem tuz")
        ),
        systemPersonaPrompt = "You are JARVIS Systems Administrator & SRE. Monitor infrastructure health, alert on anomalies, verify backup redundancies, and assist with shell troubleshooting for Axmadulloh sir."
    )

    val KONTENT = JarvisDomain(
        id = "KONTENT",
        titleUz = "Kontent Meyker / SMM",
        icon = "🎬",
        category = "Ijod",
        primaryColor = Color.parseColor("#FF1744"),
        glowColor = Color.parseColor("#D50000"),
        secondaryColor = Color.parseColor("#FF8A80"),
        description = "Mavzu g'oyalari va ssenariy; sarlavha/post variantlari; kontent kalendari; ovozdan matnga; grammatika tahriri.",
        motto = "Kontent Meyker rejimi faol. Ssenariylar va trendlar tayyor.",
        keywords = listOf("kontent", "smm", "bloger", "youtube", "tiktok", "reels", "ssenariy", "post"),
        actions = listOf(
            DomainAction("act_script_write", "Reels Ssenariysi", "🎬", "30 soniyalik qiziqarli Reels ssenariysi tuz"),
            DomainAction("act_viral_hook", "Viral Sarlavhalar", "🔥", "Ushbu video uchun 5 ta chertma (hook) sarlavha"),
            DomainAction("act_content_plan", "Kontent Kalendari", "📅", "Keyingi hafta uchun kunlik postlar rejasi"),
            DomainAction("act_voice_to_post", "Ovozdan Post", "🎙️", "Aytgan fikrimni chiroyli Instagram postiga aylantir")
        ),
        systemPersonaPrompt = "You are JARVIS Content Strategist & Viral Copywriter. Craft engaging scripts, captivating video hooks, and high-converting social media plans for Axmadulloh sir."
    )

    val FOTO_VIDEO = JarvisDomain(
        id = "FOTO_VIDEO",
        titleUz = "Foto & Video Montaj",
        icon = "📸",
        category = "Ijod",
        primaryColor = Color.parseColor("#536DFE"),
        glowColor = Color.parseColor("#3D5AFE"),
        secondaryColor = Color.parseColor("#8C9EFF"),
        description = "Buyurtma va suratga olish kalendari; narx paketlari; mijozga galereya yuborish; montaj vazifalari ro'yxati.",
        motto = "Foto & Video studiya rejimi faol. S'yomkalar va montaj ro'yxati tayyor.",
        keywords = listOf("foto", "video", "montaj", "fotosessiya", "kamera", "fotograf", "klip"),
        actions = listOf(
            DomainAction("act_shoot_cal", "S'yomka Kalendari", "📸", "Bugungi va ertangi suratga olish vaqtlari"),
            DomainAction("act_edit_tasks", "Montaj Ro'yxati", "✂️", "Topshirilishi kerak bo'lgan tayyor montajlar"),
            DomainAction("act_pricing_pack", "Narx Paketlari", "💵", "Mijozga to'y yoki tadbir narx paketlarini yubor"),
            DomainAction("act_gear_check", "Uskunalar Tekshiruvi", "🔋", "Batareyalar va fleshkalarni tekshirish eslatmasi")
        ),
        systemPersonaPrompt = "You are JARVIS Media Production Coordinator. Organize shoot schedules, track post-production edits, and manage equipment checklists for Axmadulloh sir."
    )

    val MUSIQA = JarvisDomain(
        id = "MUSIQA",
        titleUz = "Musiqachi / Bastakor",
        icon = "🎵",
        category = "Ijod",
        primaryColor = Color.parseColor("#E040FB"),
        glowColor = Color.parseColor("#D500F9"),
        secondaryColor = Color.parseColor("#F48FB1"),
        description = "G'oyani ovozda yozib olish; qo'shiq matni qoralamasi; repetitsiya jadvali; chiqish rejasi.",
        motto = "Musiqa & Ijod rejimi faol. Kuy va she'riyat ilhomi tayyor.",
        keywords = listOf("musiqa", "qoshiq", "bastakor", "ovoz", "ritm", "akkord", "repetitsiya"),
        actions = listOf(
            DomainAction("act_melody_rec", "Kuy G'oyasini Yoz", "🎙️", "Hozir aytadigan kuyni diktofonga ol va saqla"),
            DomainAction("act_lyrics_draft", "Qo'shiq Matni", "✍️", "Berilgan mavzu bo'yicha qofiyali qo'shiq matni yoz"),
            DomainAction("act_rehearsal", "Repetitsiya", "📅", "Navbatdagi mashg'ulot vaqtini eslat"),
            DomainAction("act_chords", "Akkordlar & Lad", "🎸", "Ushbu tonaallikdagi akkord ketma-ketligini top")
        ),
        systemPersonaPrompt = "You are JARVIS Creative Music Assistant. Capture fleeting melodic ideas, suggest rhyming lyrical verses, and organize rehearsals for Axmadulloh sir."
    )

    val GAMER = JarvisDomain(
        id = "GAMER",
        titleUz = "Gamer / Strimer",
        icon = "🎮",
        category = "Ko'ngilochar",
        primaryColor = Color.parseColor("#FF0055"),
        glowColor = Color.parseColor("#00F0FF"),
        secondaryColor = Color.parseColor("#FF5252"),
        description = "O'yin yangilanishlarini boshqarish; stream rejasi; auditoriya savollariga javob; o'yin narxi chegirmasi kuzatuvi.",
        motto = "Gamer Cyber-Rejimi faol. FPS, o'yin yangiliklari va stream tayyor.",
        keywords = listOf("gamer", "oyun", "o'yin", "stream", "strim", "pubg", "csgo", "dota"),
        actions = listOf(
            DomainAction("act_stream_plan", "Strim Rejasi", "🎮", "Bugungi strim vaqti va mavzusini e'lon qil"),
            DomainAction("act_discount_track", "Chegirmalar", "🏷️", "Steam va Epic Games'dagi eng katta chegirmalar"),
            DomainAction("act_game_news", "Patch Yangiliklari", "📰", "O'yinning oxirgi yangilanishi va metani ayt"),
            DomainAction("act_chat_mod", "Auditoriya Savollari", "💬", "Chatdagi eng ko'p so'ralgan savollarga javob")
        ),
        systemPersonaPrompt = "You are JARVIS Gaming Assistant. Track gaming patches, Steam discounts, stream schedules, and meta tactics for Axmadulloh sir."
    )

    val FERMER = JarvisDomain(
        id = "FERMER",
        titleUz = "Fermer / Qishloq Xo'jaligi",
        icon = "🌱",
        category = "Agro",
        primaryColor = Color.parseColor("#43A047"),
        glowColor = Color.parseColor("#2E7D32"),
        secondaryColor = Color.parseColor("#A5D6A7"),
        description = "Ob-havo va sovuq/yomg'ir ogohlantirishi; sug'orish va ekish kalendari; hosil va xarajat daftari; bozor narxlari; kamera orqali kasallik tahlili.",
        motto = "Agro-Fermer rejimi faol. Ob-havo, ekish va hosildorlik hisobi tayyor.",
        keywords = listOf("fermer", "agro", "hosil", "yer", "ekin", "daraxt", "meva", "issiqxona"),
        actions = listOf(
            DomainAction("act_weather_alert", "Agro Ob-havo", "🌦️", "Keyingi 3 kundagi sovuq urishi va yomg'ir xavfi"),
            DomainAction("act_irrigation", "Sug'orish Kalendari", "💧", "Yer namligi va sug'orish navbatini rejalashtir"),
            DomainAction("act_crop_health", "Kasallik Tahlili", "🌿", "Kamera orqali bargdagi kasallikni aniqla"),
            DomainAction("act_market_price", "Bozor Narxlari", "📈", "Ulgurji bozordagi mahsulot narxlari tahlili")
        ),
        systemPersonaPrompt = "You are JARVIS Agricultural Consultant. Monitor frost/weather alerts, optimize crop irrigation schedules, diagnose plant diseases, and evaluate market crop valuations for Axmadulloh sir."
    )

    val MOLIYA = JarvisDomain(
        id = "MOLIYA",
        titleUz = "Shaxsiy Moliya & Byudjet",
        icon = "💰",
        category = "Moliya",
        primaryColor = Color.parseColor("#00C853"),
        glowColor = Color.parseColor("#FFD600"),
        secondaryColor = Color.parseColor("#B9F6CA"),
        description = "“Taksiga 20 ming” desa xarajat avtomatik yoziladi; oylik byudjet va chegara ogohlantirishi; qarz eslatmasi; haftalik grafik hisobot.",
        motto = "Moliya rejimi faol. Xarajatlar va oylik byudjet nazoratda.",
        keywords = listOf("moliya", "pul", "xarajat", "byudjet", "daromad", "kartochka", "jamgarma"),
        actions = listOf(
            DomainAction("act_add_expense", "Xarajat Yozish", "💸", "Taksiga 25 000 so'm xarajat deb qo'sh"),
            DomainAction("act_budget_left", "Byudjet Qoldig'i", "📊", "Bu oy uchun qancha byudjetim qoldi?"),
            DomainAction("act_debt_track", "Qarzlar Daftari", "📋", "Kimdan qancha qarzim bor va menga kim berishi kerak?"),
            DomainAction("act_savings_goal", "Jamg'arma Maqsadi", "🎯", "Maqsadimga yetish uchun kunlik tejash rejasi")
        ),
        systemPersonaPrompt = "You are JARVIS Personal Financial Advisor. Categorize spoken expenses instantly, track monthly limits, compute savings pace, and report debt ledgers for Axmadulloh sir."
    )

    val UY = JarvisDomain(
        id = "UY",
        titleUz = "Aqlli Uy (Smart Home)",
        icon = "🏠",
        category = "Uy",
        primaryColor = Color.parseColor("#00B0FF"),
        glowColor = Color.parseColor("#40C4FF"),
        secondaryColor = Color.parseColor("#80D8FF"),
        description = "Chiroq, konditsioner, kamera boshqaruvi; “Kechqurun rejimi” kabi sahnalar; sensor ogohlantirishlari; kelish-ketishga qarab avtomatik harakat.",
        motto = "Aqlli Uy boshqaruvi faol. Xonalar va qurilmalar nazoratda.",
        keywords = listOf("uy", "aqlli uy", "chiroq", "konditsioner", "kamera", "smart home", "eshik"),
        actions = listOf(
            DomainAction("act_lights", "Chiroqlarni O'chirish", "💡", "Barcha xonadagi chiroqlarni o'chir"),
            DomainAction("act_temp", "Haroratni Sozlash", "❄️", "Konditsionerni 24 gradusga qo'y"),
            DomainAction("act_night_scene", "Kechqurun Sahna", "🌙", "Kechki tinch rejimni yoq"),
            DomainAction("act_camera_view", "Kameralarni Ko'rsat", "📹", "Hovli va darvozadagi kamerani och")
        ),
        systemPersonaPrompt = "You are JARVIS Smart Home Controller. Automate lighting, climate, security surveillance, and comfort scenes effortlessly for Axmadulloh sir."
    )

    val OILA = JarvisDomain(
        id = "OILA",
        titleUz = "Oila & Ro'zg'or",
        icon = "👨‍👩‍👦",
        category = "Hayot",
        primaryColor = Color.parseColor("#FF5252"),
        glowColor = Color.parseColor("#FF1744"),
        secondaryColor = Color.parseColor("#FF8A80"),
        description = "Umumiy oilaviy kalendar; xaridlar ro'yxati (hamma ko'radi); ovqat rejasi; bola jadvali va emlash eslatmasi.",
        motto = "Oila & Ro'zg'or rejimi faol. Ro'zg'or va oilaviy reja tartibda.",
        keywords = listOf("oila", "ro'zgor", "bozorlik", "bola", "xarid", "uyga", "ota-ona"),
        actions = listOf(
            DomainAction("act_family_buy", "Bozorlik Ro'yxati", "🛒", "Bozorlik ro'yxatiga go'sht va mevalarni qo'sh"),
            DomainAction("act_meal_plan", "Kechki Taom Rejasi", "🍲", "Bugun kechki ovqatga nima pishirish mumkin?"),
            DomainAction("act_child_sched", "Farzand Mashg'uloti", "👶", "Farzandlarning to'garak va emlash vaqtlari"),
            DomainAction("act_fam_events", "Oilaviy Sanalar", "🎂", "Tug'ilgan kunlar va oilaviy bayramlar eslatmasi")
        ),
        systemPersonaPrompt = "You are JARVIS Family Coordinator. Harmonize family schedules, maintain shared grocery lists, suggest balanced meal plans, and track developmental milestones for Axmadulloh sir."
    )

    val KEKSA = JarvisDomain(
        id = "KEKSA",
        titleUz = "Katta Yoshi & Qulaylik",
        icon = "👵",
        category = "G'amxo'rlik",
        primaryColor = Color.parseColor("#FFB300"),
        glowColor = Color.parseColor("#FFA000"),
        secondaryColor = Color.parseColor("#FFE082"),
        description = "Juda oddiy ekran va katta shrift; dori eslatmasi (ichdimi-yo'qmi tasdig'i); bir tugma bilan yaqinlarga qo'ng'iroq; favqulodda signal va joylashuv yuborish.",
        motto = "Katta Yoshlilar rejimi faol. Yirik shrift, qulaylik va g'amxo'rlik yoniq.",
        keywords = listOf("keksa", "katta yosh", "dori", "onam", "otam", "bobo", "buvi", "sodda"),
        actions = listOf(
            DomainAction("act_pill_check", "Dori Ichish Vaqti", "💊", "Bugungi dorilarni ichish vaqtini baland ovozda ayt"),
            DomainAction("act_sos_call", "Tezkor Qo'ng'iroq", "🚨", "Yaqin insonimga bir zumda qo'ng'iroq qil"),
            DomainAction("act_loc_send", "Joylashuv Yuborish", "📍", "Mening manzilimni yaqinlarimga SMS yubor"),
            DomainAction("act_read_news", "Yangiliklarni O'qib Ber", "🔊", "Bugungi eng muhim yangiliklarni sokin o'qib ber")
        ),
        systemPersonaPrompt = "You are JARVIS Senior Companion. Speak with extreme warmth, patience, large clear context, confirm medications reliably, and provide immediate one-touch family dispatch for Axmadulloh sir."
    )

    val SALOMATLIK = JarvisDomain(
        id = "SALOMATLIK",
        titleUz = "Salomatlik & Tibbiyot",
        icon = "🩺",
        category = "Salomatlik",
        primaryColor = Color.parseColor("#00E5FF"),
        glowColor = Color.parseColor("#1DE9B6"),
        secondaryColor = Color.parseColor("#84FFFF"),
        description = "Dori, suv, uyqu va mashq eslatmalari; kunlik salomatlik daftari; umumiy tibbiy ma'lumot va tahlillar eslatmasi.",
        motto = "Salomatlik rejimi faol. Kunlik suv, uyqu va dori intizomi nazoratda.",
        keywords = listOf("salomatlik", "doktor", "suv", "uyqu", "tibbiyot", "puls", "bosim"),
        actions = listOf(
            DomainAction("act_water_track", "Suv Ichish", "💧", "1 stakan suv ichdim deb belgilab qo'y"),
            DomainAction("act_sleep_stats", "Uyqu Rejimi", "😴", "Uyqu vaqti va dam olish tavsiyasi"),
            DomainAction("act_med_tracker", "Dorilar Grafigi", "💊", "Retseptdagi dorilar jadvalini ko'rsat"),
            DomainAction("act_doctor_visit", "Shifokor Qabuli", "🏥", "Shifokor ko'rigi va tahlillar sanasini ayt")
        ),
        systemPersonaPrompt = "You are JARVIS Wellness Assistant. Encourage healthy hydration, sleep consistency, medication compliance, and lifestyle tracking without making definitive medical diagnoses."
    )

    val SPORT = JarvisDomain(
        id = "SPORT",
        titleUz = "Sport & Fitnes",
        icon = "🏋️",
        category = "Salomatlik",
        primaryColor = Color.parseColor("#FF3D00"),
        glowColor = Color.parseColor("#DD2C00"),
        secondaryColor = Color.parseColor("#FF6E40"),
        description = "Mashq dasturi va natijalar tarixi; kaloriya va ovqat rejasi; maqsadga qarab moslashuv; kuchli motivatsiya.",
        motto = "Sport & Fitnes rejimi faol. Mashg'ulot, kaloriya va g'alaba tomon olg'a!",
        keywords = listOf("sport", "fitnes", "mashq", "zal", "turnik", "yugurish", "kaloriya"),
        actions = listOf(
            DomainAction("act_workout_start", "Mashqni Boshlash", "🏋️", "Bugungi ko'krak va qo'l mashg'ulotini boshlaymiz"),
            DomainAction("act_rest_timer", "Dam Olish Taymeri", "⏱️", "Setlar orasida 90 soniya taymer qo'y"),
            DomainAction("act_cal_track", "Kaloriya Hisobi", "🥗", "Bugungi ovqatlardan olingan kaloriyani hisobla"),
            DomainAction("act_motivation", "Kuchli Motivatsiya", "🔥", "Mashg'ulot uchun kuchli motivatsiya ber")
        ),
        systemPersonaPrompt = "You are JARVIS High-Performance Fitness Coach. Drive intense athletic motivation, pace rest timers, track training split progressions, and manage macro calories for Axmadulloh sir."
    )

    val SAYOHAT = JarvisDomain(
        id = "SAYOHAT",
        titleUz = "Sayohat & Turizm",
        icon = "✈️",
        category = "Hayot",
        primaryColor = Color.parseColor("#00B0FF"),
        glowColor = Color.parseColor("#00E5FF"),
        secondaryColor = Color.parseColor("#80D8FF"),
        description = "Marshrut va chipta/mehmonxona eslatmasi; valyuta kursi; jonli ovozli tarjimon; boriladigan joy tavsiyalari.",
        motto = "Sayohat rejimi faol. Parvozlar, chiptalar va tarjimon doim yoningizda.",
        keywords = listOf("sayohat", "samolyot", "aeroport", "mehmonxona", "bilet", "turizm", "valyuta"),
        actions = listOf(
            DomainAction("act_flight_info", "Parvoz & Chiptalar", "✈️", "Parvoz vaqti va terminal ma'lumotlarini ayt"),
            DomainAction("act_currency", "Valyuta Kursi", "💱", "Bugungi dollar va yevro kursi qancha?"),
            DomainAction("act_live_trans", "Sayohat Tarjimoni", "🗣️", "Mahalliy aholi bilan gaplashish uchun tarjimonni yoq"),
            DomainAction("act_attractions", "Diqqatga Sazovor Joylar", "🗺️", "Bu shahardagi eng mashhur 5 ta joyni tavsiya qil")
        ),
        systemPersonaPrompt = "You are JARVIS World Travel Concierge. Monitor flights, organize itinerary bookings, perform rapid currency conversions, and recommend local cultural sights for Axmadulloh sir."
    )

    val HAYVON = JarvisDomain(
        id = "HAYVON",
        titleUz = "Uy Hayvonlari (Veterinar)",
        icon = "🐾",
        category = "G'amxo'rlik",
        primaryColor = Color.parseColor("#00E676"),
        glowColor = Color.parseColor("#69F0AE"),
        secondaryColor = Color.parseColor("#B9F6CA"),
        description = "Ovqat va emlash eslatmasi; veterinar tarixi; vazn va sog'liq daftari; sayr va parvarish jadvali.",
        motto = "Uy hayvonlari rejimi faol. Jonivoringiz salomatligi va parvarishi nazoratda.",
        keywords = listOf("hayvon", "mushuk", "kuchuk", "veterinar", "jonivor", "emlash", "sayr"),
        actions = listOf(
            DomainAction("act_pet_feed", "Ovqatlantirish", "🍖", "Mushuk/kuchukning ovqat va suv vaqtini eslat"),
            DomainAction("act_pet_vaccine", "Emlash & Vaksina", "💉", "Keyingi emlash va veterinar ko'rigi sanasi"),
            DomainAction("act_pet_walk", "Sayr Taymeri", "🐕", "Jonivor bilan 30 daqiqalik sayr rejasini yoq"),
            DomainAction("act_pet_health", "Sog'liq Daftari", "📋", "Oxirgi vazn va dori ichirish tarixini och")
        ),
        systemPersonaPrompt = "You are JARVIS Pet Care & Veterinary Assistant. Schedule feedings, track immunization calendars, record weight dynamics, and maintain walk routines for Axmadulloh sir."
    )

    // Complete list of all domains in order
    private val allDomainsList: List<JarvisDomain> = listOf(
        TALABA, OQITUVCHI, TIL, DOKON, ONLAYN_SAVDO,
        KAFE, SALON, HAYDOVCHI, AVTOUSTA, USTA,
        MULK, FRILANSER, MENEJER, HR, YURIST, TADBIRKOR,
        DASTURCHI, IT_ADMIN, KONTENT, FOTO_VIDEO, MUSIQA,
        GAMER, FERMER, MOLIYA, UY, OILA,
        KEKSA, SALOMATLIK, SPORT, SAYOHAT, DIN,
        HAYVON
    )

    fun getAllDomains(): List<JarvisDomain> = allDomainsList

    fun getDomainById(id: String): JarvisDomain {
        return allDomainsList.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: ASOSIY
    }

    /**
     * Automatic Voice Detection: Matches spoken Uzbek commands like:
     * "haydovchi rejimiga o't", "yurist rejimini yoq", "tadbirkorlik", "talaba", "dasturchi"
     */
    fun findDomainByVoice(transcript: String): JarvisDomain? {
        val clean = transcript.lowercase().trim()

        if (clean.contains("asosiy") || clean.contains("bosh sahifa") || clean.contains("standart")) {
            return ASOSIY
        }
        if (clean.contains("haydovchi") || clean.contains("mashina") || clean.contains("shofyor") || clean.contains("taksi") || clean.contains("rul")) {
            return HAYDOVCHI
        }
        if (clean.contains("yurist") || clean.contains("advokat") || clean.contains("soliq") || clean.contains("shartnoma") || clean.contains("sud")) {
            return YURIST
        }
        if (clean.contains("tadbirkor") || clean.contains("firma") || clean.contains("mchj") || clean.contains("yatt") || clean.contains("startap")) {
            return TADBIRKOR
        }
        if (clean.contains("talaba") || clean.contains("student") || clean.contains("dars") || clean.contains("konspekt")) {
            return TALABA
        }
        if (clean.contains("dasturchi") || clean.contains("kod") || clean.contains("programist") || clean.contains("terminal")) {
            return DASTURCHI
        }
        if (clean.contains("din") || clean.contains("quron") || clean.contains("qur'on") || clean.contains("namoz")) {
            return DIN
        }

        val isSwitchCommand = clean.contains("rejim") || clean.contains("mode") ||
                clean.contains("o't") || clean.contains("ot") || clean.contains("yoq") ||
                clean.contains("boshla") || clean.contains("ko'rinish") || clean.contains("korinish")

        // Match against all registered domains
        for (domain in allDomainsList) {
            for (kw in domain.keywords) {
                if (clean.contains(kw)) {
                    if (isSwitchCommand || clean.length <= kw.length + 8) {
                        return domain
                    }
                }
            }
        }
        return null
    }
}
