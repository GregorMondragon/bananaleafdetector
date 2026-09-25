package com.thesis.bananaleaf.presentation.discover

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.thesis.bananaleaf.DiseaseInfo
import com.thesis.bananaleaf.databinding.FragmentDiscoverBinding

/**
 * DiscoverFragment — full-screen, opaque fragment for the Discover tab.
 *
 * Shows a list of banana leaf diseases with a premium left-accent card style,
 * plus an About this App informational card at the bottom.
 */
class DiscoverFragment : Fragment() {

    private var _binding: FragmentDiscoverBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDiscoverBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Adjust top padding to cleanly accommodate the green status bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.discoverAppBar) { v, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.updatePadding(top = statusBarHeight)
            insets
        }
        ViewCompat.requestApplyInsets(binding.discoverAppBar)

        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        val items = DiscoverItem.buildList()
        val adapter = DiscoverAdapter(items) { item ->
            // Show a disease detail bottom sheet when tapped
            DiseaseDetailBottomSheetFragment.show(childFragmentManager, item.key)
        }
        binding.discoverRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
