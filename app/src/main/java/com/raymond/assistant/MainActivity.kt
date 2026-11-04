package com.raymond.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private lateinit var status: TextView
    private lateinit var transcript: TextView
    private lateinit var character: AiCharacterView
    private val handler = Handler(Looper.getMainLooper())
    private var sessionActive = false
    private var isListening = false
    private var isSpeaking = false
    private var ttsReady = false
    private var retryDelay = 700L
    private var pendingSpeech: String? = null

    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startConversation()
        else status.text = "MICROPHONE PERMISSION REQUIRED"
    }
    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scanImage(uri)
    }

    private val retryListening = Runnable {
        if (sessionActive && !isSpeaking && !isFinishing) startListening()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        buildInterface()
        createRecognizer()
    }

    private fun buildInterface() {
        window.statusBarColor = Color.rgb(2, 6, 18)
        window.navigationBarColor = Color.rgb(2, 6, 18)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LOW_PROFILE

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(22, 26, 22, 20)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(2, 5, 17), Color.rgb(5, 13, 31), Color.rgb(1, 4, 14))
            )
        }

        val title = TextView(this).apply {
            text = "R A Y M O N D"
            textSize = 23f
            letterSpacing = 0.22f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(175, 247, 255))
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setShadowLayer(25f, 0f, 0f, Color.rgb(0, 190, 255))
        }
        val subtitle = TextView(this).apply {
            text = "PERSONAL AI COMPANION"
            textSize = 9f
            letterSpacing = 0.25f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(88, 139, 169))
        }

        character = AiCharacterView(this)
        character.contentDescription = "Animated RAYMOND AI character. Tap to start or stop a voice conversation. Long press to scan an image."
        character.isClickable = true
        character.isFocusable = true
        character.setOnClickListener {
            if (sessionActive) stopConversation()
            else if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startConversation()
            } else {
                audioPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        character.setOnLongClickListener {
            imagePicker.launch("image/*")
            true
        }

        status = TextView(this).apply {
            text = "●  READY WHEN YOU ARE"
            textSize = 11f
            letterSpacing = 0.16f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(83, 225, 255))
            setShadowLayer(14f, 0f, 0f, Color.rgb(0, 160, 255))
            setPadding(8, 8, 8, 8)
        }

        transcript = TextView(this).apply {
            text = "Tap the character and start talking to me."
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(205, 232, 247))
            setPadding(18, 16, 18, 16)
            background = GradientDrawable().apply {
                cornerRadius = 20f * resources.displayMetrics.density
                setColor(Color.argb(95, 8, 24, 43))
                setStroke((1f * resources.displayMetrics.density).toInt().coerceAtLeast(1), Color.argb(105, 0, 174, 220))
            }
        }

        val hint = TextView(this).apply {
            text = "TAP CHARACTER TO TALK  •  HOLD TO SCAN"
            textSize = 8f
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(65, 103, 132))
            setPadding(8, 15, 8, 0)
        }

        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        root.addView(subtitle, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 7 })
        root.addView(character, LinearLayout.LayoutParams(-1, 0, 1.55f).apply {
            topMargin = 2
            bottomMargin = 0
        })
        root.addView(status, LinearLayout.LayoutParams(-1, -2))
        root.addView(transcript, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8 })
        root.addView(hint, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
    }

    private fun createRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "SPEECH RECOGNITION UNAVAILABLE"
            return
        }
        try {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).also {
                it.setRecognitionListener(recognitionListener)
            }
        } catch (_: Exception) {
            recognizer = null
        }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            isListening = true
            retryDelay = 700L
            status.text = "●  LISTENING"
            character.mode = AiCharacterView.Mode.LISTENING
        }
        override fun onBeginningOfSpeech() { character.mode = AiCharacterView.Mode.LISTENING }
        override fun onRmsChanged(rmsdB: Float) {
            character.voiceLevel = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            isListening = false
            status.text = "◉  THINKING"
            character.mode = AiCharacterView.Mode.THINKING
        }
        override fun onError(error: Int) {
            isListening = false
            character.voiceLevel = 0f
            if (!sessionActive || isSpeaking) return
            createRecognizer()
            val delay = when (error) {
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> max(retryDelay, 1800L)
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER,
                SpeechRecognizer.ERROR_CLIENT -> max(retryDelay, 1200L)
                else -> retryDelay
            }
            retryDelay = (retryDelay * 2).coerceAtMost(8000L)
            scheduleListening(delay)
        }
        override fun onResults(results: Bundle?) {
            isListening = false
            retryDelay = 700L
            character.voiceLevel = 0f
            val words = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (words.isBlank()) scheduleListening(350) else handlePhrase(words)
        }
        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!partial.isNullOrBlank()) transcript.text = partial
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun startConversation() {
        sessionActive = true
        status.text = "CONNECTING MICROPHONE"
        transcript.text = "I'm here. You can speak naturally. You don't need to repeat my name."
        character.mode = AiCharacterView.Mode.LISTENING
        startListening()
    }

    private fun stopConversation() {
        sessionActive = false
        isListening = false
        handler.removeCallbacksAndMessages(null)
        try { recognizer?.cancel() } catch (_: Exception) {}
        character.mode = AiCharacterView.Mode.IDLE
        status.text = "●  CONVERSATION PAUSED"
        transcript.text = "Tap my character whenever you want to talk again."
    }

    private fun startListening() {
        if (!sessionActive || isSpeaking || isListening) return
        if (recognizer == null) createRecognizer()
        val speech = recognizer ?: run {
            status.text = "SPEECH SERVICE UNAVAILABLE"
            scheduleListening(2500)
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            speech.startListening(intent)
        } catch (_: SecurityException) {
            stopConversation()
            status.text = "MICROPHONE PERMISSION REQUIRED"
        } catch (_: Exception) {
            isListening = false
            createRecognizer()
            scheduleListening(retryDelay)
            retryDelay = (retryDelay * 2).coerceAtMost(8000L)
        }
    }

    private fun scheduleListening(delay: Long) {
        if (!sessionActive || isSpeaking) return
        handler.removeCallbacks(retryListening)
        handler.postDelayed(retryListening, delay)
    }

    private fun handlePhrase(raw: String) {
        val phrase = raw.trim()
        val normalized = phrase.lowercase(Locale.getDefault())
        val stopPhrases = listOf("stop listening", "stop conversation", "end conversation", "goodbye", "go to sleep")
        if (stopPhrases.any { normalized == it || normalized.endsWith(it) }) {
            sessionActive = false
            character.mode = AiCharacterView.Mode.IDLE
            status.text = "●  CONVERSATION PAUSED"
            speak("Okay. I'll pause here. Tap my character whenever you want to talk again.", false)
            return
        }

        transcript.text = phrase
        status.text = "◉  THINKING"
        character.mode = AiCharacterView.Mode.THINKING

        val localReply = try {
            PhoneCommandRouter.handle(this, phrase)
        } catch (_: Exception) {
            "I couldn't complete that phone action."
        }

        if (localReply != null) {
            status.text = "PHONE ACTION"
            speak(localReply, true)
        } else {
            AssistantApi.ask(phrase) { result ->
                runOnUiThread {
                    if (isFinishing) return@runOnUiThread
                    val reply = result.getOrElse {
                        "I'm having trouble reaching my AI service. " + it.message.orEmpty()
                    }
                    status.text = if (result.isSuccess) "RAYMOND RESPONDED" else "CONNECTION ISSUE"
                    transcript.text = "$phrase\n\n$reply"
                    speak(reply, true)
                }
            }
        }
    }

    private fun speak(message: String, continueConversation: Boolean) {
        if (!ttsReady) {
            pendingSpeech = message
            return
        }
        isSpeaking = true
        isListening = false
        try { recognizer?.cancel() } catch (_: Exception) {}
        character.mode = AiCharacterView.Mode.SPEAKING
        character.voiceLevel = 0.65f
        val result = tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "raymond_reply")
        if (result == TextToSpeech.ERROR) {
            isSpeaking = false
            character.mode = AiCharacterView.Mode.IDLE
            if (continueConversation) scheduleListening(900)
        }
    }

    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) {
            val language = tts?.setLanguage(Locale.getDefault())
            ttsReady = language != TextToSpeech.LANG_MISSING_DATA && language != TextToSpeech.LANG_NOT_SUPPORTED
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    runOnUiThread {
                        isSpeaking = true
                        character.mode = AiCharacterView.Mode.SPEAKING
                        status.text = "◉  SPEAKING"
                    }
                }
                override fun onDone(utteranceId: String?) {
                    runOnUiThread {
                        isSpeaking = false
                        character.voiceLevel = 0f
                        if (sessionActive) {
                            character.mode = AiCharacterView.Mode.LISTENING
                            status.text = "●  LISTENING"
                            scheduleListening(500)
                        } else {
                            character.mode = AiCharacterView.Mode.IDLE
                        }
                    }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    runOnUiThread {
                        isSpeaking = false
                        character.voiceLevel = 0f
                        character.mode = AiCharacterView.Mode.IDLE
                        if (sessionActive) scheduleListening(900)
                    }
                }
                override fun onError(utteranceId: String?, errorCode: Int) = onError(utteranceId)
            })
            pendingSpeech?.let {
                pendingSpeech = null
                speak(it, sessionActive)
            }
        } else {
            status.text = "VOICE OUTPUT UNAVAILABLE"
        }
    }

    private fun scanImage(uri: Uri) {
        status.text = "SCANNING IMAGE"
        character.mode = AiCharacterView.Mode.THINKING
        try {
            val image = com.google.mlkit.vision.common.InputImage.fromFilePath(this, uri)
            com.google.mlkit.vision.text.TextRecognition.getClient(
                com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS
            ).process(image)
                .addOnSuccessListener { result ->
                    val extracted = result.text.trim()
                    transcript.text = if (extracted.isBlank()) "No readable text was found." else extracted
                    status.text = "IMAGE SCAN COMPLETE"
                    speak(if (extracted.isBlank()) "I couldn't find readable text in that image." else "I've scanned the image and displayed the text.", sessionActive)
                }
                .addOnFailureListener {
                    status.text = "SCAN FAILED"
                    speak("I couldn't read that image. Please try a clearer picture.", sessionActive)
                }
        } catch (_: Exception) {
            status.text = "COULD NOT OPEN IMAGE"
            character.mode = AiCharacterView.Mode.IDLE
        }
    }

    override fun onDestroy() {
        sessionActive = false
        handler.removeCallbacksAndMessages(null)
        try { recognizer?.cancel(); recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}

class AiCharacterView(context: android.content.Context) : View(context) {
    enum class Mode { IDLE, LISTENING, THINKING, SPEAKING }
    var mode: Mode = Mode.IDLE
        set(value) { field = value; invalidate() }
    var voiceLevel: Float = 0f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handler = Handler(Looper.getMainLooper())
    private var frame = 0
    private val animator = object : Runnable {
        override fun run() {
            frame++
            if (mode == Mode.SPEAKING) voiceLevel = 0.25f + (0.55f * (0.5f + 0.5f * sin(frame * 0.42f))).toFloat()
            invalidate()
            handler.postDelayed(this, 34)
        }
    }

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        handler.post(animator)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val cx = w / 2f
        val scale = min(w / 330f, h / 390f).coerceAtLeast(0.55f)
        val cy = h * 0.50f
        val t = frame * 0.035f
        val pulse = (sin(t).toFloat() * 2.5f) * scale
        val glow = when (mode) {
            Mode.LISTENING -> Color.rgb(0, 225, 255)
            Mode.THINKING -> Color.rgb(142, 119, 255)
            Mode.SPEAKING -> Color.rgb(65, 255, 213)
            Mode.IDLE -> Color.rgb(0, 165, 225)
        }
        val headW = 112f * scale
        val headH = 145f * scale
        val headTop = cy - 112f * scale + pulse
        val head = RectF(cx - headW / 2, headTop, cx + headW / 2, headTop + headH)

        paint.style = Paint.Style.FILL
        paint.color = Color.argb(15, Color.red(glow), Color.green(glow), Color.blue(glow))
        paint.setShadowLayer(48f * scale, 0f, 0f, glow)
        canvas.drawRoundRect(RectF(cx - 105f * scale, cy - 160f * scale, cx + 105f * scale, cy + 190f * scale),
            90f * scale, 90f * scale, paint)
        paint.clearShadowLayer()

        val shoulders = Path().apply {
            moveTo(cx - 115f * scale, cy + 175f * scale)
            cubicTo(cx - 105f * scale, cy + 105f * scale, cx - 65f * scale, cy + 83f * scale, cx, cy + 83f * scale)
            cubicTo(cx + 65f * scale, cy + 83f * scale, cx + 105f * scale, cy + 105f * scale, cx + 115f * scale, cy + 175f * scale)
            close()
        }
        paint.shader = LinearGradient(cx, cy + 80f * scale, cx, cy + 180f * scale,
            intArrayOf(Color.rgb(12, 92, 133), Color.rgb(5, 22, 49)), null, Shader.TileMode.CLAMP)
        paint.setShadowLayer(22f * scale, 0f, 0f, glow)
        canvas.drawPath(shoulders, paint)
        paint.shader = null
        paint.clearShadowLayer()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * scale
        paint.color = Color.argb(175, Color.red(glow), Color.green(glow), Color.blue(glow))
        canvas.drawPath(shoulders, paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(20, 93, 126)
        canvas.drawRoundRect(RectF(cx - 23f * scale, cy + 43f * scale, cx + 23f * scale, cy + 105f * scale), 12f * scale, 12f * scale, paint)

        paint.color = Color.rgb(21, 106, 145)
        canvas.drawRoundRect(RectF(head.left - 10f * scale, head.top + 55f * scale, head.left + 9f * scale, head.top + 91f * scale), 8f * scale, 8f * scale, paint)
        canvas.drawRoundRect(RectF(head.right - 9f * scale, head.top + 55f * scale, head.right + 10f * scale, head.top + 91f * scale), 8f * scale, 8f * scale, paint)
        paint.shader = LinearGradient(head.left, head.top, head.right, head.bottom,
            intArrayOf(Color.rgb(112, 224, 244), Color.rgb(31, 111, 158), Color.rgb(12, 42, 79)),
            null, Shader.TileMode.CLAMP)
        paint.setShadowLayer(25f * scale, 0f, 0f, glow)
        canvas.drawRoundRect(head, 48f * scale, 48f * scale, paint)
        paint.shader = null
        paint.clearShadowLayer()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.6f * scale
        paint.color = Color.argb(210, 173, 247, 255)
        canvas.drawRoundRect(head, 48f * scale, 48f * scale, paint)

        val hair = Path().apply {
            moveTo(head.left + 4f * scale, head.top + 55f * scale)
            lineTo(head.left + 9f * scale, head.top + 21f * scale)
            quadTo(cx, head.top - 20f * scale, head.right - 9f * scale, head.top + 21f * scale)
            lineTo(head.right - 4f * scale, head.top + 55f * scale)
            quadTo(cx, head.top + 28f * scale, head.left + 4f * scale, head.top + 55f * scale)
        }
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(7, 30, 63)
        canvas.drawPath(hair, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * scale
        paint.color = Color.argb(220, 92, 236, 255)
        canvas.drawPath(hair, paint)

        val blink = frame % 115 in 0..5
        val eyeY = head.top + 76f * scale
        val eyeOffset = 29f * scale
        for (side in listOf(-1f, 1f)) {
            val ex = cx + side * eyeOffset
            val eye = RectF(ex - 12f * scale, eyeY - (if (blink) 1f else 7f) * scale,
                ex + 12f * scale, eyeY + (if (blink) 1f else 7f) * scale)
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(4, 30, 53)
            canvas.drawOval(eye, paint)
            if (!blink) {
                paint.color = Color.rgb(103, 250, 255)
                paint.setShadowLayer(9f * scale, 0f, 0f, Color.rgb(0, 225, 255))
                canvas.drawCircle(ex, eyeY, 5f * scale, paint)
                paint.clearShadowLayer()
                paint.color = Color.WHITE
                canvas.drawCircle(ex - 1.5f * scale, eyeY - 2f * scale, 1.8f * scale, paint)
            }
        }

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.6f * scale
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.argb(125, 190, 249, 255)
        canvas.drawLine(cx, eyeY + 12f * scale, cx - 2f * scale, eyeY + 30f * scale, paint)
        val mouthY = head.top + 112f * scale
        val mouthWidth = (if (mode == Mode.SPEAKING) 10f + voiceLevel * 15f else 18f) * scale
        val mouthHeight = (if (mode == Mode.SPEAKING) 3f + voiceLevel * 10f else 3f) * scale
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(2, 28, 51)
        canvas.drawRoundRect(RectF(cx - mouthWidth, mouthY - mouthHeight, cx + mouthWidth, mouthY + mouthHeight),
            8f * scale, 8f * scale, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.4f * scale
        paint.color = Color.rgb(105, 246, 255)
        canvas.drawRoundRect(RectF(cx - mouthWidth, mouthY - mouthHeight, cx + mouthWidth, mouthY + mouthHeight),
            8f * scale, 8f * scale, paint)

        paint.style = Paint.Style.FILL
        paint.color = glow
        paint.setShadowLayer(12f * scale, 0f, 0f, glow)
        canvas.drawRoundRect(RectF(cx - 13f * scale, cy + 137f * scale, cx + 13f * scale, cy + 141f * scale),
            3f * scale, 3f * scale, paint)
        paint.clearShadowLayer()

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f * scale
        paint.color = Color.argb(28, 160, 245, 255)
        for (i in -2..2) {
            val x = cx + i * 7f * scale
            canvas.drawLine(x, head.top + 7f * scale, x, head.top + 18f * scale, paint)
        }
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(animator)
        super.onDetachedFromWindow()
    }
}
