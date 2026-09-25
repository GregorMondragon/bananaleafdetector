package com.thesis.bananaleaf.data.repository

import android.content.Context
import com.thesis.bananaleaf.data.local.db.AppDatabase
import com.thesis.bananaleaf.data.local.db.ScanEntity
import com.thesis.bananaleaf.domain.repository.ScanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class ScanRepositoryImpl(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getDatabase(context)
) : ScanRepository {

    private val scanDao = database.scanDao()
    private val storageDir = File(context.filesDir, "saved_scans").apply { if (!exists()) mkdirs() }

    override fun getAllScans(): Flow<List<ScanEntity>> = scanDao.getAllScans()

    override suspend fun saveScan(
        sourceImage: File,
        diseaseNames: List<String>,
        confidences: List<Float>,
        healthyConfidence: Float
    ): ScanEntity = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        val destFile = File(storageDir, "scan_$timestamp.jpg")
        sourceImage.copyTo(destFile, overwrite = true)

        val entity = ScanEntity.create(
            imagePath = destFile.absolutePath,
            diseaseNames = diseaseNames,
            confidences = confidences,
            healthyConfidence = healthyConfidence,
            timestamp = timestamp
        )
        val id = scanDao.insertScan(entity)
        entity.copy(id = id)
    }

    override suspend fun deleteScan(scan: ScanEntity) = withContext(Dispatchers.IO) {
        // Delete image file from storage
        try {
            val file = File(scan.imagePath)
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            android.util.Log.w("ScanRepositoryImpl", "Could not delete image file: ${e.message}")
        }
        // Delete from database
        scanDao.deleteScan(scan)
    }
}
