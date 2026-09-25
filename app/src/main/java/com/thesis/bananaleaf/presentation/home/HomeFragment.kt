package com.thesis.bananaleaf.presentation.home

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.thesis.bananaleaf.DiseaseInfo
import com.thesis.bananaleaf.R
import com.thesis.bananaleaf.databinding.FragmentHomeBinding
import com.thesis.bananaleaf.domain.model.DiagnosisResult
import com.thesis.bananaleaf.presentation.main.MainViewModel
import com.thesis.bananaleaf.presentation.main.ScanUiState
import com.thesis.bananaleaf.presentation.tts.BananaLeafTtsHelper
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * HomeFragment — camera scanner screen.
 *
 * This fragment is ADDED once and shown/hidden via the fragment manager.
 * It is NEVER replaced, so the camera lifecycle is preserved across tab switches.
 * The result bottom sheet is a separate BottomSheetDialogFragment that sits on top.
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()

    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var isFlashOn = false

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var freezeFrameBitmap: Bitmap? = null
    var lastDiagnosis: DiagnosisResult? = null
    var lastResultPath: String = ""

    private var lastTopResultKey: String = "Healthy"
    private var lastDetectedSpokenText: String = ""
    private var ttsHelper: BananaLeafTtsHelper? = null

    private var lastLiveFrameTimestamp = 0L

    @Volatile
    private var scanFrameRelativeRect: RectF? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera()
            else Toast.makeText(requireContext(), R.string.camera_permission_required, Toast.LENGTH_LONG).show()
        }

    // ────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ────────────────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupBlur()
        setupButtons()
        setShutterEnabled(false)
        updateInferenceStats(0L)
        observeViewModel()

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        binding.scanFrame.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateScanFrameCropBounds()
        }
        binding.viewFinder.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateScanFrameCropBounds()
        }
        binding.root.post { updateScanFrameCropBounds() }

        ttsHelper = BananaLeafTtsHelper(requireContext())
    }

    override fun onPause() {
        super.onPause()
        camera?.cameraControl?.enableTorch(false)
        isFlashOn = false
        updateTorchButton(false)
        ttsHelper?.stop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        freezeFrameBitmap?.recycle()
        freezeFrameBitmap = null
        ttsHelper?.shutdown()
        ttsHelper = null
        cameraExecutor.shutdown()
        _binding = null
    }

    // ────────────────────────────────────────────────────────────────────────
    // Camera
    // ────────────────────────────────────────────────────────────────────────

    private fun setupBlur() {
        binding.blurOutsideFrame.setCutoutView(binding.scanFrame)
        binding.blurOutsideFrame.setupWith(binding.cameraBlurTarget)
            .setBlurRadius(4.5f)
            .setBlurAutoUpdate(true)
            .setOverlayColor(0x20000000)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(cameraExecutor, ::analyzeLiveFrame) }

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    viewLifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA,
                    preview, imageCapture, imageAnalysis
                )
                setupFlashButton()
            } catch (e: Exception) {
                Toast.makeText(requireContext(),
                    getString(R.string.camera_start_failed, e.message), Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun analyzeLiveFrame(image: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastLiveFrameTimestamp < 350) { image.close(); return }
        lastLiveFrameTimestamp = now
        val bitmap = yuvImageProxyToBitmap(image)
        image.close()
        if (bitmap != null) {
            val cropped = cropBitmapToScanFrame(bitmap)
            viewModel.onLiveFrame(cropped)
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Buttons
    // ────────────────────────────────────────────────────────────────────────

    private fun setupButtons() {
        val triggerShutter = {
            if (binding.captureButton.isEnabled && viewModel.liveFrameState.value.isLeaf) {
                binding.captureButtonRing.animate().scaleX(0.91f).scaleY(0.91f).setDuration(70)
                    .withEndAction {
                        binding.captureButtonRing.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    }.start()
                capturePhoto()
            }
        }
        binding.captureButton.setOnClickListener { triggerShutter() }
        binding.captureButtonRing.setOnClickListener { triggerShutter() }

        binding.settingsButton.setOnClickListener {
            ResultBottomSheetFragment.showAppGuide(childFragmentManager)
        }

        binding.capturedFreezeFrame.setOnClickListener {
            if (viewModel.uiState.value !is ScanUiState.Processing) {
                resetForNewScan()
            }
        }
    }

    private fun setShutterEnabled(enabled: Boolean) {
        val isLeaf = viewModel.liveFrameState.value.isLeaf
        val canClick = enabled && isLeaf
        binding.captureButton.isEnabled = canClick
        binding.captureButtonRing.isEnabled = canClick
        binding.captureButton.alpha = if (canClick) 1.0f else 0.40f
        binding.captureButtonRing.alpha = if (canClick) 1.0f else 0.40f
    }

    // ────────────────────────────────────────────────────────────────────────
    // Capture
    // ────────────────────────────────────────────────────────────────────────

    fun capturePhoto() {
        if (!viewModel.liveFrameState.value.isLeaf) {
            return
        }
        val capture = imageCapture ?: return
        setShutterEnabled(false)
        binding.capturedFreezeFrame.visibility = View.GONE
        binding.capturedFreezeFrame.setImageDrawable(null)
        freezeFrameBitmap?.recycle()
        freezeFrameBitmap = null

        capture.takePicture(
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = try {
                        imageProxyToBitmap(image)
                    } finally {
                        image.close()
                    }
                    if (bitmap == null) {
                        setShutterEnabled(true)
                        binding.capturedFreezeFrame.visibility = View.GONE
                        binding.detectionTitle.text = "IMAGE PROCESSING FAILED"
                        return
                    }
                    val freezeCopy = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
                    freezeFrameBitmap?.recycle()
                    freezeFrameBitmap = freezeCopy
                    binding.capturedFreezeFrame.setImageBitmap(freezeCopy)
                    binding.capturedFreezeFrame.visibility = View.VISIBLE
                    val cropped = cropBitmapToScanFrame(bitmap)
                    viewModel.onPhotoCaptured(cropped)
                }

                override fun onError(exception: ImageCaptureException) {
                    setShutterEnabled(true)
                    binding.capturedFreezeFrame.visibility = View.GONE
                    freezeFrameBitmap?.recycle()
                    freezeFrameBitmap = null
                    Toast.makeText(requireContext(),
                        getString(R.string.capture_failed, exception.message), Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    // ────────────────────────────────────────────────────────────────────────
    // ViewModel Observer
    // ────────────────────────────────────────────────────────────────────────

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state -> handleUiState(state) }
                }
                launch {
                    viewModel.liveFrameState.collect { live ->
                        updateInferenceStats(live.inferenceTimeMs)
                        if (binding.progressBar.visibility != View.VISIBLE) {
                            applyStatusColor(live.isLeaf, hasDisease = false, confidence = live.confidence)
                            if (!live.isLeaf) {
                                binding.detectionTitle.text = "Looking for banana leaf…"
                                binding.detectionHealth.text = "Align a real banana leaf inside the frame"
                                binding.detectionConfidence.visibility = View.GONE
                                setShutterEnabled(false)
                            } else {
                                binding.detectionTitle.text = "Banana Leaf"
                                binding.detectionConfidence.text =
                                    String.format(Locale.US, "%.0f%%", live.confidence * 100f)
                                binding.detectionHealth.text = "Ready • Tap Shutter to Diagnose"
                                setShutterEnabled(true)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleUiState(state: ScanUiState) {
        when (state) {
            is ScanUiState.Initial, is ScanUiState.Ready -> {
                binding.progressBar.visibility = View.GONE
                setShutterEnabled(viewModel.liveFrameState.value.isLeaf)
            }
            is ScanUiState.LoadingModels -> {
                binding.progressBar.visibility = View.VISIBLE
                binding.detectionTitle.text = "INITIALIZING MODELS"
                binding.detectionHealth.text = "Loading offline leaf detection models"
            }
            is ScanUiState.Processing -> {
                binding.progressBar.visibility = View.VISIBLE
                setShutterEnabled(false)
                binding.detectionTitle.text = "ANALYZING BANANA LEAF"
                binding.detectionHealth.text = "Running offline leaf analysis models"
            }
            is ScanUiState.NoLeafDetected -> {
                binding.progressBar.visibility = View.GONE
                applyStatusColor(leafPresent = false, hasDisease = false)
                binding.detectionTitle.text = "Not a banana leaf"
                binding.detectionConfidence.visibility = View.GONE
                binding.detectionHealth.text = "Hold steady inside frame • Retrying…"
                updateInferenceStats(state.inferenceTimeMs)

                // Auto-unfreeze so the camera preview resumes smoothly and user is not stuck
                binding.capturedFreezeFrame.postDelayed({
                    if (_binding != null && viewModel.uiState.value is ScanUiState.NoLeafDetected) {
                        resetForNewScan()
                    }
                }, 1600)
            }
            is ScanUiState.ScanSuccess -> {
                binding.progressBar.visibility = View.GONE
                setShutterEnabled(true)
                lastDiagnosis = state.diagnosis
                lastResultPath = state.tempPhotoFile.absolutePath
                applyStatusColor(
                    leafPresent = true,
                    hasDisease = state.diagnosis.detectedDiseases.isNotEmpty(),
                    confidence = state.diagnosis.leafConfidence
                )
                updateInferenceStats(state.diagnosis.inferenceTimeMs)
                if (state.diagnosis.detectedDiseases.isNotEmpty() && isFlashOn) {
                    camera?.cameraControl?.enableTorch(false)
                    isFlashOn = false
                    updateTorchButton(false)
                }
                // Show result as proper BottomSheetDialogFragment
                ResultBottomSheetFragment.show(
                    childFragmentManager,
                    state.diagnosis,
                    state.tempPhotoFile.absolutePath
                )
            }
            is ScanUiState.Error -> {
                binding.progressBar.visibility = View.GONE
                setShutterEnabled(true)
                binding.detectionTitle.text = "SCAN FAILED"
                binding.detectionHealth.text = "${state.message} • Tap to retry"
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()

                // Auto-unfreeze on failure so user is never stuck
                binding.capturedFreezeFrame.postDelayed({
                    if (_binding != null && viewModel.uiState.value is ScanUiState.Error) {
                        resetForNewScan()
                    }
                }, 1600)
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Status color
    // ────────────────────────────────────────────────────────────────────────

    private fun applyStatusColor(leafPresent: Boolean, hasDisease: Boolean, confidence: Float = 1f) {
        val textColor = when {
            !leafPresent -> Color.parseColor("#E2E8F0")
            hasDisease -> Color.parseColor("#FDE047")
            else -> Color.parseColor("#4ADE80")
        }
        val frameColor = if (leafPresent) Color.parseColor("#34D399") else Color.argb(160, 255, 255, 255)
        binding.scanFrameCorners.cornerColor = frameColor
        binding.captureButtonRing.setBackgroundResource(
            if (leafPresent) R.drawable.bg_capture_ring_active else R.drawable.bg_capture_ring_idle
        )
        binding.statusPill.setBackgroundResource(
            if (leafPresent) R.drawable.bg_status_capsule_detected else R.drawable.bg_status_capsule_idle
        )
        binding.statusDot.setBackgroundResource(
            if (leafPresent) R.drawable.bg_status_dot_detected else R.drawable.bg_status_dot_searching
        )
        binding.detectionTitle.setTextColor(textColor)
        binding.detectionConfidence.visibility = if (leafPresent) View.VISIBLE else View.GONE
        binding.detectionConfidence.setTextColor(Color.WHITE)
    }

    // ────────────────────────────────────────────────────────────────────────
    // Flash
    // ────────────────────────────────────────────────────────────────────────

    private fun setupFlashButton() {
        val currentCamera = camera ?: return
        if (!currentCamera.cameraInfo.hasFlashUnit()) {
            binding.flashButton.visibility = View.GONE
            return
        }
        binding.flashButton.visibility = View.VISIBLE
        isFlashOn = false
        updateTorchButton(false)
        currentCamera.cameraInfo.torchState.observe(viewLifecycleOwner) { state ->
            val on = state == TorchState.ON
            isFlashOn = on
            updateTorchButton(on)
        }
        binding.flashButton.setOnClickListener {
            val next = !isFlashOn
            isFlashOn = next
            updateTorchButton(next)
            camera?.cameraControl?.enableTorch(next)
        }
    }

    private fun updateTorchButton(torchOn: Boolean) {
        binding.flashButton.setImageResource(if (torchOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off)
        binding.flashButton.contentDescription = getString(if (torchOn) R.string.flash_on else R.string.flash_off)
    }

    // ────────────────────────────────────────────────────────────────────────
    // TTS
    // ────────────────────────────────────────────────────────────────────────

    private fun speakCurrentResult() {
        val text = lastDetectedSpokenText.ifEmpty { "No result available yet." }
        ttsHelper?.speak(text)
    }

    fun setLastDetectedText(text: String) {
        lastDetectedSpokenText = text
    }

    // ────────────────────────────────────────────────────────────────────────
    // Reset
    // ────────────────────────────────────────────────────────────────────────

    fun resetForNewScan() {
        binding.capturedFreezeFrame.visibility = View.GONE
        binding.capturedFreezeFrame.setImageDrawable(null)
        freezeFrameBitmap?.recycle()
        freezeFrameBitmap = null
        lastResultPath = ""
        lastDiagnosis = null
        lastDetectedSpokenText = ""
        binding.detectionTitle.text = "Looking for banana leaf…"
        binding.detectionConfidence.visibility = View.GONE
        binding.detectionHealth.text = "Center a leaf inside the frame and tap to scan"
        applyStatusColor(leafPresent = false, hasDisease = false)
        viewModel.resetScanner()
    }

    // ────────────────────────────────────────────────────────────────────────
    // Inference stats
    // ────────────────────────────────────────────────────────────────────────

    private fun updateInferenceStats(elapsedMs: Long) {
        val safeElapsed = elapsedMs.coerceAtLeast(0L)
        val fps = if (safeElapsed > 0) (1000f / safeElapsed).coerceAtMost(60f) else 30.0f
        val runtime = Runtime.getRuntime()
        val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024f * 1024f)
        binding.inferenceStatsBar.text = String.format(
            Locale.US, "Inference: %d ms | FPS: %.1f | RAM: %.0f MB", safeElapsed, fps, usedMb
        )
        binding.inferenceStatsBar.visibility = View.VISIBLE
    }

    // ────────────────────────────────────────────────────────────────────────
    // Crop / scan frame math
    // ────────────────────────────────────────────────────────────────────────

    private fun updateScanFrameCropBounds() {
        val b = _binding ?: return
        b.scanFrame.post {
            val targetRect = Rect(); b.scanFrame.getGlobalVisibleRect(targetRect)
            val containerRect = Rect(); b.viewFinder.getGlobalVisibleRect(containerRect)
            val vW = containerRect.width().toFloat(); val vH = containerRect.height().toFloat()
            if (vW > 0f && vH > 0f) {
                scanFrameRelativeRect = RectF(
                    (targetRect.left - containerRect.left) / vW,
                    (targetRect.top - containerRect.top) / vH,
                    (targetRect.right - containerRect.left) / vW,
                    (targetRect.bottom - containerRect.top) / vH
                )
            }
        }
    }

    private fun cropBitmapToScanFrame(source: Bitmap): Bitmap {
        var bounds = scanFrameRelativeRect
        val viewW = binding.viewFinder.width.toFloat()
        val viewH = binding.viewFinder.height.toFloat()
        if (bounds == null && viewW > 0f && viewH > 0f) {
            val t = Rect(); binding.scanFrame.getGlobalVisibleRect(t)
            val c = Rect(); binding.viewFinder.getGlobalVisibleRect(c)
            val cW = c.width().toFloat(); val cH = c.height().toFloat()
            if (cW > 0f && cH > 0f) {
                bounds = RectF(
                    (t.left - c.left) / cW, (t.top - c.top) / cH,
                    (t.right - c.left) / cW, (t.bottom - c.top) / cH
                )
                scanFrameRelativeRect = bounds
            }
        }
        if (bounds == null || viewW <= 0f || viewH <= 0f) return source
        val bW = source.width.toFloat(); val bH = source.height.toFloat()
        val scale = maxOf(viewW / bW, viewH / bH)
        val dx = (viewW - bW * scale) / 2f; val dy = (viewH - bH * scale) / 2f
        val cropX = ((bounds.left * viewW - dx) / scale).toInt().coerceIn(0, source.width - 1)
        val cropY = ((bounds.top * viewH - dy) / scale).toInt().coerceIn(0, source.height - 1)
        val cropW = (((bounds.right - bounds.left) * viewW) / scale).toInt().coerceIn(1, source.width - cropX)
        val cropH = (((bounds.bottom - bounds.top) * viewH) / scale).toInt().coerceIn(1, source.height - cropY)
        return try {
            val cropped = Bitmap.createBitmap(source, cropX, cropY, cropW, cropH)
            if (cropped !== source) source.recycle()
            cropped
        } catch (e: Exception) { source }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Image conversion helpers
    // ────────────────────────────────────────────────────────────────────────

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        // Read dimensions first without allocating full bitmap in memory
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)
        val origW = boundsOptions.outWidth
        val origH = boundsOptions.outHeight

        // Target max dimension ~1600px for speed, safety, and crisp display
        val maxDim = maxOf(origW, origH)
        var sampleSize = 1
        while (maxDim / (sampleSize * 2) >= 1600) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions) ?: return null
        val rotation = image.imageInfo.rotationDegrees
        if (rotation == 0) return bitmap
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun yuvImageProxyToBitmap(image: ImageProxy): Bitmap? {
        if (image.format != ImageFormat.YUV_420_888) return null
        val width = image.width; val height = image.height
        val yPlane = image.planes[0]; val uPlane = image.planes[1]; val vPlane = image.planes[2]
        val nv21 = ByteArray(width * height * 3 / 2); var pos = 0
        val yBuffer = yPlane.buffer; val yRowStride = yPlane.rowStride
        for (row in 0 until height) {
            yBuffer.position(row * yRowStride); yBuffer.get(nv21, pos, width); pos += width
        }
        val uBuffer = uPlane.buffer; val vBuffer = vPlane.buffer
        val uRowStride = uPlane.rowStride; val vRowStride = vPlane.rowStride
        val uPixelStride = uPlane.pixelStride; val vPixelStride = vPlane.pixelStride
        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                nv21[pos++] = vBuffer.get(row * vRowStride + col * vPixelStride)
                nv21[pos++] = uBuffer.get(row * uRowStride + col * uPixelStride)
            }
        }
        val yuvImage = android.graphics.YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 70, out)
        val jpegBytes = out.toByteArray()
        val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size) ?: return null
        val rotation = image.imageInfo.rotationDegrees
        if (rotation == 0) return bitmap
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }
}
