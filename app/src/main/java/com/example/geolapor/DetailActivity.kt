package com.example.geolapor

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.geolapor.data.storage.PrefManager
import com.example.geolapor.databinding.ActivityDetailBinding
import java.io.File

class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private lateinit var pref: PrefManager
    private var currentId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pref = PrefManager.getInstance(this)
        currentId = intent.getStringExtra("report_id")

        loadDetail()

        binding.btnShowOnMap.setOnClickListener {
            val r = findReport() ?: return@setOnClickListener
            val i = Intent(this, MapActivity::class.java)
            i.putExtra("center_lat", r.lat)
            i.putExtra("center_lon", r.lon)
            startActivity(i)
        }

        binding.btnDelete.setOnClickListener {
            val list = pref.loadReports().toMutableList()
            list.removeAll { it.id == currentId }
            pref.saveReports(list)
            finish()
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

        if (!r.photoPath.isNullOrEmpty()) {
            val f = File(r.photoPath)
            if (f.exists()) {
                binding.imgDetail.visibility = android.view.View.VISIBLE
                binding.imgDetail.setImageBitmap(BitmapFactory.decodeFile(f.absolutePath))
            }
        }
    }
}
