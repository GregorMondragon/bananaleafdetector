package com.thesis.bananaleaf.presentation.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

class BananaLeafTtsHelper(context: Context, onReady: (() -> Unit)? = null) {

    private var tts: TextToSpeech? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    var isReady: Boolean = false
        private set
    var isSpeaking: Boolean = false
        private set

    var onPlaybackStateChanged: ((Boolean) -> Unit)? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            isReady = status == TextToSpeech.SUCCESS
            if (isReady) {
                setupLanguageAndVoice()
                setupProgressListener()
                onReady?.invoke()
            }
        }
    }

    private fun setupLanguageAndVoice() {
        val engine = tts ?: return
        val tagalog = Locale("fil", "PH")
        val availability = engine.isLanguageAvailable(tagalog)
        if (availability >= TextToSpeech.LANG_AVAILABLE) {
            engine.language = tagalog
        } else {
            val fallbackTagalog = Locale("tl", "PH")
            if (engine.isLanguageAvailable(fallbackTagalog) >= TextToSpeech.LANG_AVAILABLE) {
                engine.language = fallbackTagalog
            } else {
                engine.language = Locale.getDefault()
            }
        }

        selectNaturalMaleVoice()

        // Apply deep male baritone pitch & clear pacing
        engine.setPitch(0.78f)
        engine.setSpeechRate(0.95f)
    }

    private fun selectNaturalMaleVoice() {
        val engine = tts ?: return
        val currentLang = engine.voice?.locale?.language ?: "fil"
        val voices = engine.voices

        if (!voices.isNullOrEmpty()) {
            val sameLanguage = voices.filter { it.locale.language.equals(currentLang, ignoreCase = true) }
            val candidatePool = if (sameLanguage.isNotEmpty()) sameLanguage else voices.toList()

            // In Google TTS and Android TTS:
            // Male voice identifiers: "male", "#male", "-m-", "fie", "fid", "gender=male"
            // Female voice identifiers: "female", "#female", "-f-", "fia", "fic", "gender=female"
            val maleCandidates = candidatePool.filter { voice ->
                val name = voice.name.lowercase(Locale.US)
                val features = voice.features?.joinToString(",")?.lowercase(Locale.US).orEmpty()
                val isExplicitlyFemale = name.contains("female") || name.contains("#female") ||
                        name.contains("-f-") || name.contains("fia") || name.contains("fic") ||
                        features.contains("gender=female") || features.contains("female")

                val isMale = name.contains("male") || name.contains("#male") ||
                        name.contains("-m-") || name.contains("fie") || name.contains("fid") ||
                        name.contains("man") || features.contains("gender=male") || features.contains("male")

                isMale && !isExplicitlyFemale
            }

            val bestMale = maleCandidates.firstOrNull() ?: candidatePool.firstOrNull { voice ->
                val name = voice.name.lowercase(Locale.US)
                !name.contains("female") && !name.contains("fia") && !name.contains("fic")
            }

            if (bestMale != null) {
                Log.d("BananaLeafTts", "Selected male TTS voice: ${bestMale.name}")
                engine.voice = bestMale
            }
        }

        // Unconditionally set deep masculine pitch
        engine.setPitch(0.78f)
        engine.setSpeechRate(0.95f)
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeaking = true
                mainHandler.post { onPlaybackStateChanged?.invoke(true) }
            }

            override fun onDone(utteranceId: String?) {
                isSpeaking = false
                mainHandler.post { onPlaybackStateChanged?.invoke(false) }
            }

            override fun onError(utteranceId: String?) {
                isSpeaking = false
                mainHandler.post { onPlaybackStateChanged?.invoke(false) }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                isSpeaking = false
                mainHandler.post { onPlaybackStateChanged?.invoke(false) }
            }
        })
    }

    fun speak(text: String) {
        if (!isReady || text.isBlank()) return
        val engine = tts ?: return
        engine.stop()

        // Ensure natural male baritone settings prior to synthesis
        engine.setPitch(0.78f)
        engine.setSpeechRate(0.95f)

        val params = android.os.Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "leaf_result_utterance")
        isSpeaking = true
        mainHandler.post { onPlaybackStateChanged?.invoke(true) }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, "leaf_result_utterance")
    }

    fun stop() {
        tts?.stop()
        isSpeaking = false
        mainHandler.post { onPlaybackStateChanged?.invoke(false) }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        isSpeaking = false
    }
}
