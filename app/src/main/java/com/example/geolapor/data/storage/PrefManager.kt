package com.example.geolapor.data.storage

import android.content.Context
import com.example.geolapor.data.model.Report
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PrefManager private constructor(ctx: Context) {

    private val prefs = ctx.getSharedPreferences("geolapor_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private var INSTANCE: PrefManager? = null
        fun getInstance(ctx: Context): PrefManager =
            INSTANCE ?: PrefManager(ctx.applicationContext).also { INSTANCE = it }
    }

    // ---------------- LOGIN ----------------

    fun saveUser(email: String, pass: String) {
        prefs.edit()
            .putString("email", email)
            .putString("password", pass)
            .apply()
    }

    fun login(email: String, password: String) =
        prefs.getString("email", null) == email &&
                prefs.getString("password", null) == password

    fun saveLoginState(state: Boolean) {
        prefs.edit().putBoolean("loggedIn", state).apply()
    }

    fun isLoggedIn() =
        prefs.getBoolean("loggedIn", false)

    fun logout() {
        prefs.edit()
            .putBoolean("loggedIn", false)
            .apply()
    }

    // ---------------- REPORTS ----------------
    private val KEY_REPORTS = "reports_list"

    fun saveReports(list: List<Report>) {
        prefs.edit()
            .putString(KEY_REPORTS, gson.toJson(list))
            .apply()
    }

    fun loadReports(): List<Report> {
        val json = prefs.getString(KEY_REPORTS, null) ?: return emptyList()
        val type = object : TypeToken<List<Report>>() {}.type
        return gson.fromJson(json, type)
    }
}
