package com.raymond.assistant

import android.Manifest
import android.net.Uri
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.View
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Locale
import kotlin.math.sin

class MainActivity : AppCompatActivity(), android.speech.tts.TextToSpeech.OnInitListener {
    private var recognizer: SpeechRecognizer? = null
    private var tts: android.speech.tts.TextToSpeech? = null
    private lateinit var status: TextView
    private lateinit var transcript: TextView
    private lateinit var orb: OrbView
    private lateinit var backgroundButton: Button

    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startOneShotListening() else status.text = "Microphone permission is required for voice commands."
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) enableBackgroundMode() else status.text = "Allow notifications so Android can show the background microphone indicator."
    }
    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scanImage(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = android.speech.tts.TextToSpeech(this, this)
        buildInterface()
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        } else {
            status.text = "Speech recognition is unavailable on this device."
        }
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 28, 28, 24)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(3, 8, 22), Color.rgb(7, 18, 38), Color.rgb(2, 7, 18)))
        }
        val title = TextView(this).apply {
            text = "R A Y M O N D"
            textSize = 25f
            letterSpacing = 0.18f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(130, 240, 255))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setShadowLayer(22f, 0f, 0f, Color.rgb(0, 190, 255))
        }
        val subtitle = TextView(this).apply {
            text = "PERSONAL VOICE INTELLIGENCE"
            textSize = 10f
            letterSpacing = 0.18f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(115, 145, 170))
        }
        orb = OrbView(this)
        status = TextView(this).apply {
            text = "SYSTEM STANDBY"
            textSize = 12f
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(90, 235, 255))
            setShadowLayer(12f, 0f, 0f, Color.rgb(0, 170, 255))
        }
        transcript = TextView(this).apply {
            text = "Say “Hey Raymond” when background mode is enabled."
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(220, 245, 255))
            setPadding(18, 20, 18, 20)
            background = neonPanel(Color.rgb(8, 24, 43), Color.rgb(0, 170, 220), 18f)
        }
        val talkButton = Button(this).apply {
            text = "TALK NOW"
            isAllCaps = false
            setOnClickListener {
                if (hasAudioPermission()) startOneShotListening()
                else audioPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        backgroundButton = Button(this).apply {
            text = "ENABLE BACKGROUND MODE"
            isAllCaps = false
            setOnClickListener {
                if (!hasAudioPermission()) audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else enableBackgroundMode()
            }
        }
        val scanButton = Button(this).apply {
            text = "SCAN IMAGE / DOCUMENT"
            isAllCaps = false
            setOnClickListener { imagePicker.launch("image/*") }
        }
        listOf(talkButton, scanButton, backgroundButton).forEachIndexed { index, button ->
            styleNeonButton(button, index == 0)
        }
        val footnote = TextView(this).apply {
            text = "LOCAL COMMAND MODE • MICROPHONE USE IS SHOWN BY ANDROID"
            textSize = 9f
            letterSpacing = 0.08f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(95, 115, 140))
            setPadding(8, 20, 8, 0)
        }
        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        root.addView(subtitle, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 5 })
        root.addView(orb, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = 10; bottomMargin = 4 })
        root.addView(status, LinearLayout.LayoutParams(-1, -2))
        root.addView(transcript, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 4 })
        root.addView(talkButton, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8 })
        root.addView(scanButton, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8 })
        root.addView(backgroundButton, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8 })
        root.addView(footnote, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
    }

    private fun neonPanel(fill: Int, stroke: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusDp * resources.displayMetrics.density
            setColor(fill)
            setStroke((1.2f * resources.displayMetrics.density).toInt().coerceAtLeast(1), stroke)
        }

    private fun styleNeonButton(button: Button, primary: Boolean) {
        val cyan = Color.rgb(0, 205, 255)
        button.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            if (primary) intArrayOf(Color.rgb(0, 105, 160), Color.rgb(0, 65, 125))
            else intArrayOf(Color.rgb(12, 34, 58), Color.rgb(8, 22, 42))
        ).apply {
            cornerRadius = 16f * resources.displayMetrics.density
            setStroke((1.3f * resources.displayMetrics.density).toInt(), if (primary) cyan else Color.rgb(0, 125, 175))
        }
        button.setTextColor(Color.rgb(205, 245, 255))
        button.textSize = 13f
        button.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        button.letterSpacing = 0.06f
        button.elevation = 10f * resources.displayMetrics.density
        button.setPadding(18, 15, 18, 15)
    }

    private fun hasAudioPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun enableBackgroundMode() {
        val service = Intent(this, RaymondListeningService::class.java)
        ContextCompat.startForegroundService(this, service)
        status.text = "BACKGROUND MODE STARTING"
        transcript.text = "RAYMOND will listen for “Hey Raymond”. Use the notification to stop."
        orb.mode = OrbView.Mode.LISTENING
        backgroundButton.text = "BACKGROUND MODE ENABLED"
    }

    private fun startOneShotListening() {
        val speech = recognizer ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        status.text = "LISTENING"
        orb.mode = OrbView.Mode.LISTENING
        speech.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { status.text = "THINKING" ; orb.mode = OrbView.Mode.THINKING }
            override fun onError(error: Int) { status.text = "READY"; orb.mode = OrbView.Mode.IDLE }
            override fun onResults(results: Bundle?) {
                val words = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                transcript.text = words.ifBlank { "I didn't catch that." }
                val localReply = try { PhoneCommandRouter.handle(this@MainActivity, words) } catch (_: Exception) { "I couldn't complete that phone action." }
                if (localReply != null) {
                    status.text = "PHONE ACTION"
                    orb.mode = OrbView.Mode.SPEAKING
                    speak(localReply)
                } else {
                    status.text = "THINKING"
                    orb.mode = OrbView.Mode.THINKING
                    AssistantApi.ask(words) { result ->
                        runOnUiThread {
                            val reply = result.getOrElse { it.message ?: "I couldn't connect to the AI service." }
                            status.text = if (result.isSuccess) "RAYMOND RESPONDED" else "CONNECTION ISSUE"
                            orb.mode = OrbView.Mode.SPEAKING
                            transcript.text = "$words\n\n$reply"
                            speak(reply)
                        }
                    }
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speech.startListening(intent)
    }

    private fun scanImage(uri: Uri) {
        status.text = "SCANNING DOCUMENT"
        orb.mode = OrbView.Mode.THINKING
        try {
            val image = InputImage.fromFilePath(this, uri)
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image)
                .addOnSuccessListener { result ->
                    val extracted = result.text.trim()
                    transcript.text = if (extracted.isBlank()) "No readable text was found." else extracted
                    status.text = "TEXT SCAN COMPLETE"
                    speak(if (extracted.isBlank()) "I couldn't find readable text in that image." else "I've extracted the text and displayed it on screen.")
                    orb.mode = OrbView.Mode.IDLE
                }
                .addOnFailureListener {
                    status.text = "SCAN FAILED"
                    speak("I couldn't read that image. Please try a clearer picture.")
                    orb.mode = OrbView.Mode.IDLE
                }
        } catch (_: Exception) {
            status.text = "COULD NOT OPEN IMAGE"
            orb.mode = OrbView.Mode.IDLE
        }
    }

    private fun speak(message: String) {
        tts?.speak(message, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "raymond_reply")
        Handler(Looper.getMainLooper()).postDelayed({ if (!isFinishing) orb.mode = OrbView.Mode.IDLE }, 1800)
    }

    override fun onInit(result: Int) {
        if (result == android.speech.tts.TextToSpeech.SUCCESS) tts?.language = Locale.getDefault()
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }
}

class OrbView(context: android.content.Context) : View(context) {
    enum class Mode { IDLE, LISTENING, THINKING, SPEAKING }
    var mode: Mode = Mode.IDLE
        set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var frame = 0
    private val handler = Handler(Looper.getMainLooper())
    private val pulse = object : Runnable {
        override fun run() { frame++; invalidate(); handler.postDelayed(this, 40) }
    }
    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        handler.post(pulse)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val base = (width.coerceAtMost(height) * 0.27f)
        val wave = sin(frame * 0.055).toFloat()
        val active = if (mode == Mode.IDLE) 0.025f else 0.075f
        val radius = base * (1f + wave * active)
        val glow = when (mode) {
            Mode.LISTENING -> Color.rgb(0, 220, 255)
            Mode.THINKING -> Color.rgb(125, 100, 255)
            Mode.SPEAKING -> Color.rgb(60, 255, 210)
            Mode.IDLE -> Color.rgb(0, 155, 210)
        }
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(18, Color.red(glow), Color.green(glow), Color.blue(glow))
        paint.setShadowLayer(radius * 0.75f, 0f, 0f, glow)
        canvas.drawCircle(cx, cy, radius * 1.78f, paint)
        paint.clearShadowLayer()
        paint.color = Color.argb(38, Color.red(glow), Color.green(glow), Color.blue(glow))
        canvas.drawCircle(cx, cy, radius * 1.42f, paint)
        paint.color = Color.argb(24, Color.red(glow), Color.green(glow), Color.blue(glow))
        canvas.drawCircle(cx, cy, radius * 1.22f, paint)
        val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        val shader = android.graphics.RadialGradient(cx - radius * .28f, cy - radius * .32f, radius * 1.5f,
            intArrayOf(Color.WHITE, glow, Color.rgb(5, 35, 75)), null, android.graphics.Shader.TileMode.CLAMP)
        paint.shader = shader
        paint.setShadowLayer(radius * 0.38f, 0f, 0f, glow)
        canvas.drawOval(rect, paint)
        paint.shader = null
        paint.clearShadowLayer()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.2f
        paint.color = Color.argb(210, 145, 245, 255)
        canvas.drawCircle(cx, cy, radius * (1.13f + wave * 0.025f), paint)
        canvas.save()
        canvas.rotate(frame * if (mode == Mode.THINKING) 2.2f else 0.65f, cx, cy)
        paint.strokeWidth = 3.2f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.argb(220, 95, 235, 255)
        canvas.drawArc(RectF(cx - radius * 1.30f, cy - radius * 1.30f, cx + radius * 1.30f, cy + radius * 1.30f), 205f, 92f, false, paint)
        paint.strokeWidth = 1.6f
        paint.color = Color.argb(145, 130, 115, 255)
        canvas.drawArc(RectF(cx - radius * 1.43f, cy - radius * 1.43f, cx + radius * 1.43f, cy + radius * 1.43f), 28f, 65f, false, paint)
        canvas.restore()
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(160, 175, 245, 255)
        canvas.drawCircle(cx - radius * .28f, cy - radius * .34f, radius * .12f, paint)
    }
    override fun onDetachedFromWindow() { handler.removeCallbacks(pulse); super.onDetachedFromWindow() }
}
