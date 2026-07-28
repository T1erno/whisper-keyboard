# Whisper Keyboard 🎙️

A custom, single-button Voice Input Method Editor (IME) for Android powered by OpenAI Whisper.

## Features
- **Minimalist Voice Dictation UI**: A sleek, dark purple interface with a large central microphone button.
- **Hold-to-Talk Recording**: Instant AAC/MPEG-4 audio capture with hardware haptic motor feedback (`brr`) and audio prompt chimes.
- **Cancel Transcription**: Dedicated cancellation button during upload and network transcription.
- **Punctuation Key Bar**: 5 compact keys (`,`, `.`, `?`, `!`, `"`) with 0.5s long-press floating popup menus for multi-symbol alternatives (`¡`, `¿`, `;`, `:`, `'`, `<`, `{`, `>`, `}`, `~`, `\`, `|`, `/`, `` ` ``, `«`, `»`).
- **Progressive Accelerated Backspace**: Smart hold-to-delete logic (0–1s @ 500ms, 1–3s @ 150ms, 3s+ @ 20ms).
- **Onboarding & Configuration Menu**:
  - Live 2-second periodic TCP ping test with visual status dot (🟢 Green / 🔴 Red).
  - Custom backend URL configuration for Tailscale or local server deployment.
  - Haptic vibration motor toggle.
  - Step-by-step setup check (Microphone Permission, System IME Enable, Active Keyboard Selection).

## Setup & Build
Headless terminal build with Gradle wrapper:
```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleDebug
```
Deploy via ADB:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
