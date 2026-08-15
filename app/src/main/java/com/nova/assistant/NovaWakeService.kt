package com.nova.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.nova.assistant.memory.MemoryManager

/**
 * Real Android foreground service — this is what lets "Hey Nova" keep working
 * when the app isn't the one on screen, which a plain Activity-owned listener
 * can never do (Android suspends an Activity's work almost immediately once
 * it's not visible).
 *
 * HONEST LIMITS (documented, not hidden):
 * - This is foreground-while-requested, not truly infinite background. If the
 *   user force-stops the app or swipes it away from Recents, most Android
 *   OEMs (Xiaomi/MIUI, Oppo/ColorOS, Vivo, and some OnePlus/Samsung battery
 *   modes especially) will still kill it despite the foreground-service API
 *   being used correctly — that's an OEM battery-management decision Nova
 *   cannot override, not a bug in this code.
 * - It requires a persistent, visible notification the whole time it runs —
 *   that's an Android platform requirement for any microphone-using
 *   foreground service (not optional, not something we can hide).
 * - It still uses Android's cloud/on-device SpeechRecognizer in a loop, the
 *   same engine tap-to-talk uses — it is NOT a specialized low-power wake-word
 *   chip/engine (like Picovoice), so it uses meaningfully more battery than a
 *   dedicated wake-word engine would. Swapping in a dedicated engine later is
 *   a bigger change than this file; this is the real, working V1.
 */
class NovaWakeService : LifecycleService() {

    companion object {
        private const val CHANNEL_ID = "nova_wake_channel"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_STOP = "com.nova.assistant.action.STOP_WAKE"

        /** Lets MainActivity re-sync its "Wake word: ON/OFF" button after being
         *  recreated (rotation, low-memory recreation) while the service was
         *  already running in the background — otherwise the button could show
         *  OFF while the service was actually still listening. */
        var isRunning = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, NovaWakeService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, NovaWakeService::class.java).setAction(ACTION_STOP))
        }
    }

    private lateinit var memory: MemoryManager
    private lateinit var brain: NovaBrain
    private lateinit var voiceEngine: NovaVoiceEngine
    private lateinit var processor: CommandProcessor
    private var wakeWordListener: WakeWordListener? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as NovaApp
        memory = app.memory
        voiceEngine = app.voiceEngine
        brain = app.brain
        // Safe even if MainActivity already started this — only loads once (see NovaApp).
        app.ensureBrainInitialized { /* if it fails, ask() will just say so — handled per-question */ }

        processor = CommandProcessor(
            context = this,
            memory = memory,
            brain = brain,
            voiceEngine = voiceEngine,
            scope = lifecycleScope,
            onLog = { who, text -> NovaEventBus.log(who, text) },
            onStatus = { status -> NovaEventBus.status(status) },
            requestApproval = { actionLabel, onDecision ->
                SensitiveActionReceiver.requestApproval(this, actionLabel, onDecision)
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            wakeWordListener?.stop()
            isRunning = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        isRunning = true
        startForegroundWithNotification("Listening for \"Hey Nova\"…")
        startWakeListening()
        return START_STICKY
    }

    private fun startWakeListening() {
        if (wakeWordListener != null) return
        wakeWordListener = WakeWordListener(
            context = this,
            onWakeDetected = { updateNotification("Yes? I'm listening…") },
            onCommandHeard = { command ->
                NovaEventBus.log("You", command)
                processor.handle(command)
                updateNotification("Listening for \"Hey Nova\"…")
            },
            onFatalError = { reason ->
                NovaEventBus.log("System", reason)
                updateNotification(reason)
                stopSelf()
            }
        )
        wakeWordListener?.start()
    }

    private fun startForegroundWithNotification(text: String) {
        ensureChannel()
        val notification = buildNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(text: String): Notification {
        val stopPendingIntent = PendingIntent.getService(
            this, 0,
            Intent(this, NovaWakeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Nova")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Stop", stopPendingIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Nova wake word", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onDestroy() {
        // brain and voiceEngine are shared (owned by NovaApp) — NOT shut down here,
        // since MainActivity may still be using them in the foreground.
        isRunning = false
        wakeWordListener?.stop()
        super.onDestroy()
    }
}
