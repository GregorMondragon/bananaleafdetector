package com.thesis.bananaleaf.presentation.garden

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.thesis.bananaleaf.DetailsActivity
import com.thesis.bananaleaf.databinding.FragmentGardenBinding
import com.thesis.bananaleaf.presentation.main.MainViewModel
import kotlinx.coroutines.launch

/**
 * GardenFragment — full-screen, opaque fragment for the My Garden tab.
 *
 * Displays saved banana leaf scans in a premium RecyclerView.
 * Uses activityViewModels() to share the MainViewModel (and its gardenScans flow)
 * with the rest of the app.
 */
class GardenFragment : Fragment() {

    private var _binding: FragmentGardenBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var gardenAdapter: GardenAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGardenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Adjust top padding to cleanly accommodate the green status bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.gardenAppBar) { v, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.updatePadding(top = statusBarHeight)
            insets
        }
        ViewCompat.requestApplyInsets(binding.gardenAppBar)

        setupRecyclerView()
        observeGardenScans()

        binding.gardenScanNowBtn.setOnClickListener {
            // Navigate back to Home tab
            (activity as? com.thesis.bananaleaf.MainActivity)?.navigateTo(0)
        }
    }

    private fun setupRecyclerView() {
        gardenAdapter = GardenAdapter(
            onOpenDetails = { entry ->
                val diseases = entry.getDiseaseNames()
                val diseaseList = if (entry.isHealthy || diseases.isEmpty()) {
                    arrayListOf("Healthy")
                } else {
                    ArrayList(diseases)
                }
                DetailsActivity.launch(
                    requireContext(),
                    entry.imagePath,
                    diseaseList,
                    entry.getConfidences().toFloatArray(),
                    entry.healthyConfidence
                )
            },
            onScanAgain = {
                (activity as? com.thesis.bananaleaf.MainActivity)?.navigateTo(0)
            },
            onDelete = { entry ->
                viewModel.deleteScan(entry)
                android.widget.Toast.makeText(
                    requireContext(), "Removed from My Garden.", android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        )
        binding.gardenRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = gardenAdapter
        }
    }

    private fun observeGardenScans() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.gardenScans.collect { scans ->
                    gardenAdapter.submitList(scans)
                    val isEmpty = scans.isEmpty()
                    binding.gardenRecyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
                    binding.gardenEmptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
