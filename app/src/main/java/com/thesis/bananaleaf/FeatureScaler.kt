package com.thesis.bananaleaf

/**
 * StandardScalers fitted on the newly balanced dataset (4,498 images).
 * Generated automatically by train_pipeline.py.
 */
object FeatureScaler {
    private val LEAF_MEAN = doubleArrayOf(
        0.5532964373054691,
        0.318679190171241,
        0.49287157625611383,
        1.1389019120944905,
        0.649675390651391,
        42.101072904624274,
        15.196436839978709,
        142.2242005835927,
        127.5480337316585,
        0.08422459982214317
    )

    private val LEAF_SCALE = doubleArrayOf(
        0.17352257262061874,
        0.1648986286663059,
        0.2053440349740247,
        0.47880534198492647,
        0.14531681079968875,
        12.40758275622859,
        10.498126528204516,
        43.93769031540765,
        27.359864437091975,
        0.047628279339450685
    )

    private val DISEASE_MEAN = doubleArrayOf(
        39.4198631346153,
        9.055081686473663,
        0.8824996761792135,
        178.61550080223415,
        29.58097627262865,
        -11.81861324365772,
        117.94769675195842,
        28.971584044381316,
        2.243813209551584,
        3.864907174454621,
        1.1468852687736344,
        0.614905085983414,
        0.10484183504710767,
        0.9768585057399191,
        0.012103977365014059
    )

    private val DISEASE_SCALE = doubleArrayOf(
        8.880615061439975,
        5.176088451818415,
        11.612153608015264,
        38.70752506872262,
        8.172265604313667,
        22.050489671607153,
        39.51332423862865,
        8.68884729662103,
        22.81807573791303,
        2.013901002731546,
        0.3519258829522967,
        0.08064231938629345,
        0.031667642471570015,
        0.011339867653161466,
        0.008254209712880956
    )

    fun transformLeaf(features: DoubleArray): DoubleArray {
        require(features.size == LEAF_MEAN.size) {
            "Leaf feature size mismatch: expected ${LEAF_MEAN.size}, got ${features.size}"
        }
        return DoubleArray(features.size) { i ->
            (features[i] - LEAF_MEAN[i]) / LEAF_SCALE[i]
        }
    }

    fun transformDisease(features: DoubleArray): DoubleArray {
        require(features.size == DISEASE_MEAN.size) {
            "Disease feature size mismatch: expected ${DISEASE_MEAN.size}, got ${features.size}"
        }
        return DoubleArray(features.size) { i ->
            (features[i] - DISEASE_MEAN[i]) / DISEASE_SCALE[i]
        }
    }
}
