package com.thesis.bananaleaf.presentation.garden

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.thesis.bananaleaf.DiseaseInfo
import com.thesis.bananaleaf.R
import com.thesis.bananaleaf.data.local.db.ScanEntity
import com.thesis.bananaleaf.databinding.ItemGardenCardBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GardenAdapter(
    private val onOpenDetails: (ScanEntity) -> Unit,
    private val onScanAgain: () -> Unit,
    private val onDelete: (ScanEntity) -> Unit
) : ListAdapter<ScanEntity, GardenAdapter.GardenViewHolder>(DiffCallback) {

    private val dateFormat = SimpleDateFormat("MMM d, yyyy | h:mm a", Locale.US)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GardenViewHolder {
        val binding = ItemGardenCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return GardenViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GardenViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class GardenViewHolder(
        private val binding: ItemGardenCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ScanEntity) {
            val context = binding.root.context

            // Coil asynchronous image loading — full-width card image, no circle crop
            binding.cardThumb.load(File(item.imagePath)) {
                crossfade(true)
                placeholder(R.drawable.ic_leaf_logo)
                error(R.drawable.ic_leaf_logo)
            }

            binding.cardDate.text = dateFormat.format(Date(item.timestamp))

            val diseaseNames = item.getDiseaseNames()
            val confidences = item.getConfidences()

            if (item.isHealthy) {
                binding.cardStatusPill.text = "HEALTHY"
                binding.cardStatusPill.setBackgroundResource(R.drawable.bg_status_pill_healthy)
                binding.cardStatusPill.setTextColor(ContextCompat.getColor(context, R.color.leaf_green_dark))

                val pct = String.format(Locale.US, "%.1f%%", item.healthyConfidence * 100f)
                binding.cardConditionText.text = "Healthy Leaf ($pct)"
                binding.cardSecondaryText.visibility = android.view.View.GONE
            } else {
                binding.cardStatusPill.text = "DISEASE DETECTED"
                binding.cardStatusPill.setBackgroundResource(R.drawable.bg_status_pill_disease)
                binding.cardStatusPill.setTextColor(ContextCompat.getColor(context, R.color.status_disease_text))

                if (diseaseNames.isEmpty()) {
                    binding.cardConditionText.text = "Unspecified Condition"
                    binding.cardSecondaryText.visibility = android.view.View.GONE
                } else if (diseaseNames.size == 1) {
                    val displayName = DiseaseInfo.get(diseaseNames[0]).displayName
                    val conf = confidences.firstOrNull() ?: 0f
                    binding.cardConditionText.text = "$displayName (${String.format(Locale.US, "%.1f%%", conf * 100f)})"
                    binding.cardSecondaryText.visibility = android.view.View.GONE
                } else {
                    val primaryName = DiseaseInfo.get(diseaseNames[0]).displayName
                    val primaryConf = confidences.firstOrNull() ?: 0f
                    binding.cardConditionText.text = "$primaryName (${(primaryConf * 100f).toInt()}%)"

                    val secondary = diseaseNames.drop(1).mapIndexed { idx, name ->
                        val disp = DiseaseInfo.get(name).displayName
                        val conf = confidences.getOrNull(idx + 1) ?: 0f
                        "• $disp (${(conf * 100f).toInt()}%)"
                    }.joinToString("\n")
                    binding.cardSecondaryText.text = secondary
                    binding.cardSecondaryText.visibility = android.view.View.VISIBLE
                }
            }

            binding.root.setOnClickListener { onOpenDetails(item) }
            binding.cardViewAnalysisBtn.setOnClickListener { onOpenDetails(item) }
            binding.cardDeleteBtn.setOnClickListener { onDelete(item) }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<ScanEntity>() {
        override fun areItemsTheSame(oldItem: ScanEntity, newItem: ScanEntity): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ScanEntity, newItem: ScanEntity): Boolean =
            oldItem == newItem
    }
}
