package com.thesis.bananaleaf

import android.content.Context
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * On-device Random Forest classifier — generic tree-walking interpreter that
 * loads a forest exported by export_bin.py (see the notebook) from a .bin
 * file in assets/. This one class is reused for FOUR different models in
 * this app, each loaded from its own .bin file with its own class names:
 *
 *   RandomForestModel.loadFromAssets(ctx, "leaf_detector.bin", listOf("NotLeaf", "Leaf"))
 *   RandomForestModel.loadFromAssets(ctx, "cordana.bin", listOf("Absent", "Cordana"))
 *   RandomForestModel.loadFromAssets(ctx, "pestalotiopsis.bin", listOf("Absent", "Pestalotiopsis"))
 *   RandomForestModel.loadFromAssets(ctx, "sigatoka.bin", listOf("Absent", "Sigatoka"))
 *
 * Each is an independent binary yes/no classifier (scikit-learn
 * RandomForestClassifier with classes_ == [0, 1]), so classProbabilities["1"]
 * position (index 1, the second name passed in) is "probability this is
 * present" -- see MainActivity for how the four are combined into one result
 * (a leaf can have zero, one, two, or three diseases at once; that's decided
 * per-model here, not by a single forced argmax across 4 classes like the
 * old single-label version of this app).
 *
 * A data-driven tree-walking interpreter is used instead of generated
 * if/else Java code (e.g. m2cgen) because large forests can produce a huge
 * generated source file — direct code-gen for a 400-tree/~158k-node forest
 * exceeded the JVM's 64KB-per-method bytecode limit and failed to compile.
 * Loading the trees as data avoids that ceiling entirely regardless of
 * forest size; the tradeoff is a small amount of interpretation overhead per
 * prediction, irrelevant here (a handful of inferences per photo, not a hot
 * loop).
 */
class RandomForestModel private constructor(
    private val numFeatures: Int,
    private val numClasses: Int,
    private val trees: Array<TreeArrays>,
    private val classNames: List<String>
) {

    /** Flat arrays for one tree — index 0 is always that tree's root node. */
    private class TreeArrays(
        val feature: IntArray,
        val threshold: FloatArray,
        val left: IntArray,
        val right: IntArray,
        val probs: Array<FloatArray> // [nodeIndex][classIndex], meaningful at leaves
    )

    data class Prediction(val label: String, val confidence: Float, val classProbabilities: Map<String, Float>)

    companion object {
        fun loadFromAssets(context: Context, assetName: String, classNames: List<String>): RandomForestModel {
            val bytes = context.assets.open(assetName).use { it.readBytes() }
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

            val numTrees = buf.int
            val numFeatures = buf.int
            val numClasses = buf.int
            require(numClasses == classNames.size) {
                "$assetName has $numClasses classes but ${classNames.size} names were given"
            }

            val trees = Array(numTrees) {
                val numNodes = buf.int
                val feature = IntArray(numNodes)
                val threshold = FloatArray(numNodes)
                val left = IntArray(numNodes)
                val right = IntArray(numNodes)
                val probs = Array(numNodes) { FloatArray(numClasses) }

                for (i in 0 until numNodes) {
                    feature[i] = buf.int
                    threshold[i] = buf.float
                    left[i] = buf.int
                    right[i] = buf.int
                    for (c in 0 until numClasses) probs[i][c] = buf.float
                }
                TreeArrays(feature, threshold, left, right, probs)
            }

            return RandomForestModel(numFeatures, numClasses, trees, classNames)
        }
    }

    /** @param scaledFeatures already run through the matching scaler's transform()
     *  (the single FeatureScaler for all four models) */
    fun classify(scaledFeatures: DoubleArray): Prediction {
        require(scaledFeatures.size == numFeatures) {
            "Expected $numFeatures features, got ${scaledFeatures.size}"
        }

        val avgProbs = FloatArray(numClasses)
        for (tree in trees) {
            var node = 0
            while (tree.feature[node] != -2) { // -2 == leaf (sklearn TREE_UNDEFINED)
                node = if (scaledFeatures[tree.feature[node]] <= tree.threshold[node]) {
                    tree.left[node]
                } else {
                    tree.right[node]
                }
            }
            for (c in 0 until numClasses) avgProbs[c] += tree.probs[node][c]
        }
        for (c in 0 until numClasses) avgProbs[c] /= trees.size

        var bestIdx = 0
        for (c in 1 until numClasses) if (avgProbs[c] > avgProbs[bestIdx]) bestIdx = c

        val probMap = classNames.indices.associate { classNames[it] to avgProbs[it] }
        return Prediction(classNames[bestIdx], avgProbs[bestIdx], probMap)
    }

    /**
     * Convenience for binary models (classNames.size == 2): probability of
     * the SECOND name passed to loadFromAssets (index 1 -- the "yes/present"
     * class, since scikit-learn's classes_ for a 0/1 target is always [0, 1]
     * in that order).
     */
    fun positiveProbability(scaledFeatures: DoubleArray): Float {
        require(numClasses == 2) { "positiveProbability() is only meaningful for binary models" }
        return classify(scaledFeatures).classProbabilities.getValue(classNames[1])
    }
}
