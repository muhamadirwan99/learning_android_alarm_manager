package com.dicoding.myalarmmanager

import android.app.Dialog
import android.app.TimePickerDialog
import android.content.Context
import android.icu.util.Calendar
import android.os.Bundle
import android.widget.TimePicker
import androidx.fragment.app.DialogFragment

/**
 * Wrapper **DialogFragment** untuk `TimePickerDialog` bawaan Android.
 *
 * Strukturnya identik dengan `DatePickerFragment.kt` — sama-sama DialogFragment dengan pola
 * interface callback untuk meneruskan hasil ke Activity pemanggil (`MainActivity.kt`).
 *
 * Fragment ini dipakai **dua kali** di aplikasi:
 * - Satu untuk **one-time alarm** (tag: `TIME_PICKER_ONCE_TAG`).
 * - Satu untuk **repeating alarm** (tag: `TIME_PICKER_REPEAT_TAG`).
 *
 * Pembeda keduanya adalah TAG yang dikirim saat `.show()`, yang kemudian dibaca
 * di `onDialogTimeSet()` di `MainActivity.kt`.
 */
class TimePickerFragment : DialogFragment(), TimePickerDialog.OnTimeSetListener {

    /**
     * Menyimpan referensi ke Activity sebagai penerima callback hasil pilihan waktu.
     *
     * Di-null-kan di `onDetach()` untuk mencegah **memory leak** —
     * pola yang sama digunakan di `DatePickerFragment.kt`.
     */
    private var mListener: DialogTimeListener? = null

    /**
     * Dipanggil saat Fragment ditempelkan ke Activity-nya.
     *
     * ⚠️ NOTE: Cast `context as DialogTimeListener?` akan throw `ClassCastException` jika
     * Activity pemanggil **tidak mengimplementasi** interface `DialogTimeListener`.
     * Pastikan `MainActivity.kt` selalu implements interface ini.
     */
    override fun onAttach(context: Context) {
        super.onAttach(context)

        mListener = context as DialogTimeListener?
    }

    /**
     * Memutus referensi ke Activity saat Fragment dilepas untuk mencegah **memory leak**.
     */
    override fun onDetach() {
        super.onDetach()
        if (mListener != null) {
            mListener = null
        }
    }

    /**
     * Membangun `TimePickerDialog` dengan jam dan menit saat ini sebagai nilai awal.
     *
     * ⚠️ NOTE: Di sini dipakai `android.icu.util.Calendar` (bukan `java.util.Calendar`).
     * Keduanya memiliki API yang mirip, namun `android.icu.util.Calendar` hanya tersedia
     * di **API 24+**. Karena `minSdk` aplikasi ini adalah 24 (lihat `build.gradle.kts`),
     * penggunaan ini aman.
     */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY) // HOUR_OF_DAY = format 24 jam (0–23)
        val minute = calendar.get(Calendar.MINUTE)
        val formatHour24 = true // Tampilkan dalam format 24 jam, bukan AM/PM

        return TimePickerDialog(activity, this, hour, minute, formatHour24)

    }

    /**
     * Dipanggil **otomatis** oleh `TimePickerDialog` setelah user memilih jam dan menekan OK.
     *
     * Nilai `tag` yang diteruskan ke `mListener` inilah yang dipakai `MainActivity.kt`
     * untuk membedakan apakah hasil ini untuk **one-time** atau **repeating** alarm.
     */
    override fun onTimeSet(view: TimePicker?, hourOfDay: Int, minute: Int) {
        mListener?.onDialogTimeSet(tag, hourOfDay, minute)
    }

    /**
     * Kontrak antara `TimePickerFragment` dan Activity penggunanya.
     *
     * Setiap Activity yang ingin menampilkan fragment ini **wajib** mengimplementasi interface ini
     * agar bisa menerima hasil pilihan waktu. Implementasinya ada di `MainActivity.kt`.
     */
    interface DialogTimeListener {
        fun onDialogTimeSet(tag: String?, hourOfDay: Int, minute: Int)
    }
}