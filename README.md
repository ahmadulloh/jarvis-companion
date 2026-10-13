# 📱 JARVIS Android Companion (Ovozli Ko‘prik)

Ushbu ilova **JARVIS (Mark 57)** sun'iy intellekt tizimini Bluetooth quloqchin va Android telefon orqali masofadan (ekran o'chiq va cho'ntakda bo'lsa ham) to'g'ridan-to'g'ri boshqarish uchun maxsus ishlab chiqilgan.

---

## 🎯 Asosiy imkoniyatlari
* **Oyna ochiq turishi shart emas:** Ilova oddiy ekran oynasi emas, balki **Foreground Service** sifatida ishlaydi.
* **Xalaqit bermaydi:** Ekranda hech narsa ochilib turmaydi, faqat bildirishnomalar panelida kichik belgi turadi. Ilovani yopib (swipe qilib) yuborsangiz ham u fonda uzluksiz ishlayveradi.
* **Cho‘ntak va qulflangan ekran rejimi:** Telefon qulflangan, cho‘ntakda bo‘lsa ham `WAKE_LOCK` va `WIFI_LOCK` yordamida aloqa uzilmaydi.
* **Bluetooth Quloqchin integratsiyasi (SCO):** Ovoz to'g'ridan-to'g'ri Bluetooth quloqchin mikrofonidan yozib olinadi va JARVIS'ning javob ovozi quloqchin karnayida yangraydi.
* **Quloqchin tugmasi (Push-to-Talk):** Quloqchindagi bitta tugmani bosish orqali mikrofon ochiladi (quloqchinda qisqa "bip" signali beriladi), gapirib bo'lgach yana bosiladi yoki jim turiladi.
* **Avtomatik ishga tushish (Boot):** Telefon o'chib-yonsa ham xizmat o'z-o'zidan ishga tushadi.
* **Xavfsiz va tejamkor:** Tailscale / yopiq tarmoq orqali uydagi kompyuterga ulanadi, batareyani ortiqcha sarflamaydi.

---

## 🛠️ Android Studio'da APK yig'ish (Build)

1. **Android Studio** dasturini oching.
2. **Open** tugmasini bosing va ushbu `JarvisCompanion-Android` papkasini tanlang.
3. Gradle sinxronizatsiyasi tugashini kuting (bir necha soniya).
4. Yuqori menyudan:
   * **Build** -> **Build Bundle(s) / APK(s)** -> **Build APK(s)** ni bosing.
5. Tayyor bo'lgan `app-debug.apk` faylini telefoningizga o'tkazing va o'rnating.

---

## 🚀 Telefonni sozlash va ishga tushirish

1. Telefoningizga **Tailscale** ilovasini o'rnating va uydagi kompyuteringiz bilan bitta tarmoqqa ulang (masalan kompyuteringizning Tailscale IP manzili `100.x.y.z`).
2. **JARVIS Companion** ilovasini oching:
   * **Kompyuter manzili:** Kompyuteringiz IP manzili va portini kiriting (masalan: `100.85.12.34:8000` yoki uydagi Wi-Fi bo'lsa `192.168.1.100:8000`).
   * **Xavfsizlik kaliti:** `jarvis-phone-companion-token` (bu kompyuterdagi `api_keys.json` faylida belgilangan).
   * **Xizmatni ishga tushirish** tugmasini bosing.
   * Mikrofon va bildirishnoma ruxsatlarini bering.
3. **Ilovani yopib yuboring!**
   * Xizmat bildirishnomalar panelida kichik belgi bilan ishlayveradi.
   * Bluetooth quloqchinni taqing.
   * Bildirishnoma panelidagi **[🎤 Gapirish]** tugmasi orqali yoki quloqchingizdagi play/pause tugmasini bosib gapiring.
   * JARVIS javobi to'g'ridan-to'g'ri quloqchingizda eshitiladi!
