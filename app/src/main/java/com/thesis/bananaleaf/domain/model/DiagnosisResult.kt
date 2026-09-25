package com.thesis.bananaleaf.domain.model

data class DiseaseFinding(
    val diseaseKey: String,
    val displayName: String,
    val confidence: Float,
    val isThresholdMet: Boolean
)

data class DiagnosisResult(
    val isLeafPresent: Boolean,
    val leafConfidence: Float,
    val vegetationRatio: Float,
    val detectedDiseases: List<DiseaseFinding>,
    val healthyConfidence: Float,
    val inferenceTimeMs: Long
) {
    val isHealthy: Boolean get() = isLeafPresent && detectedDiseases.isEmpty()

    fun summaryString(): String {
        if (!isLeafPresent) return "No Banana Leaf Detected"
        if (isHealthy) return "Healthy Leaf"
        return detectedDiseases.joinToString(" · ") { "${it.displayName} ${(it.confidence * 100f).toInt()}%" }
    }
}
