package com.example.geolapor

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.geolapor.adapter.PointAdapter
import com.example.geolapor.data.storage.PrefManager
import com.example.geolapor.databinding.ActivityDataBinding

class DataActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDataBinding
    private lateinit var adapter: PointAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDataBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val pref = PrefManager.getInstance(this)

        if (!pref.isLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        adapter = PointAdapter(emptyList()) { report ->
            val i = Intent(this, DetailActivity::class.java)
            i.putExtra("report_id", report.id)
            startActivity(i)
        }

        binding.rvReports.layoutManager = LinearLayoutManager(this)
        binding.rvReports.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        adapter.update(PrefManager.getInstance(this).loadReports())
    }
}
