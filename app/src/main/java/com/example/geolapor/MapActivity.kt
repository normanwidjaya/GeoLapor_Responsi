package com.example.geolapor

import android.Manifest
import android.app.AlertDialog
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
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
import org.maplibre.android.maps.MapLibreMap
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

class MapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMapBinding
    private lateinit var map: MapLibreMap
    private lateinit var pref: PrefManager

    // photo URI used by TakePicture
    private var photoTempUri: Uri? = null

    private val client = OkHttpClient()

    // hasil kamera
    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            if (ok) {
                Toast.makeText(this, "Foto diambil", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Foto gagal", Toast.LENGTH_SHORT).show()
            }
        }

    // permission lokasi
    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            enableUserLocation()
        } else {
            Toast.makeText(
                this,
                "Izin lokasi dibutuhkan untuk memusatkan peta",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // WAJIB: init MapLibre dulu
        MapLibre.getInstance(this)

        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pref = PrefManager.getInstance(this)

        // MapView lifecycle
        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync { mapLibre ->
            map = mapLibre

            map.setStyle("https://tiles.openfreemap.org/styles/liberty") {

                // load semua marker dari Pref
                loadMarkers()

                // kalau datang dari DetailActivity
                val lat = intent.getDoubleExtra("center_lat", 0.0)
                val lon = intent.getDoubleExtra("center_lon", 0.0)
                if (lat != 0.0 || lon != 0.0) {
                    map.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(lat, lon),
                            16.0
                        )
                    )
                } else {
                    // kalau tidak, coba fokus ke lokasi user
                    if (ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        enableUserLocation()
                    } else {
                        requestLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                }

                // long-press untuk tambah laporan
                map.addOnMapLongClickListener { latLng ->
                    showAddReportDialog(latLng)
                    true
                }
            }
        }

        // isi spinner search mode dari kode (biar gak tergantung @array)
        val searchModes = listOf("Search reports", "Search place (geocode)")
        binding.spinnerSearchMode.adapter =
            ArrayAdapter(this, android.R.layout.simple_list_item_1, searchModes)

        // listener untuk SearchView
        binding.searchView.setOnQueryTextListener(
            object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    query?.let { performSearch(it) }
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean = false
            }
        )

        // FAB untuk fokus ke laporan terakhir
        binding.fabCenter.setOnClickListener { centerToLastReport() }
    }

    // ================== SEARCH ==================

    private fun performSearch(query: String) {
        val mode = binding.spinnerSearchMode.selectedItemPosition
        if (mode == 0) {
            // search reports (local)
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
                        LatLng(r.lat, r.lon),
                        15.0
                    )
                )
            } else {
                Toast.makeText(
                    this,
                    "Tidak ada laporan sesuai pencarian",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            // geocode via Nominatim
            thread {
                try {
                    val url =
                        "https://nominatim.openstreetmap.org/search?q=${
                            java.net.URLEncoder.encode(
                                query,
                                "utf-8"
                            )
                        }&format=json&limit=1"
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
                                    LatLng(lat, lon),
                                    15.0
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

    // ================== LOKASI USER ==================

    private fun enableUserLocation() {
        // cek izin dulu, kalau belum → langsung return
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val fused = LocationServices.getFusedLocationProviderClient(this)
        fused.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                val latLng = LatLng(loc.latitude, loc.longitude)
                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(latLng, 16.0)
                )
            }
        }
    }

    // ================== MARKER ==================

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

    private fun centerToLastReport() {
        val list = pref.loadReports()
        if (list.isNotEmpty()) {
            val last = list.last()
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(last.lat, last.lon),
                    15.0
                )
            )
        } else {
            Toast.makeText(this, "Belum ada laporan", Toast.LENGTH_SHORT).show()
        }
    }

    // ================== DIALOG INPUT LAPORAN ==================

    private fun showAddReportDialog(latLng: LatLng) {
        val b = DialogAddReportBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(b.root)
            .setPositiveButton("Simpan", null)
            .setNegativeButton("Batal", null)
            .create()

        // kategori / subkategori lengkap
        val categories = mapOf(
            "Infrastruktur" to listOf(
                "Jalan Rusak",
                "Jembatan Rusak",
                "Lampu Jalan Mati",
                "Drainase Tersumbat",
                "Trotoar Rusak",
                "Rambu Hilang"
            ),
            "Bencana" to listOf(
                "Banjir",
                "Tanah Longsor",
                "Pohon Tumbang",
                "Kebakaran",
                "Gempa",
                "Angin Kencang"
            ),
            "Lingkungan" to listOf(
                "Sampah Menumpuk",
                "Polusi",
                "Pencemaran Air"
            )
        )

        val catKeys = categories.keys.toList()
        b.spinnerCategory.adapter =
            ArrayAdapter(this, android.R.layout.simple_list_item_1, catKeys)

        b.spinnerCategory.setOnItemSelectedListener(object :
            android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>,
                view: android.view.View?,
                position: Int,
                id: Long
            ) {
                val key = catKeys[position]
                val subs = categories[key] ?: emptyList()
                b.spinnerSub.adapter =
                    ArrayAdapter(
                        this@MapActivity,
                        android.R.layout.simple_list_item_1,
                        subs
                    )
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        })

        // tanggal via DatePicker (default = hari ini)
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        b.tvDate.text = sdf.format(Date())
        b.tvDate.setOnClickListener {
            val c = Calendar.getInstance()
            val dp = android.app.DatePickerDialog(
                this,
                { _, y, m, d ->
                    val cal = Calendar.getInstance()
                    cal.set(y, m, d)
                    b.tvDate.text = sdf.format(cal.time)
                },
                c.get(Calendar.YEAR),
                c.get(Calendar.MONTH),
                c.get(Calendar.DAY_OF_MONTH)
            )
            dp.show()
        }

        // tombol ambil foto
        b.btnTakePhoto.setOnClickListener {
            val timeStamp =
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "IMG_${timeStamp}.jpg"
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            }
            val uri =
                contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            photoTempUri = uri
            takePictureLauncher.launch(uri)
        }

        b.btnRemovePhoto.setOnClickListener {
            b.imgPreview.setImageDrawable(null)
            b.imgPreview.visibility = android.view.View.GONE
            photoTempUri = null
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = b.edtTitle.text.toString().trim()
                if (title.isEmpty()) {
                    b.edtTitle.error = "Judul wajib diisi"
                    return@setOnClickListener
                }

                val report = Report(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    category = b.spinnerCategory.selectedItem.toString(),
                    subCategory = b.spinnerSub.selectedItem?.toString() ?: "",
                    description = b.edtDesc.text.toString(),
                    lat = latLng.latitude,
                    lon = latLng.longitude,
                    date = b.tvDate.text.toString(),
                    reporterName = b.edtName.text.toString().ifEmpty { null },
                    reporterPhone = b.edtPhone.text.toString().ifEmpty { null },
                    reporterAddress = b.edtAddress.text.toString().ifEmpty { null },
                    photoPath = photoTempUri?.let { uri -> uriToFilePath(uri) }
                )

                val list = pref.loadReports().toMutableList()
                list.add(report)
                pref.saveReports(list)

                map.addMarker(
                    org.maplibre.android.annotations.MarkerOptions()
                        .position(LatLng(report.lat, report.lon))
                        .title(report.title)
                )

                Toast.makeText(this, "Laporan tersimpan", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    // helper: copy URI ke internal storage, simpan path-nya
    private fun uriToFilePath(uri: Uri): String? {
        return try {
            val input = contentResolver.openInputStream(uri) ?: return uri.toString()
            val dir = File(filesDir, "images")
            if (!dir.exists()) dir.mkdirs()
            val fileName = "IMG_${System.currentTimeMillis()}.jpg"
            val dest = File(dir, fileName)
            input.use { inp ->
                dest.outputStream().use { out ->
                    inp.copyTo(out)
                }
            }
            dest.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            uri.toString()
        }
    }

    // MapView lifecycle
    override fun onStart() {
        super.onStart()
        binding.mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        binding.mapView.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapView.onLowMemory()
    }
}
