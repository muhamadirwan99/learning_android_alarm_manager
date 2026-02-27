# 🔔 MyAlarmManager

> Aplikasi Android untuk menjadwalkan **alarm sekali jalan** dan **alarm harian berulang** menggunakan `AlarmManager` API — dibangun sebagai proyek pembelajaran konsep penjadwalan task di background pada Android modern.

<br>

## 📱 Screenshots

| Home Screen | One-Time Alarm | Repeating Alarm |
|:-----------:|:--------------:|:---------------:|
| ![Home](docs/screenshot_home.png) | ![One-Time](docs/screenshot_onetime.png) | ![Repeating](docs/screenshot_repeating.png) |

> 💡 *Taruh screenshot atau GIF demo aplikasi kamu di folder `docs/` lalu ganti URL di atas.*

<br>

## ✨ Fitur Utama

- 📅 **DatePicker Dialog** — Pilih tanggal target alarm menggunakan `DatePickerFragment` berbasis `DialogFragment`, yang tahan rotasi layar.
- 🕐 **TimePicker Dialog** — Pilih jam alarm dengan format 24 jam melalui `TimePickerFragment`, dipakai untuk dua jenis alarm sekaligus.
- ⏰ **One-Time Alarm** — Jadwalkan notifikasi pada tanggal dan waktu spesifik yang hanya berbunyi sekali.
- 🔁 **Repeating Alarm** — Jadwalkan notifikasi harian yang berbunyi setiap hari pada jam yang sama via mekanisme **estafet** manual.
- 🚫 **Cancel Alarm** — Batalkan repeating alarm yang sudah terdaftar kapan saja.
- 🔔 **Notifikasi Sistem** — Tampilkan notifikasi lengkap dengan suara & getaran menggunakan `NotificationCompat` dan `NotificationChannel`.

<br>

## 🛠️ Teknologi yang Digunakan

| Teknologi | Keterangan |
|-----------|-----------|
| **Kotlin** | Bahasa pemrograman utama |
| **AlarmManager** | Menjadwalkan task tepat waktu di background |
| **BroadcastReceiver** | Menerima sinyal alarm dari sistem (`AlarmReceiver`) |
| **PendingIntent** | "Tiket" otorisasi yang diberikan ke AlarmManager untuk menembak Receiver |
| **NotificationManager** | Menampilkan notifikasi ke status bar saat alarm berbunyi |
| **NotificationChannel** | Wajib untuk Android 8.0+ (API 26+) agar notifikasi bisa tampil |
| **View Binding** | Akses view dari XML secara *type-safe* tanpa `findViewById()` |
| **DialogFragment** | Wrapper dialog tahan rotasi untuk DatePicker & TimePicker |
| **ActivityResultContracts** | Meminta izin runtime secara modern (menggantikan `onActivityResult`) |

<br>

## 🎓 Pelajaran Penting (Key Takeaways)

### 1. `RTC_WAKEUP` vs `ELAPSED_REALTIME_WAKEUP`
Dua mode utama AlarmManager yang sering membingungkan:

| Mode | Patokan Waktu | Kapan Dipakai |
|------|---------------|---------------|
| `RTC_WAKEUP` | **Jam nyata** (Unix timestamp) | Alarm pada jam tertentu, misal "setiap hari 07:00" ✅ |
| `ELAPSED_REALTIME_WAKEUP` | **Waktu sejak boot** (ms) | Timer/countdown sejak perangkat nyala |

> Proyek ini menggunakan `RTC_WAKEUP` karena alarm berbasis jam nyata yang diinginkan user.

---

### 2. Mengapa `setExactAndAllowWhileIdle()`, Bukan `setRepeating()`?

Android secara agresif menghemat baterai via **Doze Mode** (sejak Android 6). Hierarki ketepatan waktu:

```
setRepeating()          ← ❌ Deprecated, tidak akurat
setExact()              ← ⚠️  Bisa ditunda saat Doze aktif
setExactAndAllowWhileIdle() ← ✅ Menembus Doze, tepat waktu
```

`setRepeating()` yang lama juga sudah tidak menjamin ketepatan waktu sejak Android 6, sehingga kita harus mengimplementasi **estafet manual**: setiap kali alarm berbunyi di `onReceive()`, kita daftarkan ulang alarm untuk keesokan harinya.

---

### 3. PendingIntent Flags — Kunci Identitas Alarm

`PendingIntent` adalah "tiket" yang diberikan ke sistem untuk menembak `BroadcastReceiver` kita. Dua flag yang krusial:

| Flag | Fungsi |
|------|--------|
| `FLAG_UPDATE_CURRENT` | Perbarui data `Intent` (pesan alarm) jika PendingIntent dengan `requestCode` yang sama sudah ada |
| `FLAG_IMMUTABLE` | **Wajib** di Android 12+; sistem tidak boleh mengubah Intent ini |

> ⚠️ Untuk **membatalkan** alarm, PendingIntent yang dibuat di `cancelAlarm()` harus **identik** (flag + requestCode) dengan yang dipakai saat mendaftarkan alarm. Perbedaan sekecil apapun membuat pembatalan diam-diam gagal.

---

### 4. `Calendar.MONTH` Berbasis 0 — Jebakan Klasik!

Java/Kotlin `Calendar` API menggunakan indeks bulan berbasis **0**:

```kotlin
// ❌ Bug tersembunyi — Januari akan diset sebagai Februari!
calendar.set(Calendar.MONTH, Integer.parseInt(dateArray[1]))

// ✅ Benar — selalu kurangi 1
calendar.set(Calendar.MONTH, Integer.parseInt(dateArray[1]) - 1)
```

> Januari = 0, Februari = 1, ..., Desember = 11

---

### 5. Interface Callback — Komunikasi Fragment → Activity

Pola yang digunakan `DatePickerFragment` dan `TimePickerFragment` untuk mengirim hasil ke `MainActivity`:

```
User pilih tanggal/jam
        ↓
onDateSet() / onTimeSet()   ← dipanggil oleh sistem
        ↓
mListener?.onDialogDateSet()  ← diteruskan ke Activity via interface
        ↓
MainActivity.onDialogDateSet()  ← Activity menerima & update UI
```

Pola ini lebih aman dari passing data langsung karena Fragment dan Activity punya siklus hidup yang independen.

<br>

## ⚙️ Cara Setup

### Prasyarat
- Android Studio **Hedgehog** atau lebih baru
- JDK 11+
- Android device/emulator dengan API **24+**

### Langkah

```bash
# 1. Clone repository ini
git clone https://github.com/username/MyAlarmManager.git

# 2. Buka di Android Studio
# File → Open → pilih folder MyAlarmManager

# 3. Sync Gradle
# Klik "Sync Now" di banner notifikasi Gradle

# 4. Jalankan aplikasi
# Run → Run 'app' (Shift+F10)
```

### Izin yang Diperlukan

Aplikasi ini membutuhkan izin berikut (sudah dideklarasikan di `AndroidManifest.xml`):

| Izin | Keterangan | Cara Diminta |
|------|-----------|--------------|
| `POST_NOTIFICATIONS` | Menampilkan notifikasi | Runtime (Android 13+) — dialog otomatis muncul |
| `SCHEDULE_EXACT_ALARM` | Alarm presisi tinggi | Settings → Aplikasi → Alarm & Pengingat (Android 12+) |
| `VIBRATE` | Getaran notifikasi | Normal (otomatis diberikan) |
| `WAKE_LOCK` | Bangunkan CPU saat alarm | Normal (otomatis diberikan) |

<br>

## 📁 Struktur Project

```
app/src/main/
├── java/com/dicoding/myalarmmanager/
│   ├── MainActivity.kt          # UI utama & penghubung semua komponen
│   ├── AlarmReceiver.kt         # BroadcastReceiver + logika set/cancel alarm
│   ├── DatePickerFragment.kt    # Dialog pilih tanggal (untuk One-Time Alarm)
│   └── TimePickerFragment.kt    # Dialog pilih jam (untuk kedua jenis alarm)
├── res/
│   ├── layout/activity_main.xml # Layout UI utama
│   └── drawable/                # Ikon notifikasi
└── AndroidManifest.xml          # Deklarasi komponen & izin
```

<br>

## 📋 Alur Kerja Aplikasi

```
MainActivity (User set alarm)
        │
        ▼
AlarmReceiver.set*Alarm()
        │  mendaftarkan PendingIntent ke...
        ▼
AlarmManager (sistem OS)
        │  menembak saat waktunya tiba...
        ▼
AlarmReceiver.onReceive()
        │
        ├──► showAlarmNotification()  → Notifikasi muncul di status bar
        │
        └──► setRepeatingAlarm()      → (hanya jika REPEATING) Daftarkan ulang untuk besok
```

<br>

## 📚 Referensi

- [Android Developers — AlarmManager](https://developer.android.com/reference/android/app/AlarmManager)
- [Android Developers — Schedule Exact Alarms](https://developer.android.com/develop/background-work/services/alarms/schedule)
- [Android Developers — Notifications Overview](https://developer.android.com/develop/ui/views/notifications)
- [Android Developers — Doze Mode](https://developer.android.com/training/monitoring-device-state/doze-standby)

<br>

---

<div align="center">

Dibuat dengan ❤️ sebagai bagian dari pembelajaran **Android Development**

![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Min SDK](https://img.shields.io/badge/Min%20SDK-24-blue?style=for-the-badge)
![Target SDK](https://img.shields.io/badge/Target%20SDK-36-brightgreen?style=for-the-badge)

</div>

