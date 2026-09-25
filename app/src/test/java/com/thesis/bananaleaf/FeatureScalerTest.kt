package com.thesis.bananaleaf

import org.junit.Assert.assertEquals
import org.junit.Test

class FeatureScalerTest {

    @Test
    fun testLeafFeatureScalerDimensions() {
        val input = DoubleArray(10) { 1.0 }
        val scaled = FeatureScaler.transformLeaf(input)
        assertEquals(10, scaled.size)
    }

    @Test
    fun testDiseaseFeatureScalerDimensions() {
        val input = DoubleArray(15) { 1.0 }
        val scaled = FeatureScaler.transformDisease(input)
        assertEquals(15, scaled.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testLeafFeatureScalerInvalidSize() {
        val input = DoubleArray(5) { 1.0 }
        FeatureScaler.transformLeaf(input)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testDiseaseFeatureScalerInvalidSize() {
        val input = DoubleArray(12) { 1.0 }
        FeatureScaler.transformDisease(input)
    }
}
