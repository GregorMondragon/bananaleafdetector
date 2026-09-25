package com.thesis.bananaleaf.domain.usecase

import com.thesis.bananaleaf.data.local.db.ScanEntity
import com.thesis.bananaleaf.domain.model.DiagnosisResult
import com.thesis.bananaleaf.domain.repository.ScanRepository
import kotlinx.coroutines.flow.Flow
import java.io.File

class GetGardenScansUseCase(private val repository: ScanRepository) {
    operator fun invoke(): Flow<List<ScanEntity>> = repository.getAllScans()
}

class SaveScanUseCase(private val repository: ScanRepository) {
    suspend operator fun invoke(
        photoFile: File,
        diagnosis: DiagnosisResult
    ): ScanEntity {
        val diseaseNames = diagnosis.detectedDiseases.map { it.diseaseKey }
        val confidences = diagnosis.detectedDiseases.map { it.confidence }
        return repository.saveScan(
            sourceImage = photoFile,
            diseaseNames = diseaseNames,
            confidences = confidences,
            healthyConfidence = diagnosis.healthyConfidence
        )
    }
}

class DeleteScanUseCase(private val repository: ScanRepository) {
    suspend operator fun invoke(scan: ScanEntity) = repository.deleteScan(scan)
}
