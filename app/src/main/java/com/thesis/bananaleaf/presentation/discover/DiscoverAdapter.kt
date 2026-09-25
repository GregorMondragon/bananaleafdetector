package com.thesis.bananaleaf.presentation.discover

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.thesis.bananaleaf.DiseaseInfo
import com.thesis.bananaleaf.databinding.ItemDiscoverCardBinding

/**
 * Adapter for the Discover screen disease list.
 * Drives item_discover_card.xml items from DiseaseInfo data.
 */
class DiscoverAdapter(
    private val items: List<DiscoverItem>,
    private val onItemClick: (DiscoverItem) -> Unit
) : RecyclerView.Adapter<DiscoverAdapter.DiscoverViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DiscoverViewHolder {
        val binding = ItemDiscoverCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return DiscoverViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: DiscoverViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class DiscoverViewHolder(
        private val binding: ItemDiscoverCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DiscoverItem) {
            binding.discoverDiseaseName.text = item.displayName
            binding.discoverDiseaseType.text = item.typeLabel
            binding.discoverDiseaseOverview.text = item.overview
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }
}

data class DiscoverItem(
    val key: String,
    val displayName: String,
    val typeLabel: String,
    val overview: String
) {
    companion object {
        /** Build the discover list from the DiseaseInfo data source, excluding Healthy */
        fun buildList(): List<DiscoverItem> {
            val typeMap = mapOf(
                "Sigatoka" to "Fungal • Leaf disease",
                "Panama" to "Fungal • Soil-borne wilt",
                "BBW" to "Bacterial • Wilt disease",
                "BBTD" to "Viral • Bunchy top virus",
                "Moko" to "Bacterial • Vascular wilt",
                "Cordana" to "Fungal • Leaf spot",
                "Pestalotiopsis" to "Fungal • Leaf blight"
            )
            return DiseaseInfo.getAllKeys()
                .filter { it != "Healthy" }
                .map { key ->
                    val info = DiseaseInfo.get(key)
                    DiscoverItem(
                        key = key,
                        displayName = info.displayName,
                        typeLabel = typeMap[key] ?: "Leaf condition",
                        overview = info.overview
                    )
                }
        }
    }
}
