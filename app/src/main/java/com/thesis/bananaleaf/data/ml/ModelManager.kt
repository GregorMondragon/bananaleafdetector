package com.thesis.bananaleaf.data.ml

import android.content.Context
import com.thesis.bananaleaf.RandomForestModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ModelManager(private val context: Context) {

    @Volatile
    var isLoaded: Boolean = false
        private set

    lateinit var leafModel: RandomForestModel
        private set
    lateinit var cordanaModel: RandomForestModel
        private set
    lateinit var pestalotiopsisModel: RandomForestModel
        private set
    lateinit var sigatokaModel: RandomForestModel
        private set

    suspend fun loadModels(): Boolean = withContext(Dispatchers.IO) {
        if (isLoaded) return@withContext true
        try {
            leafModel = RandomForestModel.loadFromAssets(context, "leaf_detector.bin", listOf("NotLeaf", "Leaf"))
            cordanaModel = RandomForestModel.loadFromAssets(context, "cordana.bin", listOf("Absent", "Cordana"))
            pestalotiopsisModel = RandomForestModel.loadFromAssets(context, "pestalotiopsis.bin", listOf("Absent", "Pestalotiopsis"))
            sigatokaModel = RandomForestModel.loadFromAssets(context, "sigatoka.bin", listOf("Absent", "Sigatoka"))
            isLoaded = true
            true
        } catch (e: Exception) {
            android.util.Log.e("ModelManager", "Failed to load binary models from assets: ${e.message}", e)
            false
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: ModelManager? = null

        fun getInstance(context: Context): ModelManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ModelManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
