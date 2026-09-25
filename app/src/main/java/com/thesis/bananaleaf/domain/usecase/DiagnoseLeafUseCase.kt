package com.thesis.bananaleaf.domain.usecase

import android.graphics.Bitmap
import android.os.SystemClock
import com.thesis.bananaleaf.DiseaseInfo
import com.thesis.bananaleaf.FeatureScaler
import com.thesis.bananaleaf.ImageProcessor
import com.thesis.bananaleaf.data.ml.ModelManager
import com.thesis.bananaleaf.domain.model.DiagnosisResult
import com.thesis.bananaleaf.domain.model.DiseaseFinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

class DiagnoseLeafUseCase(private val modelManager: ModelManager) {

    companion object {
        // --- Stage 1 Leaf Gate thresholds ---
        // Live reticle gate: strictly requires confident leaf signal before enabling shutter
        const val LEAF_PRESENT_LIVE_THRESHOLD = 0.70f
        const val FOLIAR_RATIO_LIVE_THRESHOLD = 0.16f
        const val BLOB_DOMINANCE_LIVE_THRESHOLD = 0.50f

        // Full capture execution gate: slightly wider tolerance so that leaves with large
        // necrotic lesions, yellow chlorosis, or leaf margins (which reduce green ratio
        // and fragment blob dominance) are not falsely rejected after live confirmation.
        const val LEAF_PRESENT_EXECUTE_THRESHOLD = 0.62f
        const val FOLIAR_RATIO_EXECUTE_THRESHOLD = 0.14f
        const val BLOB_DOMINANCE_EXECUTE_THRESHOLD = 0.42f

        const val GREEN_RATIO_MIN_THRESHOLD = 0.025f
        const val BLOB_RATIO_MIN_THRESHOLD = 0.18f

        const val EDGE_DENSITY_MAX_THRESHOLD = 0.25f
        const val HUE_MIN_THRESHOLD = 15.0
        const val HUE_MAX_THRESHOLD = 82.0

        // Saturation guards: banana leaves are vividly saturated.
        // Non-leaf surfaces (walls, fabric, plastic in indoor lighting)
        // typically have sat_mean < 32 and sat_std < 7 in OpenCV HSV 0–255.
        const val SAT_MEAN_MIN_THRESHOLD = 32.0
        const val SAT_STD_MIN_THRESHOLD = 7.0

        // Hue uniformity guard: a real banana leaf always has hue variation
        // from midrib, veins, and blade gradient (hue_std ≥ 3.5).
        const val HUE_STD_MIN_THRESHOLD = 3.5

        // Flash overexposure guard: torch in a dark room pushes val_mean > 200
        // on any close surface while true saturation stays low for non-vivid materials.
        const val FLASH_VAL_OVEREXPOSED = 200.0   // val_mean above this → likely flash-blown
        const val FLASH_SAT_FLOOR      = 58.0    // sat_mean must exceed this if val is high

        const val LOW_CONFIDENCE_THRESHOLD = 0.65f

        const val CORDANA_THRESHOLD = 0.38f
        const val SIGATOKA_THRESHOLD = 0.38f
        const val PESTALOTIOPSIS_THRESHOLD = 0.38f
    }

    suspend fun execute(bitmap: Bitmap): DiagnosisResult = withContext(Dispatchers.Default) {
        val startMs = SystemClock.elapsedRealtime()
        require(modelManager.isLoaded) { "ML Models must be loaded before running diagnosis." }
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            throw IllegalArgumentException("Invalid bitmap passed to DiagnoseLeafUseCase")
        }

        val bgrMat = Mat()
        Utils.bitmapToMat(bitmap, bgrMat)
        if (bgrMat.empty()) {
            bgrMat.release()
            throw IllegalStateException("Unable to convert bitmap to OpenCV Mat")
        }
        Imgproc.cvtColor(bgrMat, bgrMat, Imgproc.COLOR_RGBA2BGR)

        val segmentation = try {
            ImageProcessor.segmentLeaf(bgrMat)
        } finally {
            bgrMat.release()
        }

        segmentation.use { seg ->
            // Stage 1: Banana Leaf Gate (Multi-Barrier: Botanical Morphology + Random Forest Classifier)
            val foliarMetrics = ImageProcessor.computeFoliarMetrics(seg.hsv)
            val foliarRatio = foliarMetrics.foliarRatio
            val greenRatio = foliarMetrics.greenRatio

            val leafFeatures = ImageProcessor.leafPresenceFeatures(seg)
            val vegRatio = leafFeatures[0]
            val largestBlobRatio = leafFeatures[2]
            val blobDominance = largestBlobRatio / (vegRatio + 1e-6)
            val hueMean  = leafFeatures[5]
            val hueStd   = leafFeatures[6]  // hue_std — collapses near 0 under flash on uniform surfaces
            val satMean  = leafFeatures[7]  // sat_mean
            val valMean  = leafFeatures[8]  // val_mean — spikes >200 under torch/flash
            val edgeDensity = leafFeatures[9]

            // Derive sat_std directly from the segmentation (not in leafFeatures vector)
            val satStdResult = run {
                val satCh = org.opencv.core.Mat()
                org.opencv.core.Core.extractChannel(seg.hsv, satCh, 1)
                val sdVec = org.opencv.core.MatOfDouble()
                val mnVec = org.opencv.core.MatOfDouble()
                org.opencv.core.Core.meanStdDev(satCh, mnVec, sdVec)
                val sd = sdVec.toArray().firstOrNull() ?: 0.0
                satCh.release(); mnVec.release(); sdVec.release()
                sd
            }

            val leafScaled = FeatureScaler.transformLeaf(leafFeatures)
            val leafProb = modelManager.leafModel.positiveProbability(leafScaled)

            val failsFoliarFloor    = foliarRatio < FOLIAR_RATIO_EXECUTE_THRESHOLD
            val failsGreenFloor     = greenRatio < GREEN_RATIO_MIN_THRESHOLD
            val failsBlobSize       = largestBlobRatio < BLOB_RATIO_MIN_THRESHOLD
            val failsBlobDominance  = blobDominance < BLOB_DOMINANCE_EXECUTE_THRESHOLD
            val failsHueWindow      = hueMean < HUE_MIN_THRESHOLD || hueMean > HUE_MAX_THRESHOLD
            val failsEdgeDensity    = edgeDensity > EDGE_DENSITY_MAX_THRESHOLD
            // Saturation guards: reject washed-out / uniform non-leaf surfaces
            val failsSatMean        = satMean < SAT_MEAN_MIN_THRESHOLD
            val failsSatStd         = satStdResult < SAT_STD_MIN_THRESHOLD
            val failsHueStd         = hueStd < HUE_STD_MIN_THRESHOLD
            val failsFlashOverexposure = valMean > FLASH_VAL_OVEREXPOSED && satMean < FLASH_SAT_FLOOR
            val failsMlGate         = leafProb < LEAF_PRESENT_EXECUTE_THRESHOLD

            val isNotBananaLeaf = failsFoliarFloor || failsGreenFloor || failsBlobSize ||
                failsBlobDominance || failsHueWindow || failsEdgeDensity ||
                failsSatMean || failsSatStd || failsHueStd || failsFlashOverexposure || failsMlGate

            if (isNotBananaLeaf) {
                val elapsed = SystemClock.elapsedRealtime() - startMs
                val reportedConfidence = when {
                    failsFoliarFloor || failsGreenFloor -> (foliarRatio / FOLIAR_RATIO_EXECUTE_THRESHOLD) * 0.15f
                    failsBlobSize || failsBlobDominance || failsHueWindow || failsEdgeDensity -> 0.15f
                    else -> leafProb
                }
                return@withContext DiagnosisResult(
                    isLeafPresent = false,
                    leafConfidence = reportedConfidence,
                    vegetationRatio = foliarRatio,
                    detectedDiseases = emptyList(),
                    healthyConfidence = 0f,
                    inferenceTimeMs = elapsed
                )
            }

            // Stage 2: Disease Heads (reusing existing segmentation!)
            val diseaseFeatures = ImageProcessor.diseaseFeatures(seg)
            val diseaseScaled = FeatureScaler.transformDisease(diseaseFeatures)

            val cordanaProb = modelManager.cordanaModel.positiveProbability(diseaseScaled)
            val pestalotiopsisProb = modelManager.pestalotiopsisModel.positiveProbability(diseaseScaled)
            val sigatokaProb = modelManager.sigatokaModel.positiveProbability(diseaseScaled)

            val diseaseCandidates = listOf(
                DiseaseFinding("Cordana", DiseaseInfo.get("Cordana").displayName, cordanaProb, cordanaProb >= CORDANA_THRESHOLD),
                DiseaseFinding("Pestalotiopsis", DiseaseInfo.get("Pestalotiopsis").displayName, pestalotiopsisProb, pestalotiopsisProb >= PESTALOTIOPSIS_THRESHOLD),
                DiseaseFinding("Sigatoka", DiseaseInfo.get("Sigatoka").displayName, sigatokaProb, sigatokaProb >= SIGATOKA_THRESHOLD)
            )

            val detectedDiseases = diseaseCandidates
                .filter { it.isThresholdMet }
                .sortedByDescending { it.confidence }

            val maxProb = maxOf(cordanaProb, pestalotiopsisProb, sigatokaProb)
            val healthyConfidence = (1f - maxProb).coerceIn(0f, 1f)
            val elapsed = SystemClock.elapsedRealtime() - startMs

            return@withContext DiagnosisResult(
                isLeafPresent = true,
                leafConfidence = leafProb,
                vegetationRatio = foliarRatio,
                detectedDiseases = detectedDiseases,
                healthyConfidence = healthyConfidence,
                inferenceTimeMs = elapsed
            )
        }
    }

    /**
     * Fast leaf-presence evaluation for live camera frame throttling.
     * Evaluates botanical banana leaf morphology and RF model to strictly reject other plants and non-leaf surfaces.
     */
    suspend fun evaluateLiveFrame(bitmap: Bitmap): Pair<Boolean, Float> = withContext(Dispatchers.Default) {
        if (!modelManager.isLoaded) return@withContext Pair(false, 0f)
        val bgrMat = Mat()
        Utils.bitmapToMat(bitmap, bgrMat)
        if (bgrMat.empty()) {
            bgrMat.release()
            return@withContext Pair(false, 0f)
        }
        Imgproc.cvtColor(bgrMat, bgrMat, Imgproc.COLOR_RGBA2BGR)

        val segmentation = try {
            ImageProcessor.segmentLeaf(bgrMat)
        } finally {
            bgrMat.release()
        }

        segmentation.use { seg ->
            // 1. Biological check: Does the target actually have banana leaf foliage color and chlorophyll?
            val foliarMetrics = ImageProcessor.computeFoliarMetrics(seg.hsv)
            if (foliarMetrics.foliarRatio < FOLIAR_RATIO_LIVE_THRESHOLD || foliarMetrics.greenRatio < GREEN_RATIO_MIN_THRESHOLD) {
                val notLeafConfidence = (foliarMetrics.foliarRatio / FOLIAR_RATIO_LIVE_THRESHOLD) * 0.15f
                return@withContext Pair(false, notLeafConfidence)
            }

            // 2. Botanical & ML checks: Does it match the single broad lamina, parallel venation smoothness, and RF geometry of a banana leaf?
            val leafFeatures = ImageProcessor.leafPresenceFeatures(seg)
            val vegRatio = leafFeatures[0]
            val largestBlobRatio = leafFeatures[2]
            val blobDominance = largestBlobRatio / (vegRatio + 1e-6)
            val hueMean  = leafFeatures[5]
            val hueStd   = leafFeatures[6]  // hue_std — collapses near 0 under flash on uniform surfaces
            val satMean  = leafFeatures[7]  // sat_mean
            val valMean  = leafFeatures[8]  // val_mean — spikes >200 under torch/flash
            val edgeDensity = leafFeatures[9]

            // Saturation std from HSV directly
            val satStdLive = run {
                val satCh = org.opencv.core.Mat()
                org.opencv.core.Core.extractChannel(seg.hsv, satCh, 1)
                val sdVec = org.opencv.core.MatOfDouble()
                val mnVec = org.opencv.core.MatOfDouble()
                org.opencv.core.Core.meanStdDev(satCh, mnVec, sdVec)
                val sd = sdVec.toArray().firstOrNull() ?: 0.0
                satCh.release(); mnVec.release(); sdVec.release()
                sd
            }

            val failsBlobSize          = largestBlobRatio < BLOB_RATIO_MIN_THRESHOLD
            val failsBlobDominance     = blobDominance < BLOB_DOMINANCE_LIVE_THRESHOLD
            val failsHueWindow         = hueMean < HUE_MIN_THRESHOLD || hueMean > HUE_MAX_THRESHOLD
            val failsEdgeDensity       = edgeDensity > EDGE_DENSITY_MAX_THRESHOLD
            // Saturation guards to filter out walls, plastic, fabric
            val failsSatMean           = satMean < SAT_MEAN_MIN_THRESHOLD
            val failsSatStd            = satStdLive < SAT_STD_MIN_THRESHOLD
            // Flash/torch guard: uniform surfaces lit by flash have collapsed
            // hue variation and blown-out brightness (val_mean > 200, sat_mean < 60)
            val failsHueStd            = hueStd < HUE_STD_MIN_THRESHOLD
            val failsFlashOverexposure = valMean > FLASH_VAL_OVEREXPOSED && satMean < FLASH_SAT_FLOOR

            if (failsBlobSize || failsBlobDominance || failsHueWindow || failsEdgeDensity ||
                failsSatMean || failsSatStd || failsHueStd || failsFlashOverexposure) {
                // Not a banana leaf or flash overexposed non-leaf: keep reticle RED
                return@withContext Pair(false, 0.12f)
            }

            val leafScaled = FeatureScaler.transformLeaf(leafFeatures)
            val leafProb = modelManager.leafModel.positiveProbability(leafScaled)

            val isBananaLeaf = leafProb >= LEAF_PRESENT_LIVE_THRESHOLD
            val confidence = if (isBananaLeaf) leafProb else (leafProb * 0.35f)
            Pair(isBananaLeaf, confidence)
        }
    }
}
