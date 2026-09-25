package com.thesis.bananaleaf.domain.repository

import com.thesis.bananaleaf.data.local.db.ScanEntity
import kotlinx.coroutines.flow.Flow
import java.io.File

interface ScanRepository {
    fun getAllScans(): Flow<List<ScanEntity>>
    suspend fun saveScan(
        sourceImage: File,
        diseaseNames: List<String>,
        confidences: List<Float>,
        healthyConfidence: Float
    ): ScanEntity
    suspend fun deleteScan(scan: ScanEntity)
}
