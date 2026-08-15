package com.nova.assistant

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession.LlmInferenceSessionOptions
import java.io.File

/**
 * NovaBrain — runs entirely ON-DEVICE. No API key, no internet, no cost.
 *
 * Uses Google's MediaPipe LLM Inference API (com.google.mediapipe:tasks-genai) to run
 * a local Gemma 3 1B model file (~555MB .task, NOT the "-web" variant — that's a
 * different build meant for browsers, not this Android library).
 *
 * Why Gemma 3 1B and not a newer Gemma model: Google has put the MediaPipe LLM
 * Inference API used here into "maintenance-only mode" and newer model releases
 * (Gemma 4, Gemma 3n) ship primarily as .litertlm files for the newer, separate
 * LiteRT-LM library — not this one. Gemma 3 1B is the largest/newest model still
 * confirmed to ship a genuine (non-web) .task file compatible with this exact
 * dependency. Migrating to LiteRT-LM would mean a different Gradle dependency and
 * a different Kotlin API — a bigger change than a file swap, so it's intentionally
 * not done here. See README: "Downloading the model" for the exact download link.
 *
 * API shape (this matters — getting it wrong is what broke the last build):
 * - LlmInference (the "engine") is created ONCE from the model file. It only
 *   accepts engine-level options: model path, max tokens, and setMaxTopK()
 *   (which just reserves how large top-k is allowed to be for any session).
 * - Actual sampling controls — setTopK(), setTemperature() — belong to a
 *   separate LlmInferenceSession, created per conversation turn. Mixing these
 *   up (calling setTopK on the engine options) is an unresolved-reference
 *   compile error, not a runtime one — that's exactly what happened before.
 *
 * Tradeoff vs. cloud (documented honestly, not hidden):
 * - Free forever, fully private, works with no signal / airplane mode
 * - Noticeably less sharp than Claude on complex, multi-step reasoning
 * - Cannot know live information (news, weather, current events) — see
 *   NEEDS_INTERNET_KEYWORDS below, where Nova says so instead of guessing
 */
class NovaBrain(private val context: Context) {

    companion object { private const val TAG = "NovaBrain" }

    private var llmInference: LlmInference? = null
    private var modelReady = false
    val isReady: Boolean get() = modelReady

    /** Set whenever initialize() or ask() fails, so the UI can show the REAL reason
     *  instead of a generic message. Never silently swallowed — always logged too. */
    var lastError: String? = null
        private set

    // Model file location on the phone. Defaults here, but can be overridden by
    // copyPickedModelFile() if the user selects it via the in-app file picker
    // instead of pushing it with adb (phone-only setups need this path).
    private var modelPath = "${context.getExternalFilesDir(null)}/models/gemma3-1b-it-int4.task"

    /** Call this after the user picks the downloaded .task file via Storage Access Framework.
     *  Copies it into the app's own storage so it persists and MediaPipe can read it directly. */
    fun copyPickedModelFile(uri: android.net.Uri, onDone: (Boolean) -> Unit) {
        try {
            val destDir = File("${context.getExternalFilesDir(null)}/models")
            if (!destDir.exists()) destDir.mkdirs()
            val destFile = File(destDir, "gemma3-1b-it-int4.task")
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            modelPath = destFile.absolutePath
            onDone(true)
        } catch (e: Exception) {
            lastError = "Couldn't copy the picked file: ${e.javaClass.simpleName} — ${e.message ?: "no further detail"}"
            Log.e(TAG, "copyPickedModelFile() failed", e)
            onDone(false)
        }
    }

    private val systemPrompt = """
        You are Nova, a calm, warm, precise personal voice assistant.
        Keep replies short (1-3 sentences) since they are spoken aloud, not read.
        Speak English, Hindi, or Hinglish depending on how the user speaks to you.
        Never claim to have done something you weren't actually told was executed.
    """.trimIndent()

    // Topics the local model can't answer accurately because it has no live data access.
    // Rather than let it guess/hallucinate a "current" answer, Nova is honest about the gap.
    private val needsInternetKeywords = listOf(
        "weather", "today's news", "latest news", "current price", "score today",
        "kal ka mausam", "aaj ka mausam"
    )

    fun initialize(onReady: (Boolean) -> Unit) {
        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            lastError = "Model file not found at $modelPath. Download the .task file and load it via 'Load AI Model', or push it there via adb — see README."
            Log.w(TAG, lastError!!)
            onReady(false)
            return
        }
        try {
            // Engine: created once, kept alive for the app's lifetime.
            val engineOptions = LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(512)
                .setMaxTopK(64)   // reserves capacity; the actual top-k value is set per-session below
                .build()
            llmInference = LlmInference.createFromOptions(context, engineOptions)
            modelReady = true
            lastError = null
            onReady(true)
        } catch (e: Exception) {
            modelReady = false
            lastError = "Model failed to load: ${e.javaClass.simpleName} — ${e.message ?: "no further detail from MediaPipe"}"
            Log.e(TAG, "initialize() failed", e)
            onReady(false)
        }
    }

    fun ask(userText: String, contextBlock: String, onReply: (String) -> Unit) {
        val engine = llmInference
        if (!modelReady || engine == null) {
            onReply("My local AI model isn't loaded yet — check the README's model setup step.")
            return
        }

        val lower = userText.lowercase()
        if (needsInternetKeywords.any { lower.contains(it) }) {
            onReply("I can't check that live — I run fully offline. Connect me to a weather/news source later if you want that added.")
            return
        }

        var session: LlmInferenceSession? = null
        try {
            val prompt = buildString {
                append(systemPrompt)
                if (contextBlock.isNotBlank()) {
                    append("\n\n")
                    append(contextBlock)
                }
                append("\n\nUser: $userText\nNova:")
            }

            // Session: one per question, so each call is stateless from MediaPipe's side —
            // Nova's own MemoryManager (contextBlock, passed in above) is what supplies
            // conversation history, not MediaPipe's built-in session memory. This avoids
            // context being tracked twice in two different places.
            val sessionOptions = LlmInferenceSessionOptions.builder()
                .setTopK(40)
                .setTemperature(0.7f)
                .build()
            session = LlmInferenceSession.createFromOptions(engine, sessionOptions)
            session.addQueryChunk(prompt)
            val result = session.generateResponse()
            lastError = null
            onReply(result.trim())
        } catch (e: Exception) {
            lastError = "ask() failed: ${e.javaClass.simpleName} — ${e.message ?: "no further detail"}"
            Log.e(TAG, "ask() failed", e)
            onReply("I had trouble thinking that through — could you try rephrasing?")
        } finally {
            session?.close()
        }
    }

    fun shutdown() {
        llmInference?.close()
    }
}
