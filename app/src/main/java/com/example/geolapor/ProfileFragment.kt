package com.example.geolapor.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import com.example.geolapor.LoginActivity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.example.geolapor.R
import com.example.geolapor.data.storage.PrefManager
import com.example.geolapor.databinding.FragmentProfileBinding
import java.io.File

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private lateinit var binding: FragmentProfileBinding
    private lateinit var pref: PrefManager
    private var photoUri: Uri? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding = FragmentProfileBinding.bind(view)
        pref = PrefManager.getInstance(requireContext())

        // minta permission kamera
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 100)
        }

        val user = pref.getUserData()
        binding.tvName.text = user.name
        binding.tvEmail.text = user.email

        loadProfilePhoto()

        binding.btnTakePhoto.setOnClickListener { takePhoto() }
        binding.btnPickGallery.setOnClickListener { pickPhoto() }
        binding.btnLogout.setOnClickListener {
            pref.logout()
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
        }
        binding.btnDeleteAccount.setOnClickListener {
            pref.deleteAccount()
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
        }
    }

    private fun loadProfilePhoto() {
        val uri = pref.getProfilePhoto() ?: return

        try {
            val stream = requireContext().contentResolver.openInputStream(Uri.parse(uri))
            val bitmap = android.graphics.BitmapFactory.decodeStream(stream)
            binding.imgProfile.setImageBitmap(bitmap)
            stream?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    // ===== GALERI =====
    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                pref.saveProfilePhoto(it.toString())
                loadProfilePhoto()
            }
        }

    private fun pickPhoto() {
        galleryLauncher.launch("image/*")
    }

    // ===== KAMERA =====
    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            if (ok) {
                pref.saveProfilePhoto(photoUri.toString())
                loadProfilePhoto()
            }
        }

    private fun takePhoto() {
        val file = File(requireContext().filesDir, "profile_${System.currentTimeMillis()}.jpg")

        photoUri = FileProvider.getUriForFile(
            requireContext(),
            requireContext().packageName + ".fileprovider",
            file
        )
        cameraLauncher.launch(photoUri)
    }
}
