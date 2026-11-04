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
    private var pendingBackgroundActivation = false

    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            if (pendingBackgroundActivation) continueBackgroundActivation() else startOneShotListening()
        } else {
            pendingBackgroundActivation = false
            status.text = "Microphone permission is required for voice commands."
        }
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) enableBackgroundMode() else status.text = "Allow notifications so Android can show when RAYMOND is active."
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
        window.statusBarColor = Color.rgb(2, 6, 18)
        window.navigationBarColor = Color.rgb(2, 6, 18)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LOW_PROFILE

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(22, 30, 22, 24)
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
            setShadowLayer(28f, 0f, 0f, Color.rgb(0, 190, 255))
        }
        val subtitle = TextView(this).apply {
            text = "PERSONAL AI  •  VOICE INTERFACE"
            textSize = 9f
            letterSpacing = 0.22f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(88, 139, 169))
        }

        val orbFrame = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setBackgroundColor(Color.TRANSPARENT)
        }
        orb = OrbView(this)
        orb.contentDescription = "RAYMOND holographic AI core. Tap to activate the assistant. Long-press to scan an image or document."
        orb.isClickable = true
        orb.isFocusable = true
        orb.setOnClickListener { requestBackgroundActivation() }
        orb.setOnLongClickListener {
            imagePicker.launch("image/*")
            true
        }
        orbFrame.addView(orb, LinearLayout.LayoutParams(-1, -1))

        status = TextView(this).apply {
            text = "●  SYSTEM STANDBY"
            textSize = 11f
            letterSpacing = 0.18f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(83, 225, 255))
            setShadowLayer(15f, 0f, 0f, Color.rgb(0, 160, 255))
            setPadding(8, 8, 8, 8)
        }

        transcript = TextView(this).apply {
            text = "Your intelligent assistant is ready."
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
            text = "TAP THE CORE TO ACTIVATE     •     HOLD TO SCAN"
            textSize = 8f
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(65, 103, 132))
            setPadding(8, 18, 8, 0)
        }

        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        root.addView(subtitle, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 7 })
        root.addView(orbFrame, LinearLayout.LayoutParams(-1, 0, 1.65f).apply {
            topMargin = 4
            bottomMargin = 0
        })
        root.addView(status, LinearLayout.LayoutParams(-1, -2))
        root.addView(transcript, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = 8
        })
        root.addView(hint, LinearLayout.LayoutParams(-1, -2))
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

    private fun requestBackgroundActivation() {
        if (!hasAudioPermission()) {
            pendingBackgroundActivation = true
            audioPermission.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            continueBackgroundActivation()
        }
    }

    private fun continueBackgroundActivation() {
        pendingBackgroundActivation = false
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            enableBackgroundMode()
        }
    }

    private fun enableBackgroundMode() {
        val service = Intent(this, RaymondListeningService::class.java)
        ContextCompat.startForegroundService(this, service)
        status.text = "BACKGROUND MODE STARTING"
        transcript.text = "RAYMOND will listen for “Hey Raymond”. Use the notification to stop."
        orb.mode = OrbView.Mode.LISTENING
        orb.contentDescription = "RAYMOND background listening is active. Tap to open the app; use the notification to stop listening."
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
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var frame = 0
    private val handler = Handler(Looper.getMainLooper())
    private val pulse = object : Runnable {
        override fun run() {
            frame++
            invalidate()
            handler.postDelayed(this, 32)
        }
    }

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        handler.post(pulse)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val base = width.coerceAtMost(height) * 0.205f
        val time = frame * 0.018f
        val breathing = kotlin.math.sin(time).toFloat()
        val active = if (mode == Mode.IDLE) 0.018f else 0.055f
        val radius = base * (1f + breathing * active)

        val glow = when (mode) {
            Mode.LISTENING -> Color.rgb(0, 229, 255)
            Mode.THINKING -> Color.rgb(133, 112, 255)
            Mode.SPEAKING -> Color.rgb(75, 255, 220)
            Mode.IDLE -> Color.rgb(0, 178, 235)
        }
        val cyan = Color.rgb(70, 231, 255)
        val blue = Color.rgb(55, 116, 255)

        // Wide atmospheric halo behind the holographic core.
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(18, Color.red(glow), Color.green(glow), Color.blue(glow))
        paint.setShadowLayer(radius * 1.25f, 0f, 0f, glow)
        canvas.drawCircle(cx, cy, radius * 2.35f, paint)
        paint.clearShadowLayer()
        paint.color = Color.argb(15, 0, 170, 255)
        canvas.drawCircle(cx, cy, radius * 1.95f, paint)

        // Thin concentric holographic rings.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f
        paint.color = Color.argb(55, 91, 207, 255)
        canvas.drawCircle(cx, cy, radius * 1.78f, paint)
        paint.strokeWidth = 1.5f
        paint.color = Color.argb(90, 73, 196, 255)
        canvas.drawCircle(cx, cy, radius * 1.52f, paint)

        // Rotating segmented orbital rings.
        canvas.save()
        canvas.rotate(frame * if (mode == Mode.THINKING) 1.8f else 0.42f, cx, cy)
        val ringRect1 = RectF(cx - radius * 1.62f, cy - radius * 1.62f, cx + radius * 1.62f, cy + radius * 1.62f)
        val ringRect2 = RectF(cx - radius * 1.88f, cy - radius * 1.88f, cx + radius * 1.88f, cy + radius * 1.88f)
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 4f
        paint.color = Color.argb(220, 65, 224, 255)
        canvas.drawArc(ringRect1, 198f, 74f, false, paint)
        canvas.drawArc(ringRect1, 18f, 42f, false, paint)
        paint.strokeWidth = 2.2f
        paint.color = Color.argb(155, 104, 131, 255)
        canvas.drawArc(ringRect2, 35f, 62f, false, paint)
        canvas.drawArc(ringRect2, 220f, 46f, false, paint)
        paint.strokeWidth = 1.1f
        paint.color = Color.argb(95, 74, 220, 255)
        canvas.drawArc(ringRect2, 112f, 38f, false, paint)
        canvas.restore()

        // Orbiting light nodes.
        paint.style = Paint.Style.FILL
        for (i in 0 until 8) {
            val angle = time * (if (i % 2 == 0) 0.75f else -0.55f) + i * (Math.PI.toFloat() / 4f)
            val orbit = radius * if (i % 2 == 0) 1.62f else 1.88f
            val px = cx + kotlin.math.cos(angle) * orbit
            val py = cy + kotlin.math.sin(angle) * orbit
            paint.color = if (i % 2 == 0) Color.argb(230, 115, 240, 255) else Color.argb(190, 126, 151, 255)
            paint.setShadowLayer(12f, 0f, 0f, paint.color)
            canvas.drawCircle(px, py, if (i % 2 == 0) 3.2f else 2.1f, paint)
            paint.clearShadowLayer()
        }

        // Luminous energy shell.
        paint.style = Paint.Style.FILL
        val core = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        val shader = android.graphics.RadialGradient(
            cx - radius * 0.30f,
            cy - radius * 0.36f,
            radius * 1.55f,
            intArrayOf(Color.rgb(240, 255, 255), cyan, blue, Color.rgb(3, 16, 49)),
            floatArrayOf(0f, 0.28f, 0.68f, 1f),
            android.graphics.Shader.TileMode.CLAMP
        )
        paint.shader = shader
        paint.setShadowLayer(radius * 0.62f, 0f, 0f, glow)
        canvas.drawCircle(cx, cy, radius * (1.02f + breathing * active), paint)
        paint.shader = null
        paint.clearShadowLayer()

        // Bright glass highlight and fine inner contour.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.argb(215, 191, 250, 255)
        canvas.drawCircle(cx, cy, radius * 1.02f, paint)
        paint.strokeWidth = 1f
        paint.color = Color.argb(90, 230, 255, 255)
        canvas.drawArc(core, 205f, 105f, false, paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.argb(175, 235, 255, 255)
        paint.setShadowLayer(18f, 0f, 0f, cyan)
        canvas.drawCircle(cx - radius * 0.30f, cy - radius * 0.36f, radius * 0.105f, paint)
        paint.clearShadowLayer()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(pulse)
        super.onDetachedFromWindow()
    }
}
