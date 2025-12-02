package com.example.geolapor

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.geolapor.data.model.Report
import com.example.geolapor.data.storage.PrefManager
import com.example.geolapor.databinding.ActivityDetailBinding
import com.example.geolapor.databinding.DialogAddReportBinding
import java.io.File
import androidx.core.content.FileProvider
import android.content.Intent



class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private lateinit var pref: PrefManager
    private var currentId: String? = null

    private var editPhotoUri: Uri? = null
    private var editImageFile: File? = null

    // Launcher untuk kamera
    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            if (ok) {
                bindingDialog?.imgPreview?.setImageURI(editPhotoUri)
                bindingDialog?.imgPreview?.visibility = View.VISIBLE
            }
        }

    private var bindingDialog: DialogAddReportBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pref = PrefManager.getInstance(this)
        currentId = intent.getStringExtra("report_id")

        loadDetail()

        binding.btnEdit.setOnClickListener {
            findReport()?.let { r -> showEditDialog(r) }
        }

        binding.btnDelete.setOnClickListener {
            val list = pref.loadReports().toMutableList()
            list.removeAll { it.id == currentId }
            pref.saveReports(list)
            finish()
        }
        binding.btnShowOnMap.setOnClickListener {
            val r = findReport() ?: return@setOnClickListener
            val i = Intent(this, MapActivity::class.java)
            i.putExtra("center_lat", r.lat)
            i.putExtra("center_lon", r.lon)
            startActivity(i)
        }

    }

    private fun findReport() =
        pref.loadReports().firstOrNull { it.id == currentId }

    private fun loadDetail() {
        val r = findReport() ?: return

        binding.tvTitle.text = r.title
        binding.tvReporter.text =
            "Pelapor: ${r.reporterName ?: "-"}\n${r.reporterPhone ?: "-"}\n${r.reporterAddress ?: "-"}"
        binding.tvCategory.text = "Kategori: ${r.category}"
        binding.tvSub.text = "Sub: ${r.subCategory}"
        binding.tvCoord.text = "Koordinat: ${r.lat}, ${r.lon}"
        binding.tvDate.text = "Tanggal: ${r.date}"
        binding.tvDesc.text = "Deskripsi: ${r.description}"

        if (!r.photoPath.isNullOrEmpty()) {
            binding.imgDetail.visibility = View.VISIBLE
            binding.imgDetail.setImageURI(Uri.parse(r.photoPath))
        } else {
            binding.imgDetail.visibility = View.GONE
        }
    }

    // ==============================================================
    //                         EDIT LAPORAN
    // ==============================================================

    private fun showEditDialog(r: Report) {

        val b = DialogAddReportBinding.inflate(layoutInflater)
        bindingDialog = b

        val dialog = AlertDialog.Builder(this)
            .setView(b.root)
            .setPositiveButton("Simpan", null)
            .setNegativeButton("Batal", null)
            .create()

        // ===================== ISI DATA LAMA ======================
        b.edtTitle.setText(r.title)
        b.edtDesc.setText(r.description)
        b.edtName.setText(r.reporterName)
        b.edtPhone.setText(r.reporterPhone)
        b.edtAddress.setText(r.reporterAddress)
        b.tvDate.text = r.date

        // FOTO LAMA
        if (!r.photoPath.isNullOrEmpty()) {
            editPhotoUri = Uri.parse(r.photoPath)
            b.imgPreview.setImageURI(editPhotoUri)
            b.imgPreview.visibility = View.VISIBLE
        }

        // ===================== KATEGORI & SUB ======================
        val categories = mapOf(
            "Infrastruktur" to listOf("Jalan Rusak", "Jembatan Rusak", "Lampu Jalan Mati", "Drainase Tersumbat", "Trotoar Rusak", "Rambu Hilang"),
            "Bencana" to listOf("Banjir", "Tanah Longsor", "Pohon Tumbang", "Kebakaran", "Gempa", "Angin Kencang"),
            "Lingkungan" to listOf("Sampah Menumpuk", "Polusi", "Pencemaran Air")
        )

        val catList = categories.keys.toList()
        b.spinnerCategory.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, catList)

        val currentCatIndex = catList.indexOf(r.category)
        if (currentCatIndex >= 0) b.spinnerCategory.setSelection(currentCatIndex)

        fun loadSub() {
            val subs = categories[b.spinnerCategory.selectedItem.toString()] ?: emptyList()
            b.spinnerSub.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, subs)

            val idx = subs.indexOf(r.subCategory)
            if (idx >= 0) b.spinnerSub.setSelection(idx)
        }

        loadSub()

        b.spinnerCategory.setOnItemSelectedListener(object :
            android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>, view: View?, pos: Int, id: Long
            ) {
                loadSub()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        })

        // ===================== FOTO ======================
        b.btnTakePhoto.setOnClickListener {
            // Buat file baru
            editImageFile = File(getExternalFilesDir("Pictures"), "EDIT_${System.currentTimeMillis()}.jpg")

            // Berikan akses aman ke kamera
            editPhotoUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                editImageFile!!
            )

            // Jalankan kamera
            takePictureLauncher.launch(editPhotoUri)
        }

        b.btnRemovePhoto.setOnClickListener {
            editPhotoUri = null
            b.imgPreview.visibility = View.GONE
            b.imgPreview.setImageDrawable(null)
        }

        // ===================== SAVE BUTTON ======================
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {

                val newTitle = b.edtTitle.text.toString().trim()
                if (newTitle.isEmpty()) {
                    b.edtTitle.error = "Judul wajib diisi!"
                    return@setOnClickListener
                }

                val newCat = b.spinnerCategory.selectedItem.toString()
                val newSub = b.spinnerSub.selectedItem.toString()

                val edited = r.copy(
                    title = newTitle,
                    category = newCat,
                    subCategory = newSub,
                    description = b.edtDesc.text.toString(),
                    reporterName = b.edtName.text.toString(),
                    reporterPhone = b.edtPhone.text.toString(),
                    reporterAddress = b.edtAddress.text.toString(),
                    photoPath = editPhotoUri?.toString()
                )

                val list = pref.loadReports().toMutableList()
                list.removeAll { it.id == r.id }
                list.add(edited)
                pref.saveReports(list)

                Toast.makeText(this, "Laporan berhasil diperbarui", Toast.LENGTH_SHORT).show()
                loadDetail()
                dialog.dismiss()
            }
        }

        dialog.show()
    }
}
