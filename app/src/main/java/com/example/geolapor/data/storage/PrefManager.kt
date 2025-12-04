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

    fun saveUser(name: String, email: String, pass: String) {
        prefs.edit()
            .putString("name", name)
            .putString("email", email)
            .putString("password", pass)
            .apply()
    }

    fun login(email: String, password: String): Boolean {
        return prefs.getString("email", null) == email &&
                prefs.getString("password", null) == password
    }

    fun saveLoginState(state: Boolean) {
        prefs.edit().putBoolean("loggedIn", state).apply()
    }

    fun isLoggedIn(): Boolean =
        prefs.getBoolean("loggedIn", false)

    fun logout() {
        prefs.edit().apply {
            putBoolean("loggedIn", false)
            apply()
        }
    }

    fun deleteAccount() {
        prefs.edit().clear().apply()
    }

    fun getUserData(): User {
        return User(
            prefs.getString("name", "") ?: "",
            prefs.getString("email", "") ?: ""
        )
    }

    data class User(
        val name: String,
        val email: String
    )


    // ---------------- REPORTS ----------------

    private fun reportKey():String{
        val email = prefs.getString("email", "")!!
        return "reports_list_$email"
    }

    fun saveReports(list: List<Report>) {
        prefs.edit()
            .putString(reportKey(), gson.toJson(list))
            .apply()
    }
    fun loadReports(): List<Report> {
        val json = prefs.getString(reportKey(), null) ?: return emptyList()
        val type = object : TypeToken<List<Report>>() {}.type
        return gson.fromJson(json, type)
    }
    fun saveProfilePhoto(path: String) {
        prefs.edit()
            .putString("profile_photo", path)
            .apply()
    }

    fun getProfilePhoto(): String? {
        return prefs.getString("profile_photo", null)
    }

}
