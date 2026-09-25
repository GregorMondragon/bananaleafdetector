package com.thesis.bananaleaf

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

@Suppress("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val scanLine = findViewById<View>(R.id.scanLine)
        val logoContainer = findViewById<View>(R.id.logoContainer)
        val logoImage = findViewById<ImageView>(R.id.logoImage)
        val appName = findViewById<TextView>(R.id.splashAppName)

        logoContainer.post {
            // Scan line — smooth up-and-down sweep
            val scanAnimator = ObjectAnimator.ofFloat(
                scanLine, "translationY",
                0f, logoContainer.height.toFloat()
            ).apply {
                duration = 1200
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
            }

            // Logo pulse — subtle alpha breath
            val flashAnimator = ObjectAnimator.ofFloat(
                logoImage, "alpha", 1f, 0.55f
            ).apply {
                duration = 600
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
            }

            // Logo scale — enters with subtle grow from 0.88
            val scaleXIn = ObjectAnimator.ofFloat(logoImage, "scaleX", 0.88f, 1f).apply { duration = 420 }
            val scaleYIn = ObjectAnimator.ofFloat(logoImage, "scaleY", 0.88f, 1f).apply { duration = 420 }

            val entrySet = AnimatorSet().apply { playTogether(scaleXIn, scaleYIn) }
            entrySet.start()

            // Start scanning animations
            AnimatorSet().apply { playTogether(scanAnimator, flashAnimator) }.start()

            // App name fades in at 700ms
            Handler(Looper.getMainLooper()).postDelayed({
                appName?.let {
                    it.visibility = View.VISIBLE
                    ObjectAnimator.ofFloat(it, "alpha", 0f, 1f).apply {
                        duration = 400
                    }.start()
                }
            }, 700)
        }

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            finish()
        }, 2500)
    }
}
