# RAYMOND

RAYMOND is a voice-first Android phone assistant with an animated orb interface.

## Current prototype

- Animated orb with idle, listening, thinking, and speaking states.
- One-time voice interaction from the app.
- Optional foreground listening mode that waits for the phrase **"Hey Raymond"**.
- A persistent Android notification with a stop-listening action.
- A built-in local command engine for greetings, date/time, opening selected apps, and Android Settings.
- Text-to-speech responses.

## Important background microphone note

Background listening is opt-in. The user must grant microphone permission and start the foreground assistant while the app is visible. Android displays an ongoing notification while the microphone service is active. Use its Stop listening action to turn it off. Android versions, device manufacturers, speech-recognition providers, battery settings, and on-device recognition availability can affect reliability and battery use. This prototype is not an always-on hardware wake-word detector.

## Local intelligence

This version does not use OpenAI or any cloud AI API. It uses local intent matching and Android system capabilities. It is a starter command engine, not a general-purpose large language model. More commands and safer confirmation flows will be added incrementally.

## Build

Open the project in Android Studio or use the repository's GitHub Actions workflow to build the debug APK.

Package: `com.raymond.assistant`
