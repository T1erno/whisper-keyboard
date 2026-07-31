package com.t1erno.whisperkeyboard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.inputmethodservice.InputMethodService
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.t1erno.whisperkeyboard.audio.AudioRecorderManager
import com.t1erno.whisperkeyboard.nativeengine.ModelManager
import com.t1erno.whisperkeyboard.nativeengine.OnDeviceTranscriber
import com.t1erno.whisperkeyboard.network.WhisperApiClient
import com.t1erno.whisperkeyboard.ui.ProgressiveBackspace
import com.t1erno.whisperkeyboard.ui.PunctuationKeyManager
import com.t1erno.whisperkeyboard.ui.SpacebarTouchListener
import com.t1erno.whisperkeyboard.ui.VibrationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class VoiceInputMethodService : InputMethodService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var audioRecorderManager: AudioRecorderManager

    private var tvStatus: TextView? = null
    private var tvPrompt: TextView? = null
    private var btnMic: ImageButton? = null
    private var progressBar: ProgressBar? = null
    private var btnCancelTranscription: View? = null

    private var toneGenerator: ToneGenerator? = null
    private var transcriptionJob: Job? = null
    private var amplitudeMonitorJob: Job? = null
    private lateinit var progressiveBackspace: ProgressiveBackspace
    private lateinit var punctuationKeyManager: PunctuationKeyManager

    private var isTapToTalkActive = false
    private var touchDownTimeMs = 0L

    private sealed class UiState {
        object IDLE : UiState()
        object RECORDING : UiState()
        object TRANSCRIBING : UiState()
        data class ERROR(val message: String) : UiState()
    }

    override fun onCreate() {
        super.onCreate()
        audioRecorderManager = AudioRecorderManager(this)
        progressiveBackspace = ProgressiveBackspace(
            contextProvider = { this },
            inputConnectionProvider = { currentInputConnection }
        )
        punctuationKeyManager = PunctuationKeyManager { text ->
            currentInputConnection?.commitText(text, 1)
        }
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        toneGenerator?.release()
        toneGenerator = null
    }

    override fun onCreateInputView(): View {
        val themedContext = ContextThemeWrapper(this, R.style.Theme_WhisperKeyboard)
        val view = LayoutInflater.from(themedContext).inflate(R.layout.input_view, null)

        tvStatus = view.findViewById(R.id.tv_status)
        tvPrompt = view.findViewById(R.id.tv_prompt)
        btnMic = view.findViewById(R.id.btn_mic)
        progressBar = view.findViewById(R.id.progress_bar)
        btnCancelTranscription = view.findViewById(R.id.btn_cancel_transcription)

        setupMicButton()
        punctuationKeyManager.setupPunctuationKeys(view, themedContext)
        setupUtilityButtons(view)

        btnCancelTranscription?.setOnClickListener {
            VibrationHelper.vibrateKey(this, 20L)
            cancelTranscription()
            updateUiState(UiState.IDLE)
        }

        updateUiState(UiState.IDLE)
        return view
    }

    private fun setupMicButton() {
        btnMic?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!hasRecordAudioPermission()) {
                        promptForPermission()
                        return@setOnTouchListener true
                    }

                    touchDownTimeMs = System.currentTimeMillis()

                    if (isTapToTalkActive && audioRecorderManager.isRecording) {
                        // Tapped while in tap-to-talk mode -> stop recording
                        isTapToTalkActive = false
                        stopAndProcessRecording()
                        return@setOnTouchListener true
                    }

                    playStartBeep()
                    VibrationHelper.vibrateKey(this, 40L)
                    startRecording()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val pressDurationMs = System.currentTimeMillis() - touchDownTimeMs

                    if (pressDurationMs < TAP_THRESHOLD_MS && audioRecorderManager.isRecording) {
                        // Short tap detected -> enter Tap-to-Talk mode (keeps recording active hands-free)
                        isTapToTalkActive = true
                        tvPrompt?.text = "Tap mic to finish speaking"
                    } else if (!isTapToTalkActive && audioRecorderManager.isRecording) {
                        // Hold to talk release -> stop recording immediately
                        stopAndProcessRecording()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun stopAndProcessRecording() {
        stopAmplitudeMonitor()
        playStopBeep()
        VibrationHelper.vibrateKey(this, 30L)
        val audioFile = audioRecorderManager.stopRecording()
        if (audioFile != null) {
            processTranscription(audioFile)
        } else {
            updateUiState(UiState.ERROR("Recording failed"))
        }
    }

    private fun startAmplitudeMonitor() {
        stopAmplitudeMonitor()
        amplitudeMonitorJob = serviceScope.launch {
            var speechDetected = false
            var silenceStartMs = 0L

            while (isActive && audioRecorderManager.isRecording) {
                val amp = audioRecorderManager.getMaxAmplitude()

                // Live Audio Waveform Pulse Animation
                val normalized = (amp / 25000.0f).coerceIn(0.0f, 1.0f)
                val targetScale = 1.0f + (normalized * 0.35f)
                btnMic?.scaleX = targetScale
                btnMic?.scaleY = targetScale

                // Voice Activity Detection (VAD) / Silence Auto-Stop
                if (PreferencesManager.isAutoSendOnSilenceEnabled(this@VoiceInputMethodService)) {
                    if (amp > SPEECH_DETECTION_THRESHOLD_AMP) {
                        speechDetected = true
                        silenceStartMs = 0L
                    } else if (speechDetected && amp < SILENCE_THRESHOLD_AMP) {
                        if (silenceStartMs == 0L) {
                            silenceStartMs = System.currentTimeMillis()
                        } else if (System.currentTimeMillis() - silenceStartMs >= SILENCE_AUTO_STOP_DURATION_MS) {
                            // Silence auto-stop triggered!
                            isTapToTalkActive = false
                            stopAndProcessRecording()
                            break
                        }
                    }
                }

                delay(60L)
            }
        }
    }

    private fun stopAmplitudeMonitor() {
        amplitudeMonitorJob?.cancel()
        amplitudeMonitorJob = null
        btnMic?.scaleX = 1.0f
        btnMic?.scaleY = 1.0f
    }

    private fun playStartBeep() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
        } catch (_: Exception) {}
    }

    private fun playStopBeep() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_PROMPT, 80)
        } catch (_: Exception) {}
    }

    private fun setupUtilityButtons(view: View) {
        view.findViewById<ImageButton>(R.id.btn_settings)?.setOnClickListener {
            VibrationHelper.vibrateKey(this, 20L)
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            startActivity(intent)
        }

        view.findViewById<ImageButton>(R.id.btn_hide_keyboard)?.setOnClickListener {
            VibrationHelper.vibrateKey(this, 20L)
            requestHideSelf(0)
        }

        view.findViewById<View>(R.id.btn_space)?.let { btn ->
            btn.setOnTouchListener(
                SpacebarTouchListener(
                    inputConnectionProvider = { currentInputConnection },
                    onSpaceClick = {
                        VibrationHelper.vibrateKey(this, 20L)
                        currentInputConnection?.commitText(" ", 1)
                    }
                )
            )
        }

        view.findViewById<ImageButton>(R.id.btn_backspace)?.let { btn ->
            progressiveBackspace.bind(btn)
        }

        view.findViewById<ImageButton>(R.id.btn_enter)?.setOnClickListener {
            VibrationHelper.vibrateKey(this, 20L)
            currentInputConnection?.sendKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
            )
            currentInputConnection?.sendKeyEvent(
                KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER)
            )
        }
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun promptForPermission() {
        Toast.makeText(this, "Microphone permission required to record audio. Launching permission setup...", Toast.LENGTH_LONG).show()
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("request_permission", true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        startActivity(intent)
    }

    private fun startRecording() {
        isTapToTalkActive = false
        val file = audioRecorderManager.startRecording()
        if (file != null) {
            updateUiState(UiState.RECORDING)
            startAmplitudeMonitor()
        } else {
            updateUiState(UiState.ERROR("Failed to access microphone"))
        }
    }

    private fun cancelTranscription() {
        stopAmplitudeMonitor()
        transcriptionJob?.cancel()
        transcriptionJob = null
        isTapToTalkActive = false
    }

    private fun processTranscription(audioFile: File) {
        stopAmplitudeMonitor()
        updateUiState(UiState.TRANSCRIBING)

        transcriptionJob = serviceScope.launch {
            val mode = PreferencesManager.getEngineMode(this@VoiceInputMethodService)
            val result = if (mode == PreferencesManager.EngineMode.EDGE_ON_DEVICE) {
                transcribeOnDevice(audioFile)
            } else {
                WhisperApiClient.uploadAudio(this@VoiceInputMethodService, audioFile)
            }

            result.fold(
                onSuccess = { text ->
                    commitTextToInput(text)
                    updateUiState(UiState.IDLE)
                },
                onFailure = { error ->
                    updateUiState(UiState.ERROR(error.message ?: "Transcription failed"))
                }
            )

            if (audioFile.exists()) {
                audioFile.delete()
            }
        }
    }

    private suspend fun transcribeOnDevice(audioFile: File): Result<String> = withContext(Dispatchers.IO) {
        val selectedFileName = PreferencesManager.getSelectedModelFileName(this@VoiceInputMethodService)
        val modelFile = ModelManager.getModelFile(this@VoiceInputMethodService, selectedFileName)

        if (!modelFile.exists()) {
            val modelInfo = ModelManager.getModelInfoByFileName(selectedFileName)
            return@withContext Result.failure(Exception("Model missing (${modelInfo.name}). Download in settings."))
        }

        OnDeviceTranscriber.transcribeAudioFile(this@VoiceInputMethodService, audioFile)
    }

    private fun commitTextToInput(text: String) {
        val ic = currentInputConnection ?: return
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) {
            ic.commitText("$trimmed ", 1)
        }
    }

    private fun updateUiState(state: UiState) {
        val selectedFileName = PreferencesManager.getSelectedModelFileName(this)
        val modelInfo = ModelManager.getModelInfoByFileName(selectedFileName)
        val mode = PreferencesManager.getEngineMode(this)
        val modelLabel = if (mode == PreferencesManager.EngineMode.EDGE_ON_DEVICE) modelInfo.name else "Remote Server"

        when (state) {
            is UiState.IDLE -> {
                tvStatus?.text = getString(R.string.status_idle)
                tvPrompt?.text = "Hold or tap to talk"
                btnMic?.setBackgroundResource(R.drawable.bg_mic_button_idle)
                btnMic?.setImageResource(R.drawable.ic_mic)
                btnMic?.isEnabled = true
                progressBar?.visibility = View.GONE
                btnCancelTranscription?.visibility = View.GONE
                stopAmplitudeMonitor()
            }
            is UiState.RECORDING -> {
                tvStatus?.text = "Listening ($modelLabel)..."
                tvPrompt?.text = "Release or tap to finish"
                btnMic?.setBackgroundResource(R.drawable.bg_mic_button_recording)
                btnMic?.setImageResource(R.drawable.ic_mic)
                btnMic?.isEnabled = true
                progressBar?.visibility = View.GONE
                btnCancelTranscription?.visibility = View.GONE
            }
            is UiState.TRANSCRIBING -> {
                tvStatus?.text = "Transcribing ($modelLabel)..."
                tvPrompt?.text = "Processing audio..."
                btnMic?.setBackgroundResource(R.drawable.bg_mic_button_idle)
                btnMic?.isEnabled = false
                progressBar?.visibility = View.VISIBLE
                btnCancelTranscription?.visibility = View.VISIBLE
                stopAmplitudeMonitor()
            }
            is UiState.ERROR -> {
                tvStatus?.text = "Error: ${state.message}"
                tvPrompt?.text = "Tap mic to retry"
                btnMic?.setBackgroundResource(R.drawable.bg_mic_button_idle)
                btnMic?.setImageResource(R.drawable.ic_mic)
                btnMic?.isEnabled = true
                progressBar?.visibility = View.GONE
                btnCancelTranscription?.visibility = View.GONE
                stopAmplitudeMonitor()
            }
        }
    }

    companion object {
        private const val TAP_THRESHOLD_MS = 250L
        private const val SPEECH_DETECTION_THRESHOLD_AMP = 1500
        private const val SILENCE_THRESHOLD_AMP = 1200
        private const val SILENCE_AUTO_STOP_DURATION_MS = 1800L
    }
}
