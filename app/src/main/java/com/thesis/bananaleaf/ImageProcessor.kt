package com.thesis.bananaleaf

import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * Exact Kotlin port of the CURRENT BananaLeaf.ipynb feature extraction.
 *
 * Stage 1 (leaf gate): 10 features
 * Stage 2 (disease heads): 15 features
 *
 * The training notebook uses a 200x200 resized, Gaussian-blurred image.
 * Leaf segmentation is K-means (2 clusters over HSV color), with Otsu's
 * thresholding on the Saturation channel deciding which cluster is the
 * leaf region (falls back to the fixed HSV range [15,25,25]..[95,255,255]
 * if neither cluster is clearly more saturated than the Otsu cutoff),
 * followed by 5x5 close then open morphology.
 */
object ImageProcessor {
    private const val IMG_SIZE = 200

    val LEAF_FEATURE_NAMES = listOf(
        "veg_ratio", "circularity", "largest_blob_ratio", "aspect", "extent",
        "hue_mean", "hue_std", "sat_mean", "val_mean", "edge_density"
    )

    val DISEASE_FEATURE_NAMES = listOf(
        "H_mean", "H_std", "H_skew",
        "S_mean", "S_std", "S_skew",
        "V_mean", "V_std", "V_skew",
        "glcm_contrast", "glcm_dissimilarity", "glcm_homogeneity",
        "glcm_energy", "glcm_correlation", "glcm_ASM"
    )

    data class SegmentationResult(
        val processedBgr: Mat,
        val hsv: Mat,
        val mask: Mat
    ) : AutoCloseable {
        override fun close() {
            if (!processedBgr.empty()) processedBgr.release()
            if (!hsv.empty()) hsv.release()
            if (!mask.empty()) mask.release()
        }
    }

    private fun preprocess(srcBgr: Mat): Mat {
        val resized = Mat()
        Imgproc.resize(
            srcBgr, resized,
            Size(IMG_SIZE.toDouble(), IMG_SIZE.toDouble()),
            0.0, 0.0, Imgproc.INTER_AREA
        )
        val blurred = Mat()
        Imgproc.GaussianBlur(resized, blurred, Size(5.0, 5.0), 0.0)
        resized.release()
        return blurred
    }

    data class FoliarMetrics(
        val foliarRatio: Float,
        val greenRatio: Float
    )

    /**
     * Computes broad foliar metrics covering:
     * - Chlorophyll green & chlorotic yellow (Hue 16..95, Sat >= 20, Val >= 20)
     * - Brown necrotic lesions / spots (Hue 8..22, Sat >= 28, Val 20..220)
     * - Pure green chlorophyll presence (Hue 24..95, Sat >= 20, Val >= 20)
     */
    fun computeFoliarMetrics(hsv: Mat): FoliarMetrics {
        val total = hsv.rows() * hsv.cols()
        if (total <= 0) return FoliarMetrics(0f, 0f)

        val mGreenYellow = Mat()
        Core.inRange(hsv, Scalar(16.0, 20.0, 20.0), Scalar(95.0, 255.0, 255.0), mGreenYellow)

        val mBrown = Mat()
        Core.inRange(hsv, Scalar(8.0, 28.0, 20.0), Scalar(22.0, 255.0, 220.0), mBrown)

        val foliarMask = Mat()
        Core.bitwise_or(mGreenYellow, mBrown, foliarMask)
        val foliarCount = Core.countNonZero(foliarMask)

        val mPureGreen = Mat()
        Core.inRange(hsv, Scalar(24.0, 20.0, 20.0), Scalar(95.0, 255.0, 255.0), mPureGreen)
        val greenCount = Core.countNonZero(mPureGreen)

        mGreenYellow.release()
        mBrown.release()
        foliarMask.release()
        mPureGreen.release()

        val fRatio = (foliarCount.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        val gRatio = (greenCount.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        return FoliarMetrics(fRatio, gRatio)
    }

    /**
     * Backward-compatible alias returning total foliar ratio (green + yellow chlorosis + brown necrosis).
     */
    fun computeTrueVegetationRatio(hsv: Mat): Float {
        return computeFoliarMetrics(hsv).foliarRatio
    }

    fun segmentLeaf(srcBgr: Mat): SegmentationResult {
        val img = preprocess(srcBgr)
        val hsv = Mat()
        Imgproc.cvtColor(img, hsv, Imgproc.COLOR_BGR2HSV)

        // --- K-means: cluster pixels into 2 groups by HSV color ---
        val hsvFloat = Mat()
        hsv.convertTo(hsvFloat, CvType.CV_32F)
        val totalPixels = hsv.rows() * hsv.cols()
        val samples = hsvFloat.reshape(1, totalPixels)
        val labels = Mat()
        val centers = Mat()
        val criteria = TermCriteria(TermCriteria.EPS + TermCriteria.MAX_ITER, 20, 0.5)
        Core.kmeans(samples, 2, labels, criteria, 5, Core.KMEANS_PP_CENTERS, centers)
        val labelsImg = labels.reshape(1, hsv.rows())

        // --- Otsu's thresholding on Saturation: finds the cutoff that best
        // separates vivid (leaf/lesion) pixels from dull (wall/floor/background)
        // pixels. Used here to pick which K-means cluster is "leaf" rather than
        // a fixed hand-picked HSV range. Mirrors segment_leaf() in the training
        // notebook exactly -- must stay in sync with it. ---
        val sat = Mat()
        Core.extractChannel(hsv, sat, 1)
        val otsuDump = Mat()
        val otsuThresh = Imgproc.threshold(
            sat, otsuDump, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU
        )
        otsuDump.release()

        val cluster0Mask = Mat()
        val cluster1Mask = Mat()
        Core.compare(labelsImg, Scalar(0.0), cluster0Mask, Core.CMP_EQ)
        Core.compare(labelsImg, Scalar(1.0), cluster1Mask, Core.CMP_EQ)
        val cluster0Sat =
            if (Core.countNonZero(cluster0Mask) > 0) Core.mean(sat, cluster0Mask).`val`[0] else 0.0
        val cluster1Sat =
            if (Core.countNonZero(cluster1Mask) > 0) Core.mean(sat, cluster1Mask).`val`[0] else 0.0

        val mask = Mat()
        if (max(cluster0Sat, cluster1Sat) < otsuThresh) {
            // Neither cluster is clearly "vivid" -- fall back to the fixed HSV
            // range rather than risk an empty/garbage mask on a washed-out photo.
            Core.inRange(
                hsv,
                Scalar(15.0, 25.0, 25.0),
                Scalar(95.0, 255.0, 255.0),
                mask
            )
        } else {
            if (cluster0Sat >= cluster1Sat) cluster0Mask.copyTo(mask) else cluster1Mask.copyTo(mask)
        }

        cluster0Mask.release(); cluster1Mask.release()
        hsvFloat.release(); samples.release(); labels.release(); centers.release(); sat.release()

        val kernel = Mat.ones(5, 5, CvType.CV_8U)
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel)
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel)
        kernel.release()

        return SegmentationResult(img, hsv, mask)
    }

    private fun channelStats(
        hsv: Mat,
        channelIndex: Int,
        mask: Mat?
    ): DoubleArray {
        val channel = Mat()
        Core.extractChannel(hsv, channel, channelIndex)

        val values = ArrayList<Double>()
        val bytes = ByteArray(IMG_SIZE * IMG_SIZE)
        channel.get(0, 0, bytes)

        val maskBytes = if (mask != null) ByteArray(IMG_SIZE * IMG_SIZE) else null
        mask?.get(0, 0, maskBytes)

        for (i in bytes.indices) {
            if (maskBytes == null || (maskBytes[i].toInt() and 0xFF) > 0) {
                values.add((bytes[i].toInt() and 0xFF).toDouble())
            }
        }

        // Matches the notebook's color_moments(): if fewer than 20 masked
        // pixels are found, fall back to computing stats over the WHOLE
        // image instead of just the (too-small/noisy) masked region.
        if (values.size < 20) {
            values.clear()
            values.addAll(bytes.map { (it.toInt() and 0xFF).toDouble() })
        }

        var sum = 0.0
        for (v in values) sum += v
        val mean = sum / values.size

        var sq = 0.0
        var cube = 0.0
        for (v in values) {
            val d = v - mean
            sq += d * d
            cube += d * d * d
        }

        val std = sqrt(sq / values.size)
        val thirdMoment = cube / values.size
        val skew = sign(thirdMoment) * abs(thirdMoment).pow(1.0 / 3.0)

        channel.release()
        return doubleArrayOf(mean, std, skew)
    }

    /** Stage 1: exact 10-feature vector calculated directly from an existing segmentation. */
    fun leafPresenceFeatures(seg: SegmentationResult): DoubleArray {
        val mask = seg.mask
        val hsv = seg.hsv

        val total = IMG_SIZE * IMG_SIZE
        val vegRatio = Core.countNonZero(mask).toDouble() / total

        val contours = ArrayList<MatOfPoint>()
        Imgproc.findContours(
            mask, contours, Mat(),
            Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE
        )

        var circularity = 0.0
        var largestBlobRatio = 0.0
        var aspect = 0.0
        var extent = 0.0

        if (contours.isNotEmpty()) {
            val c = contours.maxByOrNull { Imgproc.contourArea(it) }
            if (c != null) {
                val area = Imgproc.contourArea(c)
                val contour2f = MatOfPoint2f(*c.toArray())
                val perim = Imgproc.arcLength(contour2f, true) + 1e-6
                contour2f.release()
                circularity = 4.0 * Math.PI * area / (perim * perim)
                largestBlobRatio = area / total

                val rect = Imgproc.boundingRect(c)
                aspect = rect.width.toDouble() / (rect.height + 1e-6)
                extent = area / (rect.width * rect.height + 1e-6)
            }
        }

        contours.forEach { it.release() }

        val hue = Mat(); Core.extractChannel(hsv, hue, 0)
        val sat = Mat(); Core.extractChannel(hsv, sat, 1)
        val value = Mat(); Core.extractChannel(hsv, value, 2)

        fun meanStd(m: Mat): DoubleArray {
            val mean = MatOfDouble()
            val std = MatOfDouble()
            Core.meanStdDev(m, mean, std)
            val res = doubleArrayOf(mean.toArray()[0], std.toArray()[0])
            mean.release()
            std.release()
            return res
        }

        val hms = meanStd(hue)
        val sms = meanStd(sat)
        val vms = meanStd(value)

        val gray = Mat()
        Imgproc.cvtColor(seg.processedBgr, gray, Imgproc.COLOR_BGR2GRAY)
        val edges = Mat()
        Imgproc.Canny(gray, edges, 50.0, 150.0)
        val edgeDensity = Core.countNonZero(edges).toDouble() / total

        hue.release(); sat.release(); value.release()
        gray.release(); edges.release()

        return doubleArrayOf(
            vegRatio, circularity, largestBlobRatio, aspect, extent,
            hms[0], hms[1], sms[0], vms[0], edgeDensity
        )
    }

    /** Stage 1 convenience overload for direct Mat input. */
    fun leafPresenceFeatures(srcBgr: Mat): DoubleArray {
        val seg = segmentLeaf(srcBgr)
        return try {
            leafPresenceFeatures(seg)
        } finally {
            seg.close()
        }
    }

    /** Stage 2: exact 15-feature disease vector calculated directly from an existing segmentation. */
    fun diseaseFeatures(seg: SegmentationResult): DoubleArray {
        val hsv = seg.hsv
        val mask = seg.mask

        val color = ArrayList<Double>(9)
        for (ch in 0..2) color.addAll(channelStats(hsv, ch, mask).toList())

        val gray = Mat()
        Imgproc.cvtColor(seg.processedBgr, gray, Imgproc.COLOR_BGR2GRAY)
        val glcm = glcmFeatures(gray, mask)

        gray.release()

        return (color + glcm).toDoubleArray()
    }

    /** Stage 2 convenience overload for direct Mat input. */
    fun diseaseFeatures(srcBgr: Mat): DoubleArray {
        val seg = segmentLeaf(srcBgr)
        return try {
            diseaseFeatures(seg)
        } finally {
            seg.close()
        }
    }


    /**
     * Port of skimage.feature.graycomatrix / graycoprops used in the notebook.
     * The notebook quantizes gray to 64 levels using (gray / 4).astype(uint8),
     * then averages each property over 4 angles with distance=1.
     */
    private fun glcmFeatures(gray: Mat, mask: Mat): List<Double> {
        val m = mask.clone()
        val nonZero = Core.countNonZero(m)

        var roi: Mat = gray

        if (nonZero >= 20) {
            val points = Mat()
            Core.findNonZero(m, points)
            val rect = Imgproc.boundingRect(points)
            points.release()
            roi = gray.submat(rect)
        }

        val w = roi.cols()
        val h = roi.rows()
        val bytes = ByteArray(w * h)
        roi.get(0, 0, bytes)

        val levels = 64
        val quant = IntArray(bytes.size) { (bytes[it].toInt() and 0xFF) / 4 }

        val offsets = arrayOf(
            intArrayOf(0, 1),
            intArrayOf(-1, 1),
            intArrayOf(-1, 0),
            intArrayOf(-1, -1)
        )

        val out = DoubleArray(6)

        for (off in offsets) {
            val glcm = Array(levels) { DoubleArray(levels) }
            var pairCount = 0

            val dr = off[0]
            val dc = off[1]

            for (r in 0 until h) {
                for (c in 0 until w) {
                    val r2 = r + dr
                    val c2 = c + dc
                    if (r2 !in 0 until h || c2 !in 0 until w) continue

                    val g1 = quant[r * w + c]
                    val g2 = quant[r2 * w + c2]
                    glcm[g1][g2] += 1.0
                    glcm[g2][g1] += 1.0
                    pairCount += 2
                }
            }

            if (pairCount == 0) continue

            var contrast = 0.0
            var dissimilarity = 0.0
            var homogeneity = 0.0
            var asm = 0.0
            var meanI = 0.0
            var meanJ = 0.0

            for (i in 0 until levels) {
                for (j in 0 until levels) {
                    val p = glcm[i][j] / pairCount
                    meanI += i * p
                    meanJ += j * p
                }
            }

            var varI = 0.0
            var varJ = 0.0
            for (i in 0 until levels) {
                for (j in 0 until levels) {
                    val p = glcm[i][j] / pairCount
                    varI += (i - meanI).pow(2) * p
                    varJ += (j - meanJ).pow(2) * p
                }
            }

            val stdI = sqrt(varI)
            val stdJ = sqrt(varJ)
            var correlation = 0.0

            for (i in 0 until levels) {
                for (j in 0 until levels) {
                    val p = glcm[i][j] / pairCount
                    val d = abs(i - j).toDouble()

                    contrast += p * d * d
                    dissimilarity += p * d
                    homogeneity += p / (1.0 + d * d)
                    asm += p * p

                    if (stdI > 1e-12 && stdJ > 1e-12) {
                        correlation += p * (i - meanI) * (j - meanJ) / (stdI * stdJ)
                    } else {
                        correlation += p
                    }
                }
            }

            out[0] += contrast
            out[1] += dissimilarity
            out[2] += homogeneity
            out[3] += sqrt(asm) // skimage graycoprops(..., "energy")
            out[4] += correlation
            out[5] += asm
        }

        if (roi !== gray) roi.release()
        m.release()

        return out.map { it / offsets.size }
    }
}
