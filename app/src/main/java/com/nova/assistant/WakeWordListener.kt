package com.nova.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Listens continuously for "Hey Nova" while this is active, and hands off the
 * command that follows. This only works while Nova's app is in the foreground —
 * true background wake-word detection needs a dedicated engine (e.g. Picovoice)
 * running as a foreground service, which is a later-phase upgrade, same as the
 * original spec listed it (V1 = mic button, background service = later).
 *
 * IMPORTANT: on error this used to retry instantly with no limit, which meant
 * a permanently-broken mic/recognizer would spin silently forever (battery
 * drain, "Wake word: ON" that visibly does nothing). It now backs off between
 * retries and gives up with a real, visible reason after repeated failures.
 */
class WakeWordListener(
    private val context: Context,
    private val onWakeDetected: () -> Unit,
    private val onCommandHeard: (String) -> Unit,
    private val onFatalError: (String) -> Unit = {}
) {
    companion object {
        private const val TAG = "WakeWordListener"
        private const val RETRY_DELAY_MS = 600L
        private const val MAX_CONSECUTIVE_ERRORS = 5
    }

    private var recognizer: SpeechRecognizer? = null
    private var isActive = false
    private var awake = false
    private var consecutiveErrors = 0
    private val handler = Handler(Looper.getMainLooper())

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onFatalError("This device has no speech recognition service available — wake word can't run. Try tap-to-talk instead.")
            return
        }
        if (isActive) return
        isActive = true
        consecutiveErrors = 0
        listenOnce()
    }

    fun stop() {
        isActive = false
        awake = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
    }

    private fun listenOnce() {
        if (!isActive) return
        recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                consecutiveErrors = 0
                val heard = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                val lower = heard.lowercase()

                if (!awake && (lower.contains("hey nova") || lower.contains("hi nova"))) {
                    awake = true
                    onWakeDetected()
                    val remainder = lower.replace(Regex("hey nova|hi nova"), "").trim()
                    if (remainder.length > 2) {
                        awake = false
                        onCommandHeard(remainder)
                    }
                } else if (awake) {
                    awake = false
                    onCommandHeard(heard)
                }
                restart(immediate = true)
            }

            override fun onError(error: Int) {
                Log.w(TAG, "wake-word recognizer error code=$error")
                restart(immediate = false)
            }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "startListening() threw", e)
            restart(immediate = false)
        }
    }

    private fun restart(immediate: Boolean) {
        recognizer?.destroy()
        if (!isActive) return

        if (!immediate) {
            consecutiveErrors++
            if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                Log.e(TAG, "giving up after $consecutiveErrors consecutive recognizer errors")
                isActive = false
                onFatalError("Wake word stopped after repeated microphone/recognizer errors. Check mic permission, or use tap-to-talk instead.")
                return
            }
        }
        handler.postDelayed({ listenOnce() }, if (immediate) 250L else RETRY_DELAY_MS)
    }
}
