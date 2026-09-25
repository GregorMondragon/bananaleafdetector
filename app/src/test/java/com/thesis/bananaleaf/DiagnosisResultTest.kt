package com.thesis.bananaleaf

import com.thesis.bananaleaf.domain.model.DiagnosisResult
import com.thesis.bananaleaf.domain.model.DiseaseFinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosisResultTest {

    @Test
    fun testHealthyDiagnosis() {
        val result = DiagnosisResult(
            isLeafPresent = true,
            leafConfidence = 0.98f,
            vegetationRatio = 0.45f,
            detectedDiseases = emptyList(),
            healthyConfidence = 0.92f,
            inferenceTimeMs = 45L
        )
        assertTrue(result.isHealthy)
        assertEquals("Healthy Leaf", result.summaryString())
    }

    @Test
    fun testSingleDiseaseDiagnosis() {
        val diseases = listOf(
            DiseaseFinding("Sigatoka", "Black Sigatoka", 0.88f, true)
        )
        val result = DiagnosisResult(
            isLeafPresent = true,
            leafConfidence = 0.99f,
            vegetationRatio = 0.55f,
            detectedDiseases = diseases,
            healthyConfidence = 0.12f,
            inferenceTimeMs = 50L
        )
        assertFalse(result.isHealthy)
        assertTrue(result.summaryString().contains("Black Sigatoka 88%"))
    }

    @Test
    fun testNoLeafDiagnosis() {
        val result = DiagnosisResult(
            isLeafPresent = false,
            leafConfidence = 0.20f,
            vegetationRatio = 0.02f,
            detectedDiseases = emptyList(),
            healthyConfidence = 0.0f,
            inferenceTimeMs = 15L
        )
        assertFalse(result.isLeafPresent)
        assertFalse(result.isHealthy)
        assertEquals("No Banana Leaf Detected", result.summaryString())
    }
}
