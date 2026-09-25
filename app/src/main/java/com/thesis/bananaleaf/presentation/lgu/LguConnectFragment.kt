package com.thesis.bananaleaf.presentation.lgu

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.thesis.bananaleaf.databinding.FragmentLguConnectBinding

/**
 * LguConnectFragment — full-screen, opaque fragment for the LGU Connect tab.
 *
 * Static content screen with actionable contact buttons (call / email / Facebook).
 * No ViewModel needed.
 */
class LguConnectFragment : Fragment() {

    private var _binding: FragmentLguConnectBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLguConnectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Adjust top padding to cleanly accommodate the green status bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.lguAppBar) { v, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.updatePadding(top = statusBarHeight)
            insets
        }
        ViewCompat.requestApplyInsets(binding.lguAppBar)

        setupContactButtons()
    }

    private fun setupContactButtons() {
        binding.lguCallButton.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_DIAL,
                    Uri.parse("tel:+63437404980"))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(),
                    "Unable to open phone app.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.lguEmailButton.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:agri@batangas.gov.ph")
                    putExtra(Intent.EXTRA_SUBJECT, "Banana Leaf Disease Inquiry")
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(),
                    "Unable to open email app.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.lguFacebookButton.setOnClickListener {
            try {
                // Try Facebook app first, fall back to browser
                val fbAppUri = Uri.parse("fb://page/OPABatangasOfficial")
                val fbWebUri = Uri.parse("https://facebook.com/OPABatangasOfficial")
                val intent = try {
                    Intent(Intent.ACTION_VIEW, fbAppUri).also {
                        requireContext().packageManager.getPackageInfo("com.facebook.katana", 0)
                    }
                } catch (e: Exception) {
                    Intent(Intent.ACTION_VIEW, fbWebUri)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(),
                    "Unable to open Facebook.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
