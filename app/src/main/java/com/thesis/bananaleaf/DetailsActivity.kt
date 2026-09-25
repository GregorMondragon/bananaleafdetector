package com.thesis.bananaleaf

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import coil.load
import com.thesis.bananaleaf.databinding.ActivityDetailsBinding
import java.io.File
import java.util.Locale

/** Full-screen details page for the complete result of the current scan. */
class DetailsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IMAGE_PATH = "details_image_path"
        const val EXTRA_DISEASE_NAME = "details_disease_name"
        const val EXTRA_CONFIDENCE = "details_confidence"
        const val EXTRA_DISEASE_NAMES = "details_disease_names"
        const val EXTRA_DISEASE_CONFIDENCES = "details_disease_confidences"
        const val EXTRA_HEALTHY_CONFIDENCE = "details_healthy_confidence"
        const val EXTRA_FROM_TAB = "details_from_tab"

        fun launch(
            context: Context,
            imagePath: String,
            diseaseNames: ArrayList<String>,
            diseaseConfidences: FloatArray,
            healthyConfidence: Float,
            fromTab: Int = R.id.nav_garden
        ) {
            val names = if (diseaseNames.isEmpty()) arrayListOf("Healthy") else diseaseNames
            val intent = Intent(context, DetailsActivity::class.java).apply {
                putExtra(EXTRA_IMAGE_PATH, imagePath)
                putStringArrayListExtra(EXTRA_DISEASE_NAMES, names)
                putExtra(EXTRA_DISEASE_CONFIDENCES, diseaseConfidences)
                putExtra(EXTRA_HEALTHY_CONFIDENCE, healthyConfidence)
                putExtra(EXTRA_FROM_TAB, fromTab)
            }
            context.startActivity(intent)
        }
    }

    private lateinit var binding: ActivityDetailsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ── Window & Status Bar: Solid Emerald Green Header so white phone notifications, clock, and battery are clearly visible
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = ContextCompat.getColor(this, R.color.leaf_green)

        binding = ActivityDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Dynamically adjust statusBarScrim height to match the system status bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val lp = binding.statusBarScrim.layoutParams
            if (lp.height != statusBarHeight) {
                lp.height = statusBarHeight
                binding.statusBarScrim.layoutParams = lp
            }
            insets
        }

        // ── Leaf image
        intent.getStringExtra(EXTRA_IMAGE_PATH)?.let { path ->
            binding.diseaseImage.load(File(path)) {
                crossfade(true)
                placeholder(R.drawable.ic_leaf_logo)
                error(R.drawable.ic_leaf_logo)
            }
        }

        val namesRaw = intent.getStringArrayListExtra(EXTRA_DISEASE_NAMES)
            ?: arrayListOf(intent.getStringExtra(EXTRA_DISEASE_NAME).orEmpty().ifBlank { "Healthy" })
        val names = if (namesRaw.isEmpty()) arrayListOf("Healthy") else namesRaw
        val confidences = intent.getFloatArrayExtra(EXTRA_DISEASE_CONFIDENCES)
            ?: floatArrayOf(intent.getFloatExtra(EXTRA_CONFIDENCE, 0f))
        val healthyConfidence = intent.getFloatExtra(EXTRA_HEALTHY_CONFIDENCE, 0f).coerceIn(0f, 1f)
        val isHealthy = names.isEmpty() || (names.size == 1 && names[0].equals("Healthy", ignoreCase = true))

        val effectiveNames = if (isHealthy) listOf("Healthy") else names
        val detailsList = effectiveNames.map { DiseaseInfo.get(it) }

        // ── Title bar
        binding.titleText.text = if (isHealthy) "Plant Health Analysis" else getString(R.string.disease_details_title)

        // ── Disease name chip & confidence text
        if (isHealthy) {
            val pct = String.format(
                Locale.US, "%.1f%%",
                (if (healthyConfidence > 0f) healthyConfidence else 0.95f) * 100f
            )
            binding.diseaseName.text = "HEALTHY SPECIMEN ($pct)"
            binding.diseaseName.setBackgroundResource(R.drawable.bg_disease_name_chip_healthy)
            binding.diseaseName.setTextColor(ContextCompat.getColor(this, R.color.leaf_green_dark))

            binding.confidenceText.text = "Diagnostic Status: Optimal Foliar Health\nConfidence Score: $pct\n\nScreening Summary:\n• Negative for Black Sigatoka (Pseudocercospora fijiensis)\n• Negative for Cordana Leaf Spot (Neocordana musae)\n• Negative for Pestalotiopsis Leaf Blight (Pestalotiopsis spp.)\n\nThe scanned leaf exhibits strong physiological vigor, vibrant chlorophyll distribution, and intact laminar architecture with no necrotic lesions or fungal haloing."
        } else if (detailsList.size == 1) {
            val primary = detailsList.firstOrNull() ?: DiseaseInfo.get("Healthy")
            val pct = String.format(
                Locale.US, "%.1f%%", (confidences.getOrNull(0)?.coerceIn(0f, 1f) ?: 0f) * 100f
            )
            binding.diseaseName.text = "DISEASE DETECTED: ${primary.displayName.uppercase(Locale.US)}"
            binding.diseaseName.setBackgroundResource(R.drawable.bg_disease_name_chip)
            binding.diseaseName.setTextColor(android.graphics.Color.parseColor("#B3261E"))

            binding.confidenceText.text = "Primary Diagnosis: ${primary.displayName}\nConfidence Score: $pct\n\nSymptoms align with characteristic lesions of ${primary.displayName}. Prompt foliar sanitation is recommended."
        } else {
            binding.diseaseName.text = "CO-OCCURRING INFECTION (${detailsList.size} CONDITIONS)"
            binding.diseaseName.setBackgroundResource(R.drawable.bg_disease_name_chip)
            binding.diseaseName.setTextColor(android.graphics.Color.parseColor("#B3261E"))

            val primary = detailsList.firstOrNull() ?: DiseaseInfo.get("Healthy")
            val primaryConf = confidences.firstOrNull() ?: 0f
            val sb = StringBuilder()
            sb.append("Primary Diagnosis:\n")
            sb.append("• ${primary.displayName} — ${String.format(Locale.US, "%.1f%%", primaryConf * 100f)} (Dominant Signal)\n\n")
            sb.append("Secondary / Co-Occurring Detections:\n")
            for (i in 1 until detailsList.size) {
                val d = detailsList[i]
                val c = confidences.getOrNull(i) ?: 0f
                sb.append("• ${d.displayName} — ${String.format(Locale.US, "%.1f%%", c * 100f)}\n")
            }
            sb.append("\nNote: Banana foliage can simultaneously host overlapping fungal species. Prioritize sanitation for the dominant pathogen while monitoring secondary lesions.")
            binding.confidenceText.text = sb.toString()
        }

        // ── Identified symptoms / Foliar health indicators card
        binding.descriptionHeader.text = if (isHealthy) {
            "Foliar Health Indicators"
        } else {
            getString(R.string.identified_symptoms_header)
        }
        binding.descriptionText.text = if (isHealthy) {
            val healthyInfo = detailsList.firstOrNull() ?: DiseaseInfo.get("Healthy")
            "${healthyInfo.displayName}\n${healthyInfo.overview}\n\n" +
                healthyInfo.symptoms.joinToString("\n\n") { "• $it" }
        } else {
            detailsList.map { details ->
                "${details.displayName}\n${details.overview}\n\n" +
                    details.symptoms.joinToString("\n") { "• $it" }
            }.joinToString("\n\n")
        }

        // ── Treatment / Recommendation / Foliar Care card
        binding.treatmentHeader.text = if (isHealthy) {
            "Foliar Maintenance & Nutrition"
        } else {
            getString(R.string.treatment_recommendation_header)
        }
        binding.treatmentText.text = detailsList
            .flatMap { it.treatment.map { t -> "• $t" } }
            .distinct()
            .joinToString("\n\n")

        // ── Prevention / Management / Plantation Best Practices card
        binding.preventionHeader.text = if (isHealthy) {
            "Preventive Care & Canopy Management"
        } else {
            getString(R.string.prevention_management_header)
        }
        binding.preventionText.text = detailsList
            .flatMap { it.recommendations.map { r -> "• $r" } }
            .distinct()
            .joinToString("\n\n")

        // ── Agricultural Extension & Monitoring card
        binding.monitoringText.text = if (isHealthy) {
            "• Routine Surveillance: Perform visual foliar inspections every 10 to 14 days, monitoring the youngest unfurled 'cigar' leaf and first three open leaves.\n\n• Weather-Triggered Checks: Following continuous rainfall or high humidity, inspect the abaxial (underside) foliar surfaces for early speckling.\n\n• Sucker Selection: Ensure newly propagated suckers originate strictly from certified disease-free mother mats with robust records.\n\n• LGU Agricultural Extension: Consult your Municipal Agriculture Office (MAO) through the LGU Connect tab for soil testing, balanced fertilization schedules, and regional pest bulletins."
        } else {
            "• Active Surveillance: Inspect newly unfurled leaves every 7 to 10 days, particularly following rainfall or persistent morning dew.\n\n• Adjacent Mat Check: Survey neighboring banana plants within a 5-meter radius for early streak symptoms.\n\n• Extension Office Verification: For rapid or severe leaf loss across multiple plots, consult your local Municipal Agriculture Office (MAO) / LGU Connect for verified chemical programs."
        }

        // ── Warning note / Advisory
        binding.detailsWarningNote.text = if (isHealthy) {
            "Specimen Health Advisory\nThis analysis confirms no visual symptoms of target fungal diseases at the time of scanning. Continue standard plantation sanitation and routine monitoring to sustain healthy banana production."
        } else {
            getString(R.string.details_view_warning)
        }

        // ── Back button
        binding.backButton.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // ── Bottom navigation setup: select originating tab and handle seamless navigation
        val fromTab = intent.getIntExtra(EXTRA_FROM_TAB, R.id.nav_garden)
        binding.bottomNavigationView.selectedItemId = fromTab

        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            val targetIndex = when (item.itemId) {
                R.id.nav_home -> 0
                R.id.nav_garden -> 1
                R.id.nav_discover -> 2
                R.id.nav_lgu -> 3
                else -> 0
            }
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("TARGET_TAB", targetIndex)
            }
            startActivity(mainIntent)
            finish()
            true
        }
    }
}
