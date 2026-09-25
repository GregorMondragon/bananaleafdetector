package com.thesis.bananaleaf.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "saved_scans")
data class ScanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val imagePath: String,
    val isHealthy: Boolean,
    val diseaseNamesJson: String,
    val confidencesJson: String,
    val healthyConfidence: Float,
    val timestamp: Long
) {
    fun getDiseaseNames(): List<String> {
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            Gson().fromJson(diseaseNamesJson, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getConfidences(): List<Float> {
        return try {
            val type = object : TypeToken<List<Float>>() {}.type
            Gson().fromJson(confidencesJson, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        fun create(
            imagePath: String,
            diseaseNames: List<String>,
            confidences: List<Float>,
            healthyConfidence: Float,
            timestamp: Long = System.currentTimeMillis()
        ): ScanEntity {
            val gson = Gson()
            return ScanEntity(
                imagePath = imagePath,
                isHealthy = diseaseNames.isEmpty(),
                diseaseNamesJson = gson.toJson(diseaseNames),
                confidencesJson = gson.toJson(confidences),
                healthyConfidence = healthyConfidence,
                timestamp = timestamp
            )
        }
    }
}
