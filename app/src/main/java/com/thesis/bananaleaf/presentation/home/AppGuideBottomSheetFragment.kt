package com.thesis.bananaleaf.presentation.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.thesis.bananaleaf.R

/**
 * App Guide bottom sheet — shown when user taps the Settings/Guide icon.
 * Lightweight static content, no ViewModel needed.
 */
class AppGuideBottomSheetFragment : BottomSheetDialogFragment() {

    companion object {
        private const val TAG = "AppGuideSheet"

        fun show(manager: FragmentManager) {
            if (manager.findFragmentByTag(TAG) != null) return
            AppGuideBottomSheetFragment().show(manager, TAG)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val density = resources.displayMetrics.density
            setPadding(
                (20 * density).toInt(), (8 * density).toInt(),
                (20 * density).toInt(), (32 * density).toInt()
            )
        }

        val density = resources.displayMetrics.density

        fun dp(v: Int) = (v * density).toInt()

        // Drag handle
        val handle = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(4)).also {
                it.gravity = android.view.Gravity.CENTER_HORIZONTAL
                it.topMargin = dp(8); it.bottomMargin = dp(16)
            }
            setBackgroundColor(android.graphics.Color.parseColor("#CCCED8"))
            background?.also {
                val shape = android.graphics.drawable.GradientDrawable()
                shape.shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                shape.cornerRadius = dp(2).toFloat()
                shape.setColor(android.graphics.Color.parseColor("#CCCED8"))
                background = shape
            }
        }
        root.addView(handle, LinearLayout.LayoutParams(dp(44), dp(4)).also {
            it.gravity = android.view.Gravity.CENTER_HORIZONTAL
            it.topMargin = dp(8); it.bottomMargin = dp(16)
        })

        // Title
        val title = TextView(requireContext()).apply {
            text = "App Guide"
            textSize = 22f
            setTextColor(resources.getColor(R.color.leaf_green_dark, requireActivity().theme))
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(16) }
        }
        root.addView(title)

        val steps = listOf(
            "1. SCAN A LEAF" to "Tap the shutter button. Place one banana leaf inside the scan frame. Use good lighting and keep the leaf centered.",
            "2. READ THE RESULT" to "After scanning, the result sheet opens automatically. Tap Disease, Treatment, or Recommendations to switch between sections. Tap the speaker icon to hear the result read aloud.",
            "3. SAVE A SCAN" to "Tap ADD TO COLLECTION inside the result sheet to save the scan to My Garden.",
            "4. SCAN AGAIN" to "Dismiss the result sheet to return to the scanner for a new scan.",
            "5. NAVIGATION" to "Home opens the scanner. My Garden is for saved scans. Discover shows banana-leaf disease information. LGU Connect shows how to reach your Local Government Unit.",
            "6. FLASH" to "Use the flash button when more light is needed.",
            "NOTE" to "The leaf analysis runs fully offline on this device."
        )

        steps.forEach { (header, body) ->
            val card = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_shadow_card)
                elevation = dp(2).toFloat()
                setPadding(dp(16), dp(14), dp(16), dp(14))
            }
            val headerView = TextView(requireContext()).apply {
                text = header
                textSize = 13f
                setTextColor(resources.getColor(R.color.leaf_green_dark, requireActivity().theme))
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD
                )
            }
            val bodyView = TextView(requireContext()).apply {
                text = body
                textSize = 14f
                setTextColor(resources.getColor(R.color.text_primary, requireActivity().theme))
                setLineSpacing(dp(4).toFloat(), 1f)
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.NORMAL
                )
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = dp(6) }
            }
            card.addView(headerView)
            card.addView(bodyView)
            root.addView(card, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(12) })
        }

        return androidx.core.widget.NestedScrollView(requireContext()).apply {
            addView(root)
        }
    }

    override fun onStart() {
        super.onStart()
        val sheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        sheet?.let {
            BottomSheetBehavior.from(it).apply {
                state = BottomSheetBehavior.STATE_EXPANDED
                skipCollapsed = true
            }
        }
    }
}
