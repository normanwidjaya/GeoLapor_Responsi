package com.example.geolapor.data.model

data class Report(
    val id: String,
    val title: String,
    val category: String,
    val subCategory: String,
    val description: String,
    val lat: Double,
    val lon: Double,
    val date: String,
    val reporterName: String? = null,
    val reporterPhone: String? = null,
    val reporterAddress: String? = null,
    val photoPath: String? = null
)
