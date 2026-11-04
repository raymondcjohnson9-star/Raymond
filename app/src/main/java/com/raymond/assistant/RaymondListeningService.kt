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
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import java.util.Locale

class RaymondListeningService : Service(), TextToSpeech.OnInitListener {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null
    private var stopped = false
    private var isListening = false
    private var isSpeaking = false
    private var readyToProcess = true
    private var retryDelayMs = INITIAL_RETRY_MS

    private val restartRunnable = Runnable {
        if (!stopped && !isSpeaking) startListening()
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        tts = TextToSpeech(this, this)
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeaking = true
                updateNotification("RAYMOND is speaking")
            }

            override fun onDone(utteranceId: String?) {
                handler.post {
                    isSpeaking = false
                    readyToProcess = true
                    scheduleListening(450)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                handler.post {
                    isSpeaking = false
                    readyToProcess = true
                    scheduleListening(700)
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                onError(utteranceId)
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAssistant()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification("Starting voice assistant"))
        if (recognizer == null) createRecognizer()
        if (recognizer == null) {
            updateNotification("Speech recognition is unavailable")
            stopSelf()
            return START_NOT_STICKY
        }
        scheduleListening(250)
        return START_STICKY
    }

    private fun createRecognizer() {
        if (stopped || !SpeechRecognizer.isRecognitionAvailable(this)) return
        try {
            // Use Android's configured recognition provider for broader device compatibility.
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer?.setRecognitionListener(recognitionListener)
        } catch (_: Exception) {
            recognizer = null
        }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            isListening = true
            retryDelayMs = INITIAL_RETRY_MS
            updateNotification("Listening for Hey Raymond")
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            isListening = false
            updateNotification("Processing speech")
        }

        override fun onError(error: Int) {
            isListening = false
            if (stopped || isSpeaking) return

            // A fresh recognizer instance recovers more reliably from client/server errors.
            resetRecognizer()
            val delay = when (error) {
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> retryDelayMs.coerceAtLeast(1800)
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER,
                SpeechRecognizer.ERROR_CLIENT -> retryDelayMs.coerceAtLeast(1200)
                else -> retryDelayMs
            }
            retryDelayMs = (retryDelayMs * 2).coerceAtMost(MAX_RETRY_MS)
            scheduleListening(delay)
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            retryDelayMs = INITIAL_RETRY_MS
            val phrase = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            handlePhrase(phrase)
        }

        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun startListening() {
        if (stopped || isSpeaking || isListening) return
        if (recognizer == null) createRecognizer()
        val speech = recognizer ?: run {
            scheduleListening(MAX_RETRY_MS)
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            speech.startListening(intent)
            updateNotification("Starting microphone")
        } catch (_: SecurityException) {
            updateNotification("Microphone permission is required")
            stopAssistant()
        } catch (_: Exception) {
            isListening = false
            resetRecognizer()
            scheduleListening(retryDelayMs)
            retryDelayMs = (retryDelayMs * 2).coerceAtMost(MAX_RETRY_MS)
        }
    }

    private fun handlePhrase(phrase: String) {
        if (stopped || phrase.isBlank()) {
            scheduleListening(350)
            return
        }

        val normalized = phrase.lowercase(Locale.getDefault()).trim()
        val wakePhraseFound = normalized.contains("hey raymond") || normalized.startsWith("raymond")
        if (!wakePhraseFound || !readyToProcess) {
            scheduleListening(350)
            return
        }

        readyToProcess = false
        handler.removeCallbacks(restartRunnable)
        updateNotification("RAYMOND heard you")

        val command = normalized
            .replace("hey raymond", "")
            .replaceFirst(Regex("^raymond"), "")
            .trim()

        if (command.isBlank()) {
            speak("I'm listening. What do you need?")
            return
        }

        val localResponse = try {
            PhoneCommandRouter.handle(this, command)
        } catch (_: Exception) {
            "I couldn't complete that phone action."
        }

        if (localResponse != null) {
            speak(localResponse)
        } else {
            updateNotification("RAYMOND is thinking")
            AssistantApi.ask(command) { result ->
                handler.post {
                    if (!stopped) {
                        val response = result.getOrElse {
                            it.message ?: "I couldn't connect to the AI service."
                        }
                        speak(response)
                    }
                }
            }
        }
    }

    private fun speak(message: String) {
        if (stopped) return
        if (!ttsReady) {
            pendingSpeech = message
            updateNotification("Preparing voice response")
            return
        }

        isSpeaking = true
        isListening = false
        try {
            recognizer?.cancel()
            val result = tts?.speak(
                message,
                TextToSpeech.QUEUE_FLUSH,
                null,
                UTTERANCE_ID
            )
            if (result == TextToSpeech.ERROR) {
                isSpeaking = false
                readyToProcess = true
                scheduleListening(800)
            }
        } catch (_: Exception) {
            isSpeaking = false
            readyToProcess = true
            scheduleListening(1000)
        }
    }

    private fun scheduleListening(delayMs: Long) {
        if (stopped || isSpeaking) return
        handler.removeCallbacks(restartRunnable)
        handler.postDelayed(restartRunnable, delayMs)
    }

    private fun resetRecognizer() {
        isListening = false
        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {
        }
        recognizer = null
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val languageResult = tts?.setLanguage(Locale.getDefault())
            ttsReady = languageResult != TextToSpeech.LANG_MISSING_DATA &&
                languageResult != TextToSpeech.LANG_NOT_SUPPORTED
            val queued = pendingSpeech
            pendingSpeech = null
            if (ttsReady && queued != null) speak(queued)
            else if (!ttsReady) scheduleListening(500)
        } else {
            ttsReady = false
            scheduleListening(1000)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "RAYMOND background assistant",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Visible status while RAYMOND listens for its wake phrase"
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String): Notification {
        val stopIntent = Intent(this, RaymondListeningService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("RAYMOND is active")
            .setContentText(message)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop listening", stopPending)
            .build()
    }

    private fun updateNotification(message: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(message))
    }

    private fun stopAssistant() {
        if (stopped) return
        stopped = true
        handler.removeCallbacksAndMessages(null)
        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {
        }
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopped = true
        handler.removeCallbacksAndMessages(null)
        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {
        }
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "raymond_assistant_channel"
        private const val NOTIFICATION_ID = 4410
        private const val ACTION_STOP = "com.raymond.assistant.STOP_LISTENING"
        private const val UTTERANCE_ID = "raymond_background_reply"
        private const val INITIAL_RETRY_MS = 650L
        private const val MAX_RETRY_MS = 8000L
    }
}
