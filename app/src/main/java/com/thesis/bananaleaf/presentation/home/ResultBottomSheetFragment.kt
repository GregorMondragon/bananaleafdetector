package com.thesis.bananaleaf.presentation.home

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import coil.load
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.thesis.bananaleaf.DetailsActivity
import com.thesis.bananaleaf.DiseaseInfo
import com.thesis.bananaleaf.DiseaseInfoTagalog
import com.thesis.bananaleaf.R
import com.thesis.bananaleaf.databinding.FragmentResultSheetBinding
import com.thesis.bananaleaf.domain.model.DiagnosisResult
import com.thesis.bananaleaf.presentation.main.MainViewModel
import com.thesis.bananaleaf.presentation.main.ScanUiState
import com.thesis.bananaleaf.presentation.tts.BananaLeafTtsHelper
import java.io.File
import java.util.Locale

/**
 * ResultBottomSheetFragment — shown after every successful banana leaf scan.
 *
 * Replaces the old translationY peek/collapse hack in MainActivity.
 * Opens as a proper BottomSheetDialogFragment that is fully expanded by default.
 * Tapping the drag handle / dismiss dismisses the sheet and resets the scanner.
 */
class ResultBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentResultSheetBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()

    private var currentTab: Int = 0
    private var lastTopResultKey: String = "Healthy"
    private var ttsHelper: BananaLeafTtsHelper? = null

    companion object {
        private const val TAG = "ResultBottomSheet"
        private const val ARG_IMAGE_PATH = "arg_image_path"

        /** Disease names as a JSON string — passed via arguments bundle */
        private const val ARG_DIAGNOSIS_JSON = "arg_diagnosis_json"

        fun show(
            manager: FragmentManager,
            diagnosis: DiagnosisResult,
            imagePath: String
        ) {
            if (manager.isDestroyed) return
            try {
                val prev = manager.findFragmentByTag(TAG)
                if (prev != null) {
                    manager.beginTransaction().remove(prev).commitNowAllowingStateLoss()
                }
            } catch (_: Exception) {}

            val fragment = ResultBottomSheetFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_IMAGE_PATH, imagePath)
                    putBoolean("isHealthy", diagnosis.isHealthy)
                    putFloat("leafConfidence", diagnosis.leafConfidence)
                    putFloat("healthyConfidence", diagnosis.healthyConfidence)
                    val diseaseKeys = diagnosis.detectedDiseases.map { it.diseaseKey }
                    val diseaseNames = diagnosis.detectedDiseases.map { it.displayName }
                    val diseaseConfs = diagnosis.detectedDiseases.map { it.confidence }
                    putStringArray("diseaseKeys", diseaseKeys.toTypedArray())
                    putStringArray("diseaseNames", diseaseNames.toTypedArray())
                    putFloatArray("diseaseConfs", diseaseConfs.toFloatArray())
                }
            }

            try {
                val ft = manager.beginTransaction()
                ft.add(fragment, TAG)
                ft.commitAllowingStateLoss()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error displaying ResultBottomSheetFragment: ${e.message}", e)
            }
        }

        /** Shows the App Guide inside the same bottom sheet mechanism */
        fun showAppGuide(manager: FragmentManager) {
            AppGuideBottomSheetFragment.show(manager)
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResultSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ttsHelper = BananaLeafTtsHelper(requireContext())
        ttsHelper?.onPlaybackStateChanged = { isSpeaking ->
            updateSpeakerUi(isSpeaking)
        }
        populateContent()
        setupTabs()
        setupButtons()
        updateSpeakerUi(false)
    }

    override fun onStart() {
        super.onStart()
        // Force full expand when shown
        val sheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        sheet?.let {
            val behavior = BottomSheetBehavior.from(it)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.peekHeight = BottomSheetBehavior.PEEK_HEIGHT_AUTO
            behavior.skipCollapsed = true
        }
    }

    override fun onPause() {
        super.onPause()
        ttsHelper?.stop()
        updateSpeakerUi(false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ttsHelper?.stop()
        ttsHelper?.shutdown()
        ttsHelper = null
        _binding = null
    }

    // ─────────────────────────────────────────────────────────────────────
    // Content population
    // ─────────────────────────────────────────────────────────────────────

    private fun populateContent() {
        val args = arguments ?: return
        val imagePath = args.getString(ARG_IMAGE_PATH, "")
        val isHealthy = args.getBoolean("isHealthy", true)
        val leafConfidence = args.getFloat("leafConfidence", 1f)
        val healthyConfidence = args.getFloat("healthyConfidence", 0f)
        val diseaseKeys = args.getStringArray("diseaseKeys") ?: emptyArray()
        val diseaseNames = args.getStringArray("diseaseNames") ?: emptyArray()
        val diseaseConfs = args.getFloatArray("diseaseConfs") ?: floatArrayOf()

        // ── Leaf image
        if (imagePath.isNotEmpty()) {
            binding.resultLeafImage.load(File(imagePath)) {
                crossfade(true)
                placeholder(R.drawable.ic_leaf_logo)
                error(R.drawable.ic_leaf_logo)
            }
        }

        // ── Status overlay pill
        val decisionMode = when {
            isHealthy -> "HEALTHY"
            diseaseNames.size == 1 -> "SINGLE"
            else -> "MULTI"
        }

        when (decisionMode) {
            "HEALTHY" -> {
                binding.resultStatusOverlay.text = "HEALTHY"
                binding.resultStatusOverlay.setBackgroundResource(R.drawable.bg_status_pill_healthy)
                binding.resultStatusOverlay.setTextColor(
                    resources.getColor(R.color.status_healthy_text, requireActivity().theme)
                )
            }
            else -> {
                binding.resultStatusOverlay.text = "DISEASE"
                binding.resultStatusOverlay.setBackgroundResource(R.drawable.bg_status_pill_disease)
                binding.resultStatusOverlay.setTextColor(
                    resources.getColor(R.color.status_disease_text, requireActivity().theme)
                )
            }
        }

        // ── Title
        binding.infoTitle.text = when (decisionMode) {
            "HEALTHY" -> "Healthy Banana Leaf"
            "SINGLE" -> diseaseNames.firstOrNull() ?: "Unknown"
            else -> diseaseNames.joinToString(" & ")
        }

        // ── Summary
        binding.resultSummary.text = when (decisionMode) {
            "HEALTHY" -> {
                val pct = String.format(Locale.US, "%.1f%%", healthyConfidence * 100f)
                "HEALTHY • Leaf confidence: $pct"
            }
            "SINGLE" -> {
                val conf = diseaseConfs.firstOrNull() ?: 0f
                val pct = String.format(Locale.US, "%.1f%%", conf * 100f)
                if (conf < 0.65f) "UNCERTAIN • ${diseaseNames.firstOrNull()} • $pct — retake for clearer result"
                else "SINGLE LABEL • ${diseaseNames.firstOrNull()} • $pct confidence"
            }
            else -> "MULTI LABEL • ${diseaseNames.size} possible diseases detected"
        }

        // ── Disease name chips
        lastTopResultKey = if (isHealthy || diseaseKeys.isEmpty()) "Healthy" else diseaseKeys[0]
        buildDiseaseNameChips(diseaseNames.toList(), diseaseConfs, isHealthy)

        // ── Tab content
        val info = DiseaseInfo.get(lastTopResultKey)
        binding.diseaseResultText.text = info.overview
        binding.treatmentResultText.text = "• " + info.treatment.joinToString("\n• ")
        binding.recommendationResultText.text = "• " + info.recommendations.joinToString("\n• ")

        // ── Add to collection button text
        binding.addCollectionButton.text = "ADD TO COLLECTION"

        // Apply initial tab
        applyTab(0)
    }

    private fun buildDiseaseNameChips(
        names: List<String>,
        confidences: FloatArray,
        isHealthy: Boolean
    ) {
        val container = binding.diseaseNameChips
        container.removeAllViews()
        val density = resources.displayMetrics.density

        if (isHealthy) {
            addChip(container, "Healthy Leaf", null, isHealthy = true, density = density)
        } else {
            names.forEachIndexed { index, name ->
                val conf = confidences.getOrElse(index) { 0f }
                addChip(container, "$name  ${(conf * 100f).toInt()}%", null, isHealthy = false, density = density)
            }
        }
    }

    private fun addChip(
        container: ViewGroup,
        text: String,
        @Suppress("UNUSED_PARAMETER") icon: Any?,
        isHealthy: Boolean,
        density: Float
    ) {
        val chip = TextView(requireContext()).apply {
            this.text = text
            setBackgroundResource(
                if (isHealthy) R.drawable.bg_disease_name_chip_healthy else R.drawable.bg_disease_name_chip
            )
            setTextColor(
                if (isHealthy) resources.getColor(R.color.leaf_green_dark, requireActivity().theme)
                else Color.parseColor("#B3261E")
            )
            setPadding(
                (14 * density).toInt(), (8 * density).toInt(),
                (14 * density).toInt(), (8 * density).toInt()
            )
            textSize = 13f
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD
            )
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (6 * density).toInt() }
        }
        container.addView(chip)
    }

    // ─────────────────────────────────────────────────────────────────────
    // Tabs
    // ─────────────────────────────────────────────────────────────────────

    private fun setupTabs() {
        binding.tabDisease.setOnClickListener {
            val wasSpeaking = ttsHelper?.isSpeaking == true
            applyTab(0)
            if (wasSpeaking) speakResult()
        }
        binding.tabTreatment.setOnClickListener {
            val wasSpeaking = ttsHelper?.isSpeaking == true
            applyTab(1)
            if (wasSpeaking) speakResult()
        }
        binding.tabRecommendations.setOnClickListener {
            val wasSpeaking = ttsHelper?.isSpeaking == true
            applyTab(2)
            if (wasSpeaking) speakResult()
        }
    }

    private fun applyTab(tab: Int) {
        currentTab = tab
        binding.diseaseSection.visibility = if (tab == 0) View.VISIBLE else View.GONE
        binding.treatmentSection.visibility = if (tab == 1) View.VISIBLE else View.GONE
        binding.recommendationSection.visibility = if (tab == 2) View.VISIBLE else View.GONE

        // Active = filled green pill, inactive = transparent background
        listOf(binding.tabDisease, binding.tabTreatment, binding.tabRecommendations)
            .forEachIndexed { index, textView ->
                val isActive = index == tab
                textView.setBackgroundResource(
                    if (isActive) R.drawable.bg_tab_active else android.R.color.transparent
                )
                textView.setTextColor(
                    if (isActive) resources.getColor(R.color.white, requireActivity().theme)
                    else resources.getColor(R.color.leaf_green_dark, requireActivity().theme)
                )
                textView.typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.SANS_SERIF,
                    if (isActive) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
                )
            }
    }

    private fun speakResult() {
        val speech = DiseaseInfoTagalog.get(lastTopResultKey)
        val text = when (currentTab) {
            0 -> speech.overview
            1 -> speech.treatment
            else -> speech.recommendation
        }
        ttsHelper?.speak(text)
    }

    // ─────────────────────────────────────────────────────────────────────
    // Buttons
    // ─────────────────────────────────────────────────────────────────────

    private fun setupButtons() {
        binding.speakResultButton.setOnClickListener {
            if (ttsHelper?.isSpeaking == true) {
                ttsHelper?.stop()
                updateSpeakerUi(false)
            } else {
                speakResult()
            }
        }

        binding.resultHandle.setOnClickListener { dismissAndReset() }

        binding.viewDetailsButton.setOnClickListener {
            // Stop speech immediately when user navigates to more details
            ttsHelper?.stop()
            updateSpeakerUi(false)

            val args = arguments ?: return@setOnClickListener
            val imagePath = args.getString(ARG_IMAGE_PATH, "")
            val isHealthy = args.getBoolean("isHealthy", true)
            val diseaseKeys = args.getStringArray("diseaseKeys") ?: emptyArray()
            val diseaseConfs = args.getFloatArray("diseaseConfs") ?: floatArrayOf()
            val healthyConf = args.getFloat("healthyConfidence", 0f)
            if (imagePath.isEmpty()) return@setOnClickListener
            val names = ArrayList(diseaseKeys.toList().ifEmpty { listOf("Healthy") })
            DetailsActivity.launch(
                requireContext(), imagePath, names, diseaseConfs, healthyConf,
                fromTab = R.id.nav_home
            )
        }

        binding.addCollectionButton.setOnClickListener {
            val state = viewModel.uiState.value
            if (state !is com.thesis.bananaleaf.presentation.main.ScanUiState.ScanSuccess) {
                Toast.makeText(requireContext(), "Scan a leaf first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.saveCurrentScan { success ->
                requireActivity().runOnUiThread {
                    if (success) {
                        binding.addCollectionButton.text = "SAVED ✓"
                        Toast.makeText(requireContext(), "Saved to My Garden.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Could not save scan.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun updateSpeakerUi(isOn: Boolean) {
        if (_binding == null || !isAdded) return
        val ctx = context ?: return
        if (isOn) {
            // ON state: Solid Emerald Green background, Crisp White speaker icon
            binding.speakResultButton.setBackgroundResource(R.drawable.bg_speaker_button_on)
            binding.speakResultButton.setImageResource(R.drawable.ic_speaker_on)
            binding.speakResultButton.setColorFilter(ContextCompat.getColor(ctx, R.color.white))
            binding.speakResultButton.contentDescription = "Stop reading aloud"
        } else {
            // OFF state: Pure White background with Green outline, Leaf Green speaker-off icon
            binding.speakResultButton.setBackgroundResource(R.drawable.bg_speaker_button_off)
            binding.speakResultButton.setImageResource(R.drawable.ic_speaker_off)
            binding.speakResultButton.setColorFilter(ContextCompat.getColor(ctx, R.color.leaf_green))
            binding.speakResultButton.contentDescription = "Read result aloud"
        }
    }

    private fun dismissAndReset() {
        ttsHelper?.stop()
        updateSpeakerUi(false)
        dismissAllowingStateLoss()
        // Reset the scanner when the sheet is dismissed
        (parentFragment as? HomeFragment)?.resetForNewScan()
    }

    override fun onCancel(dialog: android.content.DialogInterface) {
        super.onCancel(dialog)
        ttsHelper?.stop()
        updateSpeakerUi(false)
        (parentFragment as? HomeFragment)?.resetForNewScan()
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        ttsHelper?.stop()
        updateSpeakerUi(false)
        (parentFragment as? HomeFragment)?.resetForNewScan()
    }
}
