package com.example.geolapor

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.geolapor.data.storage.PrefManager
import com.example.geolapor.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var pref: PrefManager

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pref = PrefManager.getInstance(this)

        // Tambahkan
        binding.btnLogout.setOnClickListener {
            pref.logout()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        binding.btnMap.setOnClickListener {
            startActivity(Intent(this, MapActivity::class.java))
        }

        binding.btnData.setOnClickListener {
            startActivity(Intent(this, DataActivity::class.java))
        }
    }
}
