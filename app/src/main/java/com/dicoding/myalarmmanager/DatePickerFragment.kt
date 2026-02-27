package com.dicoding.myalarmmanager

import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.widget.DatePicker
import androidx.fragment.app.DialogFragment
import java.util.Calendar

/**
 * Wrapper **DialogFragment** untuk `DatePickerDialog` bawaan Android.
 *
 * Alasan memakai Fragment (bukan langsung `DatePickerDialog`): Fragment selamat dari
 * perubahan konfigurasi (rotasi layar) sehingga dialog tidak hilang saat layar diputar.
 *
 * Hubungan ke file lain:
 * - `MainActivity.kt` → membuat instance dan menampilkan via `.show()`.
 * - `DialogDateListener` → interface callback yang wajib diimplementasi `MainActivity.kt`
 *   untuk menerima hasil pilihan tanggal dari user.
 */
class DatePickerFragment : DialogFragment(), DatePickerDialog.OnDateSetListener {

    /**
     * Menyimpan referensi ke Activity yang menggunakan fragment ini sebagai penerima callback.
     *
     * Dibuat nullable (`?`) dan di-null-kan di `onDetach()` untuk memutus referensi ke Activity
     * yang sudah tidak aktif, sehingga mencegah **memory leak**.
     */
    private var mListener: DialogDateListener? = null

    /**
     * Dipanggil saat Fragment ditempelkan ke Activity-nya — momen yang tepat untuk
     * "menangkap" referensi Activity sebagai listener.
     *
     * ⚠️ NOTE: Cast `context as DialogDateListener?` akan throw `ClassCastException` jika
     * Activity pemanggil **tidak mengimplementasi** interface `DialogDateListener`.
     * Pastikan `MainActivity.kt` selalu implements interface ini.
     */
    override fun onAttach(context: Context) {
        super.onAttach(context)
        mListener = context as DialogDateListener?
    }

    /**
     * Kebalikan `onAttach()` — dipanggil saat Fragment dilepas dari Activity-nya.
     * Kita null-kan `mListener` di sini untuk **memutus referensi** ke Activity
     * dan mencegah memory leak.
     */
    override fun onDetach() {
        super.onDetach()
        if (mListener != null) {
            mListener = null
        }
    }

    /**
     * Membangun dialog yang akan ditampilkan ke user.
     *
     * Menggunakan tanggal hari ini sebagai nilai awal `DatePickerDialog` agar user
     * tidak perlu scroll jauh dari tanggal default.
     */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val date = calendar.get(Calendar.DATE)

        return DatePickerDialog(activity as Context, this, year, month, date)
    }

    /**
     * Dipanggil **otomatis** oleh `DatePickerDialog` setelah user memilih tanggal dan menekan OK.
     *
     * Nilai diteruskan ke Activity melalui `mListener` — ini adalah pola komunikasi
     * **Fragment → Activity** yang direkomendasikan Android via interface/callback.
     *
     * ⚠️ NOTE: `month` di sini berbasis 0 (Januari = 0). Konversi ke string `"yyyy-MM-dd"`
     * dilakukan di sisi `MainActivity.kt` di `onDialogDateSet()`.
     */
    override fun onDateSet(view: DatePicker, year: Int, month: Int, dayOfMonth: Int) {
        mListener?.onDialogDateSet(tag, year, month, dayOfMonth)
    }

    /**
     * Kontrak antara `DatePickerFragment` dan Activity penggunanya.
     *
     * Setiap Activity yang ingin menampilkan fragment ini **wajib** mengimplementasi interface ini
     * agar bisa menerima hasil pilihan tanggal. Implementasinya ada di `MainActivity.kt`.
     */
    interface DialogDateListener {
        fun onDialogDateSet(tag: String?, year: Int, month: Int, dayOfMonth: Int)
    }
}