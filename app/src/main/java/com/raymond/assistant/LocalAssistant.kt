package com.raymond.assistant

import android.content.Context
import android.content.Intent
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LocalAssistant {
    fun respond(context: Context, raw: String): String {
        val text = raw.lowercase(Locale.getDefault()).trim()
            .removePrefix("hey raymond").removePrefix("raymond").trim()
        if (text.isBlank()) return "I'm here. What would you like me to do?"
        if (listOf("hello", "hi", "good morning", "good afternoon", "good evening").any { text == it || text.startsWith("$it ") }) {
            return "Hello. RAYMOND is online and ready to help."
        }
        if ("what time" in text || text == "time") {
            return "The time is " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
        }
        if ("what date" in text || "today's date" in text || text == "date") {
            return "Today is " + SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date())
        }
        if ("who are you" in text || "your name" in text) return "I'm RAYMOND, your voice-first phone assistant. I'm currently using my built-in local command intelligence."
        if ("what can you do" in text || text == "help" || "help me" == text) {
            return "I can tell you the time and date, open supported apps, and open Android settings. More abilities will be added as we build."
        }
        if ("open settings" in text || "phone settings" in text) {
            launch(context, Intent(Settings.ACTION_SETTINGS))
            return "Opening your phone settings."
        }
        val apps = mapOf(
            "youtube" to "com.google.android.youtube",
            "whatsapp" to "com.whatsapp",
            "calculator" to "com.google.android.calculator",
            "camera" to "com.android.camera",
            "chrome" to "com.android.chrome",
            "gmail" to "com.google.android.gm",
            "maps" to "com.google.android.apps.maps",
            "play store" to "com.android.vending",
            "phone" to "com.google.android.dialer",
            "messages" to "com.google.android.apps.messaging"
        )
        val requested = apps.keys.firstOrNull { text.contains("open $it") || text == it }
        if (requested != null) {
            val intent = context.packageManager.getLaunchIntentForPackage(apps.getValue(requested))
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return "Opening $requested."
            }
            return "I couldn't find $requested on this phone."
        }
        if ("thank" in text) return "You're welcome. I'm ready for your next command."
        return "I heard you say: $text. I don't know how to do that yet. My local command skills will grow as we add more features."
    }

    private fun launch(context: Context, intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
