package com.raymond.assistant

import android.Manifest
import android.app.Activity
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
import android.graphics.Color
import android.graphics.Typeface
import android.view.ViewGroup
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private lateinit var statusView: TextView
    private lateinit var transcriptView: TextView
    private lateinit var listenButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        textToSpeech = TextToSpeech(this, this)
        buildScreen()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        } else {
            prepareSpeechRecognizer()
        }
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 40, 28, 32)
            setBackgroundColor(Color.rgb(7, 13, 25))
        }

        val title = TextView(this).apply {
            text = "R A Y M O N D"
            textSize = 27f
            setTextColor(Color.rgb(90, 220, 255))
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        root.addView(title, matchWrap())

        statusView = TextView(this).apply {
            text = "YOUR PERSONAL AI ASSISTANT"
            textSize = 12f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        val statusParams = matchWrap().apply { topMargin = 14 }
        root.addView(statusView, statusParams)

        val orb = TextView(this).apply {
            text = "◉"
            textSize = 104f
            setTextColor(Color.rgb(40, 190, 235))
            gravity = Gravity.CENTER
        }
        root.addView(orb, LinearLayout.LayoutParams(-1, 0, 1f))

        transcriptView = TextView(this).apply {
            text = "Tap the microphone and speak to Raymond."
            textSize = 17f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        root.addView(transcriptView, matchWrap())

        listenButton = Button(this).apply {
            text = "TALK TO RAYMOND"
            textSize = 15f
            setOnClickListener { startListening() }
        }
        val buttonParams = matchWrap().apply { topMargin = 28 }
        root.addView(listenButton, buttonParams)

        setContentView(root)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun prepareSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusView.text = "Speech recognition is unavailable on this device."
            listenButton.isEnabled = false
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).also { recognizer ->
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    statusView.text = "LISTENING..."
                }
                override fun onBeginningOfSpeech() {
                    statusView.text = "I'M LISTENING"
                }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    statusView.text = "PROCESSING..."
                }
                override fun onError(error: Int) {
                    statusView.text = "I couldn't hear that. Tap to try again."
                }
                override fun onResults(results: Bundle?) {
                    val words = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spoken = words?.firstOrNull().orEmpty()
                    transcriptView.text = spoken
                    respondTo(spoken)
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 100)
            return
        }
        if (speechRecognizer == null) prepareSpeechRecognizer()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Raymond")
        }
        statusView.text = "STARTING MICROPHONE..."
        speechRecognizer?.startListening(intent)
    }

    private fun respondTo(message: String) {
        val reply = when {
            message.isBlank() -> "I didn't catch that. Please try again."
            message.contains("hello", true) || message.contains("hi raymond", true) ->
                "Hello. Raymond is online. How can I help?"
            message.contains("your name", true) -> "I'm Raymond, your personal assistant."
            message.contains("time", true) ->
                "The time is " + java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(java.util.Date())
            else -> "I heard you say: $message. My AI connection will be added in the next development step."
        }
        transcriptView.text = reply
        statusView.text = "READY"
        textToSpeech?.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "raymond_reply")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.getDefault()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            prepareSpeechRecognizer()
            statusView.text = "READY"
        } else if (requestCode == 100) {
            statusView.text = "Microphone permission is needed for voice commands."
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onDestroy()
    }
}
