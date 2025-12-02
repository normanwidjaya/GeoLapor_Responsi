package com.example.geolapor

import android.Manifest
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import com.example.geolapor.data.model.Report
import com.example.geolapor.data.storage.PrefManager
import com.example.geolapor.databinding.ActivityMapBinding
import com.example.geolapor.databinding.DialogAddReportBinding
import com.google.android.gms.location.LocationServices
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import android.view.View
import android.widget.ImageView
import kotlin.concurrent.thread

class MapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMapBinding
    private lateinit var map: MapLibreMap
    private lateinit var pref: PrefManager

    private var photoTempUri: Uri? = null
    private var currentImageView: ImageView? = null
    private val client = OkHttpClient()

    // CAMERA SAFE
    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            if (ok) {
                currentImageView?.setImageURI(photoTempUri)
                currentImageView?.visibility = View.VISIBLE
                Toast.makeText(this, "Foto berhasil", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Gagal mengambil foto", Toast.LENGTH_SHORT).show()
            }
        }

    private fun createImageFile(): File {
        val dir = getExternalFilesDir("Pictures")!!
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "IMG_${System.currentTimeMillis()}.jpg")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MapLibre.getInstance(this)
        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Minta izin kamera sekali
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            99
        )

        pref = PrefManager.getInstance(this)

        // INIT MAP
        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync { mapLibre ->
            map = mapLibre

            map.setStyle("https://tiles.openfreemap.org/styles/liberty") {

                enableLiveLocation()
                loadMarkers()
                moveToUserLocationOnce()

                // Long press tambah marker
                map.addOnMapLongClickListener { latLng ->
                    showAddReportDialog(latLng)
                    true
                }
            }
        }

        // Search
        initSearch()
        val search = binding.searchView
        search.setIconifiedByDefault(false)
        search.isSubmitButtonEnabled = false
        search.clearFocus()


        // ======= BUTTON CENTER =======
        binding.btnCenter.setOnClickListener {
            moveToUserLocationOnce()
        }

        // ====== BUTTON MARKER (PAKAI CROSSHAIR / CAMERA CENTER) ========
        binding.btnAddMarker.setOnClickListener {
            if (!::map.isInitialized) return@setOnClickListener

            val target: LatLng? = map.cameraPosition.target
            if (target == null) {
                Toast.makeText(this, "Posisi peta belum siap", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showAddReportDialog(target)
        }
    }

    // ================== LOCATION ==================
    private fun enableLiveLocation() {

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        map.locationComponent.apply {
            activateLocationComponent(
                LocationComponentActivationOptions.builder(
                    this@MapActivity,
                    map.style!!
                ).build()
            )
            isLocationComponentEnabled = true
            cameraMode = CameraMode.TRACKING
            renderMode = RenderMode.COMPASS
        }
    }

    private fun moveToUserLocationOnce() {

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val fused = LocationServices.getFusedLocationProviderClient(this)

        fused.lastLocation.addOnSuccessListener {
            it?.let { loc ->
                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(loc.latitude, loc.longitude), 16.0
                    )
                )
            }
        }
    }

    // ================== EXISTING MARKERS ==================
    private fun loadMarkers() {
        val list = pref.loadReports()
        for (r in list) {
            map.addMarker(
                org.maplibre.android.annotations.MarkerOptions()
                    .position(LatLng(r.lat, r.lon))
                    .title(r.title)
            )
        }
    }

    // ================= SEARCH =================
    private fun initSearch() {
        // 0 = laporan, 1 = tempat (geocode)
        val searchModes = listOf("Search reports", "Search place (geocode)")
        binding.spinnerSearchMode.adapter =
            ArrayAdapter(this, android.R.layout.simple_list_item_1, searchModes)

        binding.searchView.setOnQueryTextListener(
            object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    query?.let { performSearch(it) }
                    return true
                }

                override fun onQueryTextChange(newText: String?) = false
            })
    }

    private fun performSearch(query: String) {
        val mode = binding.spinnerSearchMode.selectedItemPosition

        if (mode == 0) {
            // ======= SEARCH LAPORAN (LOCAL) =======
            val list = pref.loadReports().filter {
                it.title.contains(query, true) ||
                        (it.reporterName?.contains(query, true) ?: false) ||
                        it.subCategory.contains(query, true) ||
                        it.category.contains(query, true)
            }
            if (list.isNotEmpty()) {
                val r = list[0]
                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(r.lat, r.lon), 15.0
                    )
                )
            } else {
                Toast.makeText(this, "Tidak ada laporan sesuai pencarian", Toast.LENGTH_SHORT).show()
            }
        } else {
            // ======= SEARCH TEMPAT (GEOCODE) =======
            thread {
                try {
                    val encoded = URLEncoder.encode(query, "utf-8")
                    val url =
                        "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=1"
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", "GeoLaporApp/1.0")
                        .build()
                    val res = client.newCall(req).execute()
                    val body = res.body?.string() ?: ""
                    val arr = JSONArray(body)

                    if (arr.length() > 0) {
                        val o = arr.getJSONObject(0)
                        val lat = o.getDouble("lat")
                        val lon = o.getDouble("lon")
                        runOnUiThread {
                            map.animateCamera(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(lat, lon), 15.0
                                )
                            )
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(
                                this,
                                "Lokasi tidak ditemukan",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            "Gagal mengakses geocoding",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    // ================== DIALOG ADD REPORT ==================
    private fun showAddReportDialog(latLng: LatLng) {

        val b = DialogAddReportBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(b.root)
            .setPositiveButton("Simpan", null)
            .setNegativeButton("Batal", null)
            .create()

        // ====== Kategori & SubKategori ========
        val categories = mapOf(
            "Infrastruktur" to listOf(
                "Jalan Rusak", "Jembatan Rusak", "Lampu Jalan Mati",
                "Drainase Tersumbat", "Trotoar Rusak", "Rambu Hilang"
            ),
            "Bencana" to listOf(
                "Banjir", "Tanah Longsor", "Pohon Tumbang",
                "Kebakaran", "Gempa", "Angin Kencang"
            ),
            "Lingkungan" to listOf(
                "Sampah Menumpuk", "Polusi", "Pencemaran Air"
            )
        )

        val catKeys = categories.keys.toList()
        b.spinnerCategory.adapter =
            ArrayAdapter(this, android.R.layout.simple_list_item_1, catKeys)

        b.spinnerCategory.setOnItemSelectedListener(object :
            android.widget.AdapterView.OnItemSelectedListener {

            override fun onItemSelected(
                parent: android.widget.AdapterView<*>,
                view: android.view.View?, pos: Int, id: Long
            ) {
                val subs = categories[catKeys[pos]] ?: emptyList()
                b.spinnerSub.adapter =
                    ArrayAdapter(
                        this@MapActivity,
                        android.R.layout.simple_list_item_1,
                        subs
                    )
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        })

        // ===== DATE PICKER =====
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        b.tvDate.text = sdf.format(Date())
        b.tvDate.setOnClickListener {
            val c = Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, y, m, d ->
                    val cal = Calendar.getInstance()
                    cal.set(y, m, d)
                    b.tvDate.text = sdf.format(cal.time)
                },
                c.get(Calendar.YEAR),
                c.get(Calendar.MONTH),
                c.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        // ===== FOTO =====
        b.btnTakePhoto.setOnClickListener {
            val file = createImageFile()
            photoTempUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            takePictureLauncher.launch(photoTempUri)
            b.imgPreview.setImageURI(photoTempUri)
            b.imgPreview.visibility = View.VISIBLE
            currentImageView=b.imgPreview
        }
        b.btnRemovePhoto.setOnClickListener {
            photoTempUri = null
            b.imgPreview.setImageDrawable(null)
            b.imgPreview.visibility = View.GONE
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {

                val report = Report(
                    id = UUID.randomUUID().toString(),
                    title = b.edtTitle.text.toString(),
                    category = b.spinnerCategory.selectedItem.toString(),
                    subCategory = b.spinnerSub.selectedItem.toString(),
                    description = b.edtDesc.text.toString(),
                    lat = latLng.latitude,
                    lon = latLng.longitude,
                    date = b.tvDate.text.toString(),
                    reporterName = b.edtName.text.toString(),
                    reporterPhone = b.edtPhone.text.toString(),
                    reporterAddress = b.edtAddress.text.toString(),
                    photoPath = photoTempUri?.toString()?:""
                )

                val list = pref.loadReports().toMutableList()
                list.add(report)
                pref.saveReports(list)

                // langsung tampil marker
                map.addMarker(
                    org.maplibre.android.annotations.MarkerOptions()
                        .position(LatLng(report.lat, report.lon))
                        .title(report.title)
                )

                dialog.dismiss()
            }
        }

        dialog.show()
    }

    // Lifecycle MapView
    override fun onStart() { super.onStart(); binding.mapView.onStart() }
    override fun onResume() { super.onResume(); binding.mapView.onResume() }
    override fun onPause() { super.onPause(); binding.mapView.onPause() }
    override fun onStop() { super.onStop(); binding.mapView.onStop() }
    override fun onDestroy() { super.onDestroy(); binding.mapView.onDestroy() }
    override fun onLowMemory() { super.onLowMemory(); binding.mapView.onLowMemory() }
}
