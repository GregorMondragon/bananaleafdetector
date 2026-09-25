package com.thesis.bananaleaf.presentation.main

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thesis.bananaleaf.data.local.db.ScanEntity
import com.thesis.bananaleaf.data.ml.ModelManager
import com.thesis.bananaleaf.data.repository.ScanRepositoryImpl
import com.thesis.bananaleaf.domain.model.DiagnosisResult
import com.thesis.bananaleaf.domain.usecase.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

sealed class ScanUiState {
    object Initial : ScanUiState()
    object LoadingModels : ScanUiState()
    object Ready : ScanUiState()
    object Processing : ScanUiState()
    data class ScanSuccess(
        val diagnosis: DiagnosisResult,
        val tempPhotoFile: File
    ) : ScanUiState()
    data class NoLeafDetected(
        val leafConfidence: Float,
        val vegetationRatio: Float,
        val inferenceTimeMs: Long
    ) : ScanUiState()
    data class Error(val message: String) : ScanUiState()
}

data class LiveFrameState(
    val isLeaf: Boolean = false,
    val confidence: Float = 0f,
    val inferenceTimeMs: Long = 0L
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val modelManager = ModelManager.getInstance(application)
    private val scanRepository = ScanRepositoryImpl(application)

    private val diagnoseLeafUseCase = DiagnoseLeafUseCase(modelManager)
    private val saveScanUseCase = SaveScanUseCase(scanRepository)
    private val deleteScanUseCase = DeleteScanUseCase(scanRepository)
    private val getGardenScansUseCase = GetGardenScansUseCase(scanRepository)

    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Initial)
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private val _liveFrameState = MutableStateFlow(LiveFrameState())
    val liveFrameState: StateFlow<LiveFrameState> = _liveFrameState.asStateFlow()

    val gardenScans: StateFlow<List<ScanEntity>> = getGardenScansUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val isLiveProcessing = AtomicBoolean(false)

    init {
        loadModels()
    }

    fun loadModels() {
        viewModelScope.launch {
            _uiState.value = ScanUiState.LoadingModels
            val loaded = modelManager.loadModels()
            _uiState.value = if (loaded) ScanUiState.Ready else ScanUiState.Error("Failed to initialize ML models from assets.")
        }
    }

    /**
     * Evaluates live frame for the green/red reticle in a lightweight, throttled way.
     */
    fun onLiveFrame(bitmap: Bitmap) {
        if (!isLiveProcessing.compareAndSet(false, true)) {
            if (!bitmap.isRecycled) bitmap.recycle()
            return
        }

        viewModelScope.launch {
            try {
                val start = System.currentTimeMillis()
                val (isLeaf, confidence) = diagnoseLeafUseCase.evaluateLiveFrame(bitmap)
                val elapsed = System.currentTimeMillis() - start
                _liveFrameState.value = LiveFrameState(isLeaf, confidence, elapsed)
            } catch (e: Exception) {
                android.util.Log.w("MainViewModel", "Live frame check error: ${e.message}")
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
                isLiveProcessing.set(false)
            }
        }
    }

    /**
     * Executes the full ML diagnosis on the captured photo.
     */
    fun onPhotoCaptured(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.value = ScanUiState.Processing
            try {
                val diagnosis = diagnoseLeafUseCase.execute(bitmap)
                if (!diagnosis.isLeafPresent) {
                    _uiState.value = ScanUiState.NoLeafDetected(
                        diagnosis.leafConfidence,
                        diagnosis.vegetationRatio,
                        diagnosis.inferenceTimeMs
                    )
                    return@launch
                }

                val tempFile = withContext(Dispatchers.IO) {
                    val file = File(getApplication<Application>().cacheDir, "captured_leaf.jpg")
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    file
                }

                _uiState.value = ScanUiState.ScanSuccess(diagnosis, tempFile)
            } catch (t: Throwable) {
                android.util.Log.e("MainViewModel", "Error diagnosing photo: ${t.message}", t)
                _uiState.value = ScanUiState.Error(t.message ?: "Processing failed")
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
    }

    fun saveCurrentScan(onComplete: (Boolean) -> Unit) {
        val currentState = _uiState.value
        if (currentState !is ScanUiState.ScanSuccess) {
            onComplete(false)
            return
        }

        viewModelScope.launch {
            try {
                saveScanUseCase(currentState.tempPhotoFile, currentState.diagnosis)
                onComplete(true)
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error saving scan: ${e.message}", e)
                onComplete(false)
            }
        }
    }

    fun deleteScan(scan: ScanEntity) {
        viewModelScope.launch {
            try {
                deleteScanUseCase(scan)
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error deleting scan: ${e.message}", e)
            }
        }
    }

    fun resetScanner() {
        _uiState.value = ScanUiState.Ready
    }
}
