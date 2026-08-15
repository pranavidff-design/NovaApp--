package com.nova.assistant

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * Wraps text-to-speech so the rest of the app never talks to a TTS provider directly.
 * Part 1: Android's built-in TTS, tuned toward the "calm, slightly low, unhurried" voice
 * described in the spec. Swapping in a premium engine (e.g. ElevenLabs) later means
 * rewriting only this file — nothing else in the app needs to change.
 */
class NovaVoiceEngine(context: Context) {

    companion object { private const val TAG = "NovaVoiceEngine" }

    private var tts: TextToSpeech? = null
    private var ready = false

    // If speak() is called before TTS finishes initializing (a real race — init is async),
    // queue the text instead of silently dropping it. This was previously a silent no-op.
    private var pendingText: String? = null

    var onInitFailed: ((String) -> Unit)? = null

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setPitch(0.92f)   // slightly lower pitch, per spec
                tts?.setSpeechRate(0.93f) // slightly slower, calm delivery
                pickFemaleVoice()
                ready = true
                pendingText?.let { text ->
                    pendingText = null
                    speak(text)
                }
            } else {
                Log.e(TAG, "TextToSpeech init failed with status=$status")
                onInitFailed?.invoke("Text-to-speech engine failed to initialize (status=$status). Nova can still understand you, but can't speak replies aloud.")
            }
        }
    }

    private fun pickFemaleVoice() {
        val voices = tts?.voices ?: return
        val preferred = voices.firstOrNull {
            it.name.contains("female", ignoreCase = true) && it.locale.language == "en"
        }
        preferred?.let { tts?.voice = it }
    }

    fun speak(text: String) {
        if (!ready) {
            Log.w(TAG, "speak() called before TTS was ready — queuing: \"$text\"")
            pendingText = text
            return
        }
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nova_utterance")
        if (result == TextToSpeech.ERROR) {
            Log.e(TAG, "TTS speak() returned ERROR for text: \"$text\"")
        }
    }

    /** Stops mid-sentence — backs the "interruptible speech" requirement from the spec. */
    fun stopSpeaking() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
