package com.raymond.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private lateinit var statusView: TextView
    private lateinit var transcriptView: TextView
    private lateinit var listenButton: Button

    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening() else statusView.text = "Microphone permission is needed for voice input."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        textToSpeech = TextToSpeech(this, this)
        buildInterface()
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        } else {
            statusView.text = "Speech recognition is not available on this device."
            listenButton.isEnabled = false
        }
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 40, 28, 32)
            setBackgroundColor(android.graphics.Color.rgb(8, 13, 24))
        }
        val title = TextView(this).apply {
            text = "R A Y M O N D"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.rgb(0, 217, 255))
        }
        statusView = TextView(this).apply {
            text = "PERSONAL ASSISTANT • READY"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.LTGRAY)
        }
        transcriptView = TextView(this).apply {
            text = "Tap the microphone and speak a command."
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            setPadding(8, 36, 8, 36)
        }
        listenButton = Button(this).apply {
            text = "TALK TO RAYMOND"
            setOnClickListener {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    startListening()
                } else audioPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        val note = TextView(this).apply {
            text = "VOICE INTERFACE • EARLY BUILD\nAI connection and phone actions will be added in later stages."
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.GRAY)
            setPadding(12, 36, 12, 0)
        }
        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        root.addView(statusView, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 12 })
        root.addView(transcriptView, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 32 })
        root.addView(listenButton, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 16 })
        root.addView(note, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
    }

    private fun startListening() {
        val recognizer = speechRecognizer ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to RAYMOND")
        }
        statusView.text = "LISTENING…"
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { statusView.text = "LISTENING…" }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { statusView.text = "PROCESSING…" }
            override fun onError(error: Int) { statusView.text = "Could not hear that. Tap to try again." }
            override fun onResults(results: Bundle?) {
                val words = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: "I didn't catch that."
                transcriptView.text = "You said:\n$words"
                speak("I heard you say: $words. My AI connection is not set up yet.")
                statusView.text = "VOICE INPUT RECEIVED"
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer.startListening(intent)
    }

    private fun speak(message: String) {
        textToSpeech?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "raymond_reply")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) textToSpeech?.language = Locale.getDefault()
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        textToSpeech?.shutdown()
        super.onDestroy()
    }
}
