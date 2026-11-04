package com.raymond.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import java.util.Locale

class RaymondListeningService : Service(), TextToSpeech.OnInitListener {
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private val handler = Handler(Looper.getMainLooper())
    private var stopped = false
    private var readyToProcess = true

    override fun onCreate() {
        super.onCreate()
        createChannel()
        tts = TextToSpeech(this, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopped = true
            stopListening()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizer = if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            } else {
                SpeechRecognizer.createSpeechRecognizer(this)
            }
            startListening()
        } else {
            speak("Speech recognition is not available on this phone.")
            stopSelf()
        }
        return START_STICKY
    }

    private fun startListening() {
        if (stopped) return
        val speech = recognizer ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        try {
            speech.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { updateNotification("Listening for Hey Raymond") }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) { retryListening(900) }
                override fun onResults(results: Bundle?) {
                    val phrase = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    handlePhrase(phrase)
                    retryListening(700)
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            speech.startListening(intent)
        } catch (_: Exception) { retryListening(1500) }
    }

    private fun handlePhrase(phrase: String) {
        if (!readyToProcess || phrase.isBlank()) return
        val normalized = phrase.lowercase(Locale.getDefault()).trim()
        val wake = normalized.contains("hey raymond") || normalized.startsWith("raymond")
        if (!wake) return
        readyToProcess = false
        val command = normalized.replace("hey raymond", "").replaceFirst(Regex("^raymond"), "").trim()
        if (command.isBlank()) {
            speak("I'm listening. What do you need?")
        } else {
            val localResponse = try { PhoneCommandRouter.handle(this, command) } catch (_: Exception) { "I couldn't complete that phone action." }
            if (localResponse != null) {
                speak(localResponse)
            } else {
                AssistantApi.ask(command) { result ->
                    val response = result.getOrElse { it.message ?: "I couldn't connect to the AI service." }
                    handler.post { if (!stopped) speak(response) }
                }
            }
        }
        handler.postDelayed({ readyToProcess = true }, 2500)
    }

    private fun retryListening(delay: Long) {
        if (!stopped) handler.postDelayed({ startListening() }, delay)
    }

    private fun speak(message: String) {
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "raymond_background_reply")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.language = Locale.getDefault()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "RAYMOND background assistant", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Visible status while RAYMOND is listening for its wake phrase"
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, RaymondListeningService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val openIntent = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("RAYMOND is active")
            .setContentText("Listening for “Hey Raymond”")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop listening", stopPending)
            .build()
    }

    private fun updateNotification(message: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification().apply { }
        )
    }

    private fun stopListening() {
        handler.removeCallbacksAndMessages(null)
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    override fun onDestroy() {
        stopped = true
        stopListening()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "raymond_assistant_channel"
        private const val NOTIFICATION_ID = 4410
        private const val ACTION_STOP = "com.raymond.assistant.STOP_LISTENING"
    }
}
