package com.example.geolapor

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.example.geolapor.data.storage.PrefManager
import com.example.geolapor.databinding.ActivityRegisterBinding

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var pref: PrefManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pref = PrefManager.getInstance(this)

        binding.btnRegister.setOnClickListener {

            val name = binding.edtName.text.toString().trim()
            val email = binding.edtEmail.text.toString().trim()
            val pass = binding.edtPassword.text.toString().trim()

            if (name.isEmpty() || email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Semua field harus diisi!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Simpan akun
            pref.saveUser(email, pass)
            pref.saveLoginState(true)

            Toast.makeText(this, "Akun berhasil dibuat!", Toast.LENGTH_SHORT).show()

            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
