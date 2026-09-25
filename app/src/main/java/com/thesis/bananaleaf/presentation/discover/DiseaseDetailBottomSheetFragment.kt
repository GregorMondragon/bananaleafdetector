package com.thesis.bananaleaf.presentation.discover

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.thesis.bananaleaf.DiseaseInfo
import com.thesis.bananaleaf.R

/**
 * Shows full disease details in a bottom sheet when a disease card in Discover is tapped.
 */
class DiseaseDetailBottomSheetFragment : BottomSheetDialogFragment() {

    companion object {
        private const val TAG = "DiseaseDetailSheet"
        private const val ARG_KEY = "disease_key"

        fun show(manager: FragmentManager, key: String) {
            (manager.findFragmentByTag(TAG) as? DiseaseDetailBottomSheetFragment)
                ?.dismissAllowingStateLoss()
            DiseaseDetailBottomSheetFragment().apply {
                arguments = Bundle().also { it.putString(ARG_KEY, key) }
            }.show(manager, TAG)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val key = arguments?.getString(ARG_KEY) ?: "Healthy"
        val info = DiseaseInfo.get(key)
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(32))
        }

        // Drag handle
        val handleWrapper = LinearLayout(requireContext()).apply {
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(16) }
        }
        val handle = View(requireContext()).apply {
            val shape = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(2).toFloat()
                setColor(android.graphics.Color.parseColor("#CCCED8"))
            }
            background = shape
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(4)).also {
                it.topMargin = dp(8)
                it.gravity = android.view.Gravity.CENTER_HORIZONTAL
            }
        }
        handleWrapper.addView(handle)
        root.addView(handleWrapper)

        // Title
        root.addView(TextView(requireContext()).apply {
            text = info.displayName
            textSize = 22f
            setTextColor(resources.getColor(R.color.leaf_green_dark, requireActivity().theme))
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(6) }
        })

        // Overview
        root.addView(TextView(requireContext()).apply {
            text = info.overview
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_primary, requireActivity().theme))
            setLineSpacing(dp(4).toFloat(), 1f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(16) }
        })

        fun section(header: String, lines: List<String>) {
            root.addView(TextView(requireContext()).apply {
                text = header
                textSize = 13f
                setTextColor(resources.getColor(R.color.leaf_green_dark, requireActivity().theme))
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD
                )
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = dp(8); it.bottomMargin = dp(6) }
            })
            val card = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_shadow_card)
                elevation = dp(2).toFloat()
                setPadding(dp(16), dp(14), dp(16), dp(14))
            }
            lines.forEach { line ->
                card.addView(TextView(requireContext()).apply {
                    text = "• $line"
                    textSize = 14f
                    setTextColor(resources.getColor(R.color.text_primary, requireActivity().theme))
                    setLineSpacing(dp(3).toFloat(), 1f)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { it.bottomMargin = dp(4) }
                })
            }
            root.addView(card, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(12) })
        }

        section("Symptoms", info.symptoms)
        section("Treatment", info.treatment)
        section("Recommendations", info.recommendations)

        return androidx.core.widget.NestedScrollView(requireContext()).apply { addView(root) }
    }

    override fun onStart() {
        super.onStart()
        dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let {
            BottomSheetBehavior.from(it).apply {
                state = BottomSheetBehavior.STATE_EXPANDED
                skipCollapsed = true
            }
        }
    }
}
