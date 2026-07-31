# Whisper Keyboard

Whisper Keyboard is an Android Input Method Editor (IME) that performs voice dictation using either a self-hosted remote OpenAI Whisper API server or on-device local inference via C++ NDK bindings (whisper.cpp). The application functions as a system-wide custom keyboard, intercepting audio input and injecting transcribed text into any active Android text field using standard `InputConnection` APIs.

## System Architecture & Stack

- **Platform:** Android SDK (Kotlin, Java API level 26+).
- **Core Engine:** Android InputMethodService (`VoiceInputMethodService`).
- **Native Inference Engine:** C++20 whisper.cpp compiled via CMake Android NDK r25+ targeting `arm64-v8a` and `x86_64`.
- **Networking Layer:** OkHttp 4.x and Retrofit 2.x for HTTP/HTTPS multipart audio upload, keep-alive connection pooling, and endpoint health monitoring.
- **Audio Capture:** Android `MediaRecorder` encoding 16kHz mono AAC/MPEG-4 audio (`audio/m4a`).
- **User Interface:** Vanilla Android XML Layouts, Material Design 3, custom touch listeners for progressive deletion and floating popup key selection.

### Component Data Flow

1. **User Input:** User taps or holds the central microphone view within `VoiceInputMethodService`.
2. **Audio Capture:** `AudioRecorderManager` initializes `MediaRecorder` and captures uncompressed 16kHz mono audio to local storage (`.m4a`).
3. **Voice Activity Detection (VAD):** `VoiceInputMethodService` monitors live peak amplitude via `getMaxAmplitude()`. If configured, it automatically terminates recording after 1800ms of consecutive silence.
4. **Execution Mode Dispatch:**
   - **Remote Server Mode:** `WhisperApiClient` executes an HTTP POST multipart file upload to `/transcribe?model=<key>&language=<lang>`.
   - **Offline Edge Mode:** `OnDeviceTranscriber` invokes native C++ JNI code (`libwhisper_native.so`), executing whisper.cpp model inference directly on local GGML quantized model files (`ggml-*.bin`).
5. **Text Injection:** Transcribed text is emitted to the target application's focused `EditText` via `InputConnection.commitText()`.

## Prerequisites

### Hardware Requirements

- **Processor:** 64-bit ARM (`arm64-v8a`) or x86_64 processor (Qualcomm Snapdragon, MediaTek, Samsung Exynos, Google Tensor, or Intel/AMD x86_64 emulator).
- **Memory (RAM):**
  - Remote Mode: Minimum 1 GB available system RAM.
  - Offline Edge Mode: Minimum 2 GB RAM for `tiny`/`base`/`small` models; minimum 4 GB RAM for `large-v3-turbo` / `large-v3` quantized models.
- **Storage:** Minimum 50 MB for base APK; 100 MB to 2.0 GB free internal storage if downloading offline GGML models locally.

### Software Requirements

- **Operating System:** Android 8.0 (API level 26) or higher.
- **Build Environment:**
  - Linux (Debian, Ubuntu, Arch Linux, RHEL) or macOS.
  - Java Development Kit (JDK) 17 or higher.
  - Android SDK Tools with API 34 platform.
  - Android NDK (r25c or higher) and CMake 3.22.1+.
  - Gradle 8.5+.

## Deployment & Installation

### Step 1: Repository Preparation

Clone the repository and enter the project root directory:

```bash
git clone https://github.com/T1erno/whisper-keyboard.git
cd whisper-keyboard
```

### Step 2: Environment Configuration

Ensure the `ANDROID_HOME` and `JAVA_HOME` environment variables are set correctly:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export ANDROID_HOME=/home/user/Android/Sdk
```

Optionally define `local.properties` in the project root:

```properties
sdk.dir=/home/user/Android/Sdk
ndk.dir=/home/user/Android/Sdk/ndk/25.2.9519653
```

### Step 3: Compilation & Build

Build the debug APK using the Gradle wrapper:

```bash
./gradlew assembleDebug
```

For release builds:

```bash
./gradlew assembleRelease
```

The resulting binaries will be placed in `app/build/outputs/apk/debug/app-debug.apk` and `app/build/outputs/apk/release/app-release-unsigned.apk`.

### Step 4: Device Deployment

Connect an Android device via USB with ADB debugging enabled, then execute:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

To view real-time runtime logs from the IME service:

```bash
adb logcat -s VoiceInputMethodService WhisperApiClient OnDeviceTranscriber
```

## Configuration

Application configuration is stored in system `SharedPreferences` under the preference file `whisper_keyboard_prefs`.

| Parameter | Type | Required/Optional | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| `server_url` | String | Required (Remote) | `https://whisper.t1erno.com/` | Base URL of the backend Whisper API server. Must end with a trailing slash. |
| `engine_mode` | Enum | Required | `REMOTE_SERVER` | Execution mode. Options: `REMOTE_SERVER`, `EDGE_ON_DEVICE`. |
| `selected_model_file` | String | Required | `ggml-large-v3-turbo-q5_0.bin` | Filename of the target model selected for transcription. |
| `haptic_enabled` | Boolean | Optional | `true` | Enables or disables physical vibrator motor tactile feedback on key presses. |
| `auto_send_silence` | Boolean | Optional | `true` | Automatically stops recording and initiates transcription when 1800ms of continuous silence is detected. |

## Usage

### System Keyboard Activation

1. Launch the application main activity (`com.t1erno.whisperkeyboard.MainActivity`).
2. Complete the three-step onboarding process:
   - Tap **Grant Permission** to approve `android.permission.RECORD_AUDIO`.
   - Tap **Enable in Settings** to enable Whisper Keyboard under Android System Input Methods.
   - Tap **Select Keyboard** to switch the active system keyboard to Whisper Keyboard.

### Backend Server Endpoint Requirements

When using `REMOTE_SERVER` mode, the backend server must implement the following REST endpoints:

#### Health Check Endpoint

```http
HEAD /health
```

- Returns `200 OK` or `204 No Content` for TCP ping latency checks.

#### Model Availability Query Endpoint

```http
GET /models
```

- Returns a JSON key-boolean object representing loaded models on the remote server:

```json
{
  "large-v3-turbo": true,
  "tiny": true,
  "small": true,
  "large-v3": false
}
```

#### Audio Transcription Endpoint

```http
POST /transcribe?model=large-v3-turbo&language=es
Content-Type: multipart/form-data
```

- Accepts `file` multipart payload containing binary audio (`.m4a`).
- Returns JSON response:

```json
{
  "text": "Transcribed text content emitted back to the client."
}
```

#### Verification CLI Example

Test backend server connectivity and transcription using `curl`:

```bash
curl -X POST "https://whisper.t1erno.com/transcribe?model=large-v3-turbo&language=es" \
  -H "Accept: application/json" \
  -F "file=@sample_audio.m4a;type=audio/m4a"
```

## Networking & Security

- **Exposed Outbound Ports:** Standard HTTP (`80/TCP`) and HTTPS (`443/TCP`).
- **TLS Requirements:** HTTPS is enforced by default. If an unencrypted `http://` URL is configured, the application displays a security warning requiring user confirmation.
- **Network Topologies:** Supports direct internet access, reverse proxies (Nginx, OpenResty, Cloudflare Tunnels), and private mesh networks (Tailscale, WireGuard).
- **Android Execution Permissions:**
  - `android.permission.RECORD_AUDIO`: Required to capture microphone stream.
  - `android.permission.INTERNET`: Required for remote backend communications and downloading GGML models.
  - `android.permission.ACCESS_NETWORK_STATE`: Required for network health probing.
  - `android.permission.VIBRATE`: Required for haptic motor feedback.

## License

This project is licensed under the MIT License. See the `LICENSE` file for details.
