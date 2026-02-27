package com.dicoding.myalarmmanager

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Komponen **BroadcastReceiver** yang "terbangun" otomatis saat AlarmManager menembak sinyal
 * pada waktu yang sudah ditentukan.
 *
 * - Pendaftaran ke sistem ada di `AndroidManifest.xml` → tag `<receiver android:name=".AlarmReceiver"/>`.
 * - Fungsi publik (`setOneTimeAlarm`, `setRepeatingAlarm`, `cancelAlarm`) dipanggil dari `MainActivity.kt`.
 * - Kelas ini juga memanggil dirinya sendiri dari `onReceive()` sebagai mekanisme **estafet** alarm harian.
 */
class AlarmReceiver : BroadcastReceiver() {

    /**
     * Titik masuk utama — dipanggil sistem **secara otomatis** tepat saat alarm berbunyi.
     *
     * `Context` dan `Intent` dikirim oleh sistem; `Intent`-nya berisi data ekstra
     * (pesan & tipe) yang kita sisipkan saat mendaftarkan alarm di `set*Alarm()`.
     *
     * ⚠️ NOTE: Waktu eksekusi `onReceive()` sangat terbatas (~10 detik).
     * Jangan lakukan operasi berat (network, I/O) di sini secara langsung.
     */
    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra(EXTRA_TYPE)
        val message = intent.getStringExtra(EXTRA_MESSAGE)

        // Pakai ID berbeda agar notifikasi one-time & repeating bisa tampil bersamaan tanpa saling menimpa.
        val title = if (type.equals(TYPE_ONE_TIME, ignoreCase = true)) TYPE_ONE_TIME else TYPE_REPEATING
        val notifId = if (type.equals(TYPE_ONE_TIME, ignoreCase = true)) ID_ONETIME else ID_REPEATING

        // Cek null dulu — getStringExtra() bisa kembalikan null jika key tidak ditemukan.
        if (message != null) {
            showAlarmNotification(context, title, message, notifId)
        }

        /**
         * **Mekanisme Estafet Repeating Alarm**
         *
         * `setExactAndAllowWhileIdle()` **tidak** mendukung pengulangan otomatis seperti
         * `setRepeating()` yang sudah deprecated. Jadi setiap kali alarm berbunyi,
         * kita daftarkan ulang secara manual untuk keesokan harinya.
         *
         * Logika "besok" ditangani otomatis oleh pengecekan `Past Time` di dalam `setRepeatingAlarm()`.
         */
        if (type.equals(TYPE_REPEATING, ignoreCase = true)) {
            val calendar = Calendar.getInstance()
            val timeFormat = SimpleDateFormat(TIME_FORMAT, Locale.getDefault())
            val currentTimeString = timeFormat.format(calendar.time) // Ambil jam saat ini sebagai patokan
            setRepeatingAlarm(context, TYPE_REPEATING, currentTimeString, message ?: "")
        }
    }

    /**
     * Membangun dan menampilkan notifikasi ke status bar saat alarm berbunyi.
     *
     * - `notifId` berfungsi sebagai kunci: ID sama → **update** notifikasi lama; ID beda → **tambah** baru.
     * - Suara & getaran mengikuti setelan default sistem pengguna.
     *
     * ⚠️ NOTE: `NotificationChannel` **wajib** dibuat untuk Android 8.0+ (API 26+).
     * Tanpa channel, notifikasi akan **diam-diam diabaikan** sistem — tidak error, tapi tidak tampil.
     * `channelId` di builder harus **identik** dengan yang didaftarkan di `createNotificationChannel()`.
     */
    private fun showAlarmNotification(context: Context, title: String, message: String, notifId: Int) {
        val channelId = "Channel_1"
        val channelName = "AlarmManager channel"

        val notificationManagerCompat = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.baseline_access_time_24) // Wajib ada — tanpa ini notifikasi tidak muncul
            .setContentTitle(title)
            .setContentText(message)
            .setColor(ContextCompat.getColor(context, android.R.color.transparent))
            .setVibrate(longArrayOf(1000, 1000, 1000, 1000, 1000)) // Pola: getar(1s) - jeda(1s) - dst
            .setSound(alarmSound)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
            channel.enableVibration(true)
            channel.vibrationPattern = longArrayOf(1000, 1000, 1000, 1000, 1000)
            builder.setChannelId(channelId)
            // Aman dipanggil berkali-kali — jika channel sudah ada, sistem akan mengabaikannya.
            notificationManagerCompat.createNotificationChannel(channel)
        }

        val notification = builder.build()
        notificationManagerCompat.notify(notifId, notification)
    }

    /**
     * Membatalkan alarm yang sudah terdaftar di AlarmManager.
     *
     * Kunci pembatalan adalah **PendingIntent yang identik** — `requestCode` + `Intent` yang sama
     * persis dengan yang dipakai saat mendaftarkan alarm. Jika berbeda, alarm **tidak akan terbatalkan**.
     *
     * Dipanggil dari: `MainActivity.kt` → tombol `btn_cancel_repeating_alarm`.
     *
     * ⚠️ NOTE: `FLAG_IMMUTABLE` di sini harus **konsisten** dengan flag saat alarm didaftarkan
     * di `setRepeatingAlarm()`. Ketidakcocokan flag membuat sistem menganggapnya PendingIntent berbeda,
     * sehingga pembatalan **gagal tanpa pesan error**.
     */
    fun cancelAlarm(context: Context, type: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        // requestCode harus sama persis dengan yang dipakai saat alarm didaftarkan.
        val requestCode = if (type.equals(TYPE_ONE_TIME, ignoreCase = true)) ID_ONETIME else ID_REPEATING
        val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE)
        if (pendingIntent != null) {
            pendingIntent.cancel()          // Batalkan PendingIntent dari sistem
            alarmManager.cancel(pendingIntent) // Batalkan jadwal alarm yang terhubung
            Toast.makeText(context, "Repeating alarm dibatalkan", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Mendaftarkan alarm harian yang **berulang setiap hari** pada jam yang sama.
     *
     * Dipanggil dari dua tempat:
     * - `MainActivity.kt` → saat user pertama kali menekan **Set Repeating Alarm**.
     * - `onReceive()` → sebagai **mekanisme estafet** agar alarm bisa berbunyi lagi keesokan harinya.
     *
     * ⚠️ NOTE: `setExact()` biasa bisa **ditunda** saat Doze Mode aktif. Gunakan
     * `setExactAndAllowWhileIdle()` agar alarm tetap tepat waktu meski layar mati lama.
     *
     * ⚠️ NOTE: Sejak Android 12 (API 31), izin `SCHEDULE_EXACT_ALARM` di `AndroidManifest.xml`
     * **bisa dicabut user kapan saja** lewat Settings. Selalu cek `canScheduleExactAlarms()` sebelum
     * memanggil `setExactAndAllowWhileIdle()` untuk menghindari `SecurityException`.
     *
     * ⚠️ NOTE: `FLAG_UPDATE_CURRENT` memastikan data ekstra (pesan) di Intent **diperbarui**
     * jika PendingIntent dengan `requestCode` yang sama sudah ada sebelumnya.
     */
    fun setRepeatingAlarm(context: Context, type: String, time: String, message: String) {
        if (isDateInvalid(time, TIME_FORMAT)) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        // "Titipkan" data ke Intent — akan diambil kembali di onReceive() via getStringExtra().
        intent.putExtra(EXTRA_MESSAGE, message)
        intent.putExtra(EXTRA_TYPE, type)

        val timeArray = time.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(timeArray[0]))
        calendar.set(Calendar.MINUTE, Integer.parseInt(timeArray[1]))
        calendar.set(Calendar.SECOND, 0) // Nolkan detik agar alarm tepat di menit yang ditentukan

        // NOTE: Logika "Past Time" — mencegah alarm langsung bunyi jika jam sudah lewat hari ini.
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DATE, 1) // Majukan ke besok
        }

        // NOTE: Tambahkan FLAG_UPDATE_CURRENT agar pesan bisa di-update
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ID_REPEATING,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // PENTING: Gunakan setExactAndAllowWhileIdle agar menembus Doze Mode (Mode Hemat Baterai) Android
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            // Cek apakah kita punya izin Exact Alarm
            if (alarmManager.canScheduleExactAlarms()) {
                // Punya izin, gas jalan!
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, // Gunakan real-time clock & bangunkan perangkat saat alarm tiba
                    calendar.timeInMillis,
                    pendingIntent
                )
                Toast.makeText(context, "High Precision Alarm set up", Toast.LENGTH_SHORT).show()
            } else {
                // Jika belum punya izin, arahkan user ke halaman Settings untuk menyalakannya
                Toast.makeText(context, "Tolong izinkan alarm presisi tinggi di Pengaturan", Toast.LENGTH_LONG).show()
                // NOTE: FLAG_ACTIVITY_NEW_TASK wajib dipakai karena kita membuka Activity
                // dari BroadcastReceiver — bukan dari Activity. Tanpa flag ini aplikasi akan crash.
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            }

        } else {
            // Untuk Android 11 ke bawah (Tidak butuh izin khusus)
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
            Toast.makeText(context, "High Precision Alarm set up", Toast.LENGTH_SHORT).show()
        }

        Toast.makeText(context, "High Precision Repeating Alarm set up", Toast.LENGTH_SHORT).show()
    }

    /**
     * Mendaftarkan alarm **sekali jalan** pada tanggal dan waktu yang spesifik.
     *
     * Berbeda dengan `setRepeatingAlarm()`, fungsi ini:
     * - Menerima parameter `date` (tanggal lengkap format `yyyy-MM-dd`).
     * - Hanya dipanggil dari `MainActivity.kt` — **tidak ada mekanisme estafet**.
     *
     * ⚠️ NOTE: `Calendar.MONTH` berbasis **0** (Januari = 0, Desember = 11), makanya
     * kita kurangi 1 saat mengambil nilai dari string tanggal. Ini adalah jebakan klasik
     * Java/Kotlin Calendar API yang sering jadi sumber bug tersembunyi!
     *
     * ⚠️ NOTE: `alarmManager.set()` **tidak dijamin tepat waktu** saat Doze Mode aktif.
     * Jika presisi tinggi diperlukan, pertimbangkan ganti dengan `setExactAndAllowWhileIdle()`.
     */
    fun setOneTimeAlarm(context: Context, type: String, date: String, time: String, message: String) {
        if (isDateInvalid(date, DATE_FORMAT) || isDateInvalid(time, TIME_FORMAT)) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        intent.putExtra(EXTRA_MESSAGE, message)
        intent.putExtra(EXTRA_TYPE, type)

        Log.e("ONE TIME", "$date $time")

        val dateArray = date.split("-").toTypedArray()
        val timeArray = time.split(":").toTypedArray()
        val calendar = Calendar.getInstance()

        calendar.set(Calendar.YEAR, Integer.parseInt(dateArray[0]))
        calendar.set(Calendar.MONTH, Integer.parseInt(dateArray[1]) - 1) // ⚠️ Bulan berbasis 0!
        calendar.set(Calendar.DAY_OF_MONTH, Integer.parseInt(dateArray[2]))
        calendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(timeArray[0]))
        calendar.set(Calendar.MINUTE, Integer.parseInt(timeArray[1]))
        calendar.set(Calendar.SECOND, 0)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ID_ONETIME, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)

        Toast.makeText(context, "One time alarm set up", Toast.LENGTH_SHORT).show()
    }

    /**
     * Fungsi helper untuk memvalidasi format string tanggal atau waktu.
     *
     * Mengembalikan `true` jika format **tidak valid**, `false` jika valid.
     * Strategi *early return* di `set*Alarm()` mengandalkan nilai kembalian fungsi ini
     * untuk menghentikan proses sebelum crash terjadi.
     *
     * `isLenient = false` memaksa parsing **strict** — misalnya `"99:99"` tidak akan
     * diterima sebagai waktu valid. Default-nya lenient (longgar) yang bisa menimbulkan bug halus.
     */
    private fun isDateInvalid(date: String, format: String): Boolean {
        return try {
            val df = SimpleDateFormat(format, Locale.getDefault())
            df.isLenient = false
            df.parse(date)
            false // Parsing berhasil → format valid
        } catch (e: ParseException) {
            true // Parsing gagal → format tidak valid
        }
    }

    companion object {
        // Nilai tipe alarm — harus konsisten antara set*Alarm() dan onReceive().
        const val TYPE_ONE_TIME = "OneTimeAlarm"
        const val TYPE_REPEATING = "RepeatingAlarm"

        // Key Intent — gunakan konstanta agar tidak typo saat mengambil kembali via getStringExtra().
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_TYPE = "type"

        // Siapkan 2 id untuk 2 macam alarm, onetime dan repeating.
        // ⚠️ NOTE: Nilai harus unik di seluruh aplikasi — konflik ID bisa menyebabkan alarm/notifikasi
        // saling menimpa secara tidak sengaja.
        private const val ID_ONETIME = 100
        private const val ID_REPEATING = 101

        // Format ini harus konsisten di semua tempat yang pakai SimpleDateFormat untuk parsing/formatting.
        private const val DATE_FORMAT = "yyyy-MM-dd"
        private const val TIME_FORMAT = "HH:mm"
    }
}