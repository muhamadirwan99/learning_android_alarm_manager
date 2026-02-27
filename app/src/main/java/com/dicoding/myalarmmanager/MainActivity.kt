package com.dicoding.myalarmmanager

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.dicoding.myalarmmanager.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Pintu masuk utama aplikasi — dideklarasikan di `AndroidManifest.xml` dengan
 * `intent-filter` MAIN + LAUNCHER sehingga ikonnya muncul di launcher HP.
 *
 * Mengimplementasi 3 interface sekaligus:
 * - `View.OnClickListener` → menangani semua klik tombol dalam satu fungsi `onClick()`.
 * - `DatePickerFragment.DialogDateListener` → menerima hasil pilihan tanggal dari `DatePickerFragment.kt`.
 * - `TimePickerFragment.DialogTimeListener` → menerima hasil pilihan jam dari `TimePickerFragment.kt`.
 */
class MainActivity : AppCompatActivity(), View.OnClickListener, DatePickerFragment.DialogDateListener,
    TimePickerFragment.DialogTimeListener {

    companion object {
        // Tag string ini dipakai sebagai identifier Fragment di FragmentManager,
        // sekaligus diterima oleh onDialogTimeSet(tag) untuk membedakan time picker mana yang ditutup.
        private const val DATE_PICKER_TAG = "DatePicker"
        private const val TIME_PICKER_ONCE_TAG = "TimePickerOnce"
        private const val TIME_PICKER_REPEAT_TAG = "TimePickerRepeat"
    }

    /**
     * View Binding: akses view dari `activity_main.xml` secara **type-safe** tanpa `findViewById()`.
     * Diaktifkan di `build.gradle.kts` via `buildFeatures { viewBinding = true }`.
     *
     * ⚠️ NOTE: `lateinit` berarti kita berjanji mengisi nilai ini sebelum dipakai (di `onCreate`).
     * Jika diakses sebelum diinisialisasi, akan throw `UninitializedPropertyAccessException`.
     */
    private lateinit var binding: ActivityMainBinding

    // AlarmReceiver diinstansiasi di sini hanya untuk memanggil fungsi-fungsi helper publiknya
    // (setOneTimeAlarm, setRepeatingAlarm, cancelAlarm) — bukan untuk registrasi dinamis.
    private lateinit var alarmReceiver: AlarmReceiver

    /**
     * Launcher modern untuk meminta izin runtime, menggantikan `onActivityResult()` yang deprecated.
     *
     * ⚠️ NOTE: Harus didaftarkan **sebelum** `onCreate()` selesai (sebagai property), karena
     * `registerForActivityResult()` hanya boleh dipanggil sebelum Activity di-attach ke lifecycle.
     *
     * ⚠️ NOTE: Jika user **menolak** izin `POST_NOTIFICATIONS` di Android 13+, notifikasi alarm
     * tidak akan muncul meskipun alarm tetap terdaftar dan berbunyi di background.
     */
    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(this, "Notifications permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Notifications permission rejected", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Membuat konten tampil di belakang status bar & nav bar untuk tampilan modern (edge-to-edge).
        enableEdgeToEdge()
        // Inflate layout dari activity_main.xml via View Binding — binding.root menggantikan R.layout.activity_main.
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Sesuaikan padding konten agar tidak tertutup system bars setelah enableEdgeToEdge() aktif.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        /**
         * `POST_NOTIFICATIONS` adalah izin runtime **baru** yang wajib diminta secara eksplisit
         * mulai Android 13 (API 33). Di Android 12 ke bawah izin ini tidak ada, jadi dibungkus
         * dengan pengecekan `SDK_INT`. Izin ini juga sudah dideklarasikan di `AndroidManifest.xml`.
         */
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Set satu listener (this) untuk semua tombol — lebih efisien daripada membuat
        // anonymous lambda terpisah untuk setiap tombol. Semua ditangani di onClick() di bawah.
        binding.btnOnceDate.setOnClickListener(this)
        binding.btnOnceTime.setOnClickListener(this)
        binding.btnSetOnceAlarm.setOnClickListener(this)

        binding.btnRepeatingTime.setOnClickListener(this)
        binding.btnSetRepeatingAlarm.setOnClickListener(this)

        binding.btnCancelRepeatingAlarm.setOnClickListener(this)

        alarmReceiver = AlarmReceiver()
    }

    /**
     * Menangani klik dari semua tombol yang didaftarkan di `onCreate()`.
     *
     * Pattern `when(v?.id)` melindungi dari potensi `View` null.
     * Setiap branch mendelegasikan tugasnya ke `AlarmReceiver.kt` atau membuka Fragment dialog.
     */
    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btn_once_date -> {
                // Hasil pilihan tanggal akan dikembalikan via callback onDialogDateSet() di bawah.
                val datePickerFragment = DatePickerFragment()
                datePickerFragment.show(supportFragmentManager, DATE_PICKER_TAG)
            }

            R.id.btn_once_time -> {
                // Tag TIME_PICKER_ONCE_TAG dipakai di onDialogTimeSet() untuk membedakan
                // time picker one-time vs repeating.
                val timePickerFragmentOne = TimePickerFragment()
                timePickerFragmentOne.show(supportFragmentManager, TIME_PICKER_ONCE_TAG)
            }

            R.id.btn_set_once_alarm -> {
                // Nilai tvOnceDate & tvOnceTime sudah diisi oleh onDialogDateSet() / onDialogTimeSet().
                // Jika masih kosong/format salah, setOneTimeAlarm() akan menghentikan proses via isDateInvalid().
                val onceDate = binding.tvOnceDate.text.toString()
                val onceTime = binding.tvOnceTime.text.toString()
                val onceMessage = binding.edtOnceMessage.text.toString()

                alarmReceiver.setOneTimeAlarm(
                    this, AlarmReceiver.TYPE_ONE_TIME,
                    onceDate,
                    onceTime,
                    onceMessage
                )
            }

            R.id.btn_repeating_time -> {
                val timePickerFragmentRepeat = TimePickerFragment()
                timePickerFragmentRepeat.show(supportFragmentManager, TIME_PICKER_REPEAT_TAG)
            }

            R.id.btn_set_repeating_alarm -> {
                // Repeating alarm tidak butuh tanggal — cukup jam dan pesan.
                // Pengulangan harian ditangani oleh mekanisme estafet di AlarmReceiver.kt.
                val repeatTime = binding.tvRepeatingTime.text.toString()
                val repeatMessage = binding.edtRepeatingMessage.text.toString()

                alarmReceiver.setRepeatingAlarm(
                    this, AlarmReceiver.TYPE_REPEATING,
                    repeatTime, repeatMessage
                )
            }

            R.id.btn_cancel_repeating_alarm -> {
                // Hanya alarm REPEATING yang dibatalkan — tidak ada tombol cancel untuk one-time
                // karena sifatnya memang hanya sekali dan sudah habis sendiri setelah berbunyi.
                alarmReceiver.cancelAlarm(this, AlarmReceiver.TYPE_REPEATING)
            }
        }
    }

    /**
     * Callback dari `DatePickerFragment.kt` — dipanggil setelah user memilih tanggal dan menekan OK.
     *
     * Parameter `month` berbasis 0 (Januari = 0) sesuai Calendar API, lalu kita format
     * ke string `"yyyy-MM-dd"` agar konsisten dengan `DATE_FORMAT` di `AlarmReceiver.kt`.
     */
    override fun onDialogDateSet(tag: String?, year: Int, month: Int, dayOfMonth: Int) {
        val calendar = Calendar.getInstance()
        calendar.set(year, month, dayOfMonth)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        binding.tvOnceDate.text = dateFormat.format(calendar.time)
    }

    /**
     * Callback dari `TimePickerFragment.kt` — dipanggil setelah user memilih jam dan menekan OK.
     *
     * Parameter `tag` berisi nilai TAG yang dikirim saat `.show(supportFragmentManager, TAG)`,
     * sehingga kita bisa tahu time picker mana (once atau repeat) yang baru ditutup user.
     * Waktu diformat ke `"HH:mm"` agar konsisten dengan `TIME_FORMAT` di `AlarmReceiver.kt`.
     */
    override fun onDialogTimeSet(tag: String?, hourOfDay: Int, minute: Int) {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
        calendar.set(Calendar.MINUTE, minute)
        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        when (tag) {
            TIME_PICKER_ONCE_TAG -> binding.tvOnceTime.text = dateFormat.format(calendar.time)
            TIME_PICKER_REPEAT_TAG -> {
                binding.tvRepeatingTime.text = dateFormat.format(calendar.time)
            }

            else -> {}
        }
    }

}