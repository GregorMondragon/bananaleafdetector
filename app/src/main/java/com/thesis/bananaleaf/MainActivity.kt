package com.thesis.bananaleaf

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.thesis.bananaleaf.databinding.ActivityMainBinding
import com.thesis.bananaleaf.presentation.discover.DiscoverFragment
import com.thesis.bananaleaf.presentation.garden.GardenFragment
import com.thesis.bananaleaf.presentation.home.HomeFragment
import com.thesis.bananaleaf.presentation.lgu.LguConnectFragment
import org.opencv.android.OpenCVLoader

/**
 * MainActivity — navigation host.
 *
 * Manages 4 Fragment tabs: Home, My Garden, Discover, LGU Connect.
 *
 * CRITICAL: HomeFragment is ADDED once and shown/hidden — never replaced.
 * This preserves the CameraX lifecycle across tab switches so the camera
 * never restarts when the user navigates away and back.
 *
 * Other fragments (Garden, Discover, LGU) are added lazily on first visit
 * and then shown/hidden the same way.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val homeFragment = HomeFragment()
    private val gardenFragment by lazy { GardenFragment() }
    private val discoverFragment by lazy { DiscoverFragment() }
    private val lguFragment by lazy { LguConnectFragment() }

    private var activeTab = R.id.nav_home

    // ────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge: let content draw behind system bars naturally
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize OpenCV
        if (!OpenCVLoader.initLocal()) {
            android.widget.Toast.makeText(this, R.string.opencv_init_failed,
                android.widget.Toast.LENGTH_LONG).show()
        }

        if (savedInstanceState == null) {
            // Add HomeFragment and pre-add other tabs as hidden so views are ready
            // and animations behave consistently across all pages.
            supportFragmentManager.beginTransaction()
                .add(R.id.fragmentContainer, homeFragment, "home")
                .add(R.id.fragmentContainer, gardenFragment, "garden").hide(gardenFragment)
                .add(R.id.fragmentContainer, discoverFragment, "discover").hide(discoverFragment)
                .add(R.id.fragmentContainer, lguFragment, "lgu").hide(lguFragment)
                .commit()
        }

        // Dynamically adjust statusBarScrim height to match the system status bar
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBarHeight = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top
            val lp = binding.statusBarScrim.layoutParams
            if (lp.height != statusBarHeight) {
                lp.height = statusBarHeight
                binding.statusBarScrim.layoutParams = lp
            }
            insets
        }

        setupBottomNavigation()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val targetTab = intent?.getIntExtra("TARGET_TAB", -1) ?: -1
        if (targetTab in 0..3) {
            navigateTo(targetTab)
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Navigation
    // ────────────────────────────────────────────────────────────────────────

    private fun setupBottomNavigation() {
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            val targetIndex = when (item.itemId) {
                R.id.nav_home -> 0
                R.id.nav_garden -> 1
                R.id.nav_discover -> 2
                R.id.nav_lgu -> 3
                else -> 0
            }
            if (item.itemId != activeTab) {
                navigateToFragment(item.itemId, targetIndex)
                activeTab = item.itemId
            }
            true
        }
        // Set Home as the initial selected item and apply dark glass theme
        updateBottomNavTheme(R.id.nav_home)
        binding.bottomNavigationView.selectedItemId = R.id.nav_home
    }

    private fun updateBottomNavTheme(targetItemId: Int) {
        if (targetItemId == R.id.nav_home) {
            binding.statusBarScrim.visibility = android.view.View.GONE
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            binding.root.setBackgroundColor(android.graphics.Color.BLACK)
            binding.bottomNavigationView.setBackgroundResource(R.drawable.bg_nav_glassmorphic_dark)
            binding.bottomNavigationView.itemIconTintList =
                androidx.core.content.ContextCompat.getColorStateList(this, R.color.nav_icon_selector_dark)
            binding.bottomNavigationView.itemTextColor =
                androidx.core.content.ContextCompat.getColorStateList(this, R.color.nav_icon_selector_dark)
            // Let the BottomNavActiveIndicatorDark style control indicator shape + color
            binding.bottomNavigationView.itemActiveIndicatorColor =
                androidx.core.content.ContextCompat.getColorStateList(this, R.color.nav_indicator_dark)
        } else {
            binding.statusBarScrim.visibility = android.view.View.VISIBLE
            binding.statusBarScrim.setBackgroundColor(
                androidx.core.content.ContextCompat.getColor(this, R.color.leaf_green)
            )
            window.statusBarColor = androidx.core.content.ContextCompat.getColor(this, R.color.leaf_green)
            binding.root.setBackgroundColor(
                androidx.core.content.ContextCompat.getColor(this, R.color.background_main)
            )
            binding.bottomNavigationView.setBackgroundResource(R.drawable.bg_nav_glassmorphic_light)
            binding.bottomNavigationView.itemIconTintList =
                androidx.core.content.ContextCompat.getColorStateList(this, R.color.nav_icon_selector)
            binding.bottomNavigationView.itemTextColor =
                androidx.core.content.ContextCompat.getColorStateList(this, R.color.nav_icon_selector)
            binding.bottomNavigationView.itemActiveIndicatorColor =
                androidx.core.content.ContextCompat.getColorStateList(this, R.color.nav_indicator_light)
        }
    }

    /**
     * Exposed so fragments can programmatically switch tabs.
     * @param index 0=Home, 1=Garden, 2=Discover, 3=LGU
     */
    fun navigateTo(index: Int) {
        val itemId = when (index) {
            0 -> R.id.nav_home
            1 -> R.id.nav_garden
            2 -> R.id.nav_discover
            3 -> R.id.nav_lgu
            else -> R.id.nav_home
        }
        binding.bottomNavigationView.selectedItemId = itemId
    }

    private fun navigateToFragment(targetItemId: Int, targetIndex: Int) {
        val targetFragment: Fragment = when (targetItemId) {
            R.id.nav_home -> homeFragment
            R.id.nav_garden -> gardenFragment
            R.id.nav_discover -> discoverFragment
            R.id.nav_lgu -> lguFragment
            else -> homeFragment
        }
        val currentFragment: Fragment = when (activeTab) {
            R.id.nav_home -> homeFragment
            R.id.nav_garden -> gardenFragment
            R.id.nav_discover -> discoverFragment
            R.id.nav_lgu -> lguFragment
            else -> homeFragment
        }

        if (targetFragment == currentFragment) return

        // 100% opaque Subtle Spring Zoom (Scale Settle) animation with 0 fade
        val enterAnim = R.anim.page_enter_scale_settle

        val allFragments = listOf(homeFragment, gardenFragment, discoverFragment, lguFragment)
        val transaction = supportFragmentManager.beginTransaction()
        for (frag in allFragments) {
            if (frag != targetFragment && frag != currentFragment && frag.isAdded) {
                transaction.hide(frag)
            }
        }

        if (targetFragment.isAdded) {
            transaction.show(targetFragment)
        } else {
            val tag = when (targetItemId) {
                R.id.nav_home -> "home"
                R.id.nav_garden -> "garden"
                R.id.nav_discover -> "discover"
                R.id.nav_lgu -> "lgu"
                else -> "home"
            }
            transaction.add(R.id.fragmentContainer, targetFragment, tag)
        }
        transaction.commit()

        targetFragment.view?.let { v ->
            v.visibility = android.view.View.VISIBLE
            v.bringToFront()
            val anim = android.view.animation.AnimationUtils.loadAnimation(this, enterAnim)
            anim.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
                override fun onAnimationStart(animation: android.view.animation.Animation?) {}
                override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
                override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                    if (currentFragment.isAdded && currentFragment != targetFragment) {
                        supportFragmentManager.beginTransaction().hide(currentFragment).commitAllowingStateLoss()
                    }
                }
            })
            v.startAnimation(anim)
        } ?: run {
            if (currentFragment.isAdded) {
                supportFragmentManager.beginTransaction().hide(currentFragment).commitAllowingStateLoss()
            }
        }

        updateBottomNavTheme(targetItemId)
    }
}
