package com.t1erno.whisperkeyboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.t1erno.whisperkeyboard.audio.AudioRecorderManager
import com.t1erno.whisperkeyboard.nativeengine.OnDeviceTranscriber
import com.t1erno.whisperkeyboard.network.WhisperApiClient
import com.t1erno.whisperkeyboard.ui.ProgressiveBackspace
import com.t1erno.whisperkeyboard.ui.PunctuationKeyManager
import com.t1erno.whisperkeyboard.ui.VibrationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VoiceInputMethodService : InputMethodService() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + serviceJob)

    private lateinit var audioRecorderManager: AudioRecorderManager
    private lateinit var punctuationKeyManager: PunctuationKeyManager
    private lateinit var progressiveBackspace: ProgressiveBackspace

    private var toneGenerator: ToneGenerator? = null

    private var btnMic: ImageButton? = null
    private var tvStatus: TextView? = null
    private var tvPrompt: TextView? = null
    private var progressBar: ProgressBar? = null
    private var btnCancelTranscription: Button? = null

    private var isTranscribing = false
    private var transcriptionJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        audioRecorderManager = AudioRecorderManager(this)
        punctuationKeyManager = PunctuationKeyManager { symbol ->
            currentInputConnection?.commitText(symbol, 1)
        }
        progressiveBackspace = ProgressiveBackspace {
            currentInputConnection
        }
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
        } catch (_: Exception) {}
    }

    override fun onCreateInputView(): View {
        val contextThemeWrapper = ContextThemeWrapper(this, R.style.Theme_WhisperKeyboard)
        val themedInflater = layoutInflater.cloneInContext(contextThemeWrapper)
        val inputView = themedInflater.inflate(R.layout.input_view, null)

        btnMic = inputView.findViewById(R.id.btn_mic)
        tvStatus = inputView.findViewById(R.id.tv_status)
        tvPrompt = inputView.findViewById(R.id.tv_prompt)
        progressBar = inputView.findViewById(R.id.progress_bar)
        btnCancelTranscription = inputView.findViewById(R.id.btn_cancel_transcription)

        btnCancelTranscription?.setOnClickListener {
            cancelTranscription()
        }

        setupMicButton()
        punctuationKeyManager.setupPunctuationKeys(inputView, contextThemeWrapper)
        setupUtilityButtons(inputView)

        updateUiState(UiState.IDLE)
        return inputView
    }

    private fun setupMicButton() {
        btnMic?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    VibrationHelper.vibrateKey(this, 35L)
                    playMicBeep()
                    if (!audioRecorderManager.isRecording && !isTranscribing) {
                        if (hasRecordAudioPermission()) {
                            startRecording()
                        } else {
                            promptForPermission()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (audioRecorderManager.isRecording && !isTranscribing) {
                        VibrationHelper.vibrateKey(this, 25L)
                        stopAndTranscribe()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun playMicBeep() {
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
        Toast.makeText(this, "Microphone permission required. Launching setup...", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        startActivity(intent)
    }

    private fun startRecording() {
        val file = audioRecorderManager.startRecording()
        if (file != null) {
            updateUiState(UiState.RECORDING)
        } else {
            updateUiState(UiState.ERROR("Failed to access microphone"))
        }
    }

    private fun cancelTranscription() {
        transcriptionJob?.cancel()
        transcriptionJob = null
        isTranscribing = false
        audioRecorderManager.releaseRecorder()
        updateUiState(UiState.IDLE)
        Toast.makeText(this, "Transcription cancelled", Toast.LENGTH_SHORT).show()
    }

    private fun stopAndTranscribe() {
        isTranscribing = true
        updateUiState(UiState.TRANSCRIBING)

        transcriptionJob = serviceScope.launch(Dispatchers.IO) {
            val recordedFile = audioRecorderManager.stopRecording()

            if (recordedFile != null && recordedFile.exists() && recordedFile.length() > 1000) {
                try {
                    val engineMode = PreferencesManager.getEngineMode(applicationContext)
                    val result = if (engineMode == PreferencesManager.EngineMode.REMOTE_SERVER) {
                        WhisperApiClient.uploadAudio(applicationContext, recordedFile)
                    } else {
                        OnDeviceTranscriber.transcribeAudioFile(applicationContext, recordedFile)
                    }

                    withContext(Dispatchers.Main) {
                        result.fold(
                            onSuccess = { text ->
                                injectText(text)
                                updateUiState(UiState.IDLE)
                            },
                            onFailure = { error ->
                                updateUiState(UiState.ERROR(error.message ?: "Transcription error"))
                            }
                        )
                    }
                } catch (e: CancellationException) {
                    withContext(Dispatchers.Main) {
                        updateUiState(UiState.IDLE)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        updateUiState(UiState.ERROR(e.message ?: "Error transcribing"))
                    }
                } finally {
                    recordedFile.delete()
                    isTranscribing = false
                    transcriptionJob = null
                }
            } else {
                withContext(Dispatchers.Main) {
                    updateUiState(UiState.IDLE)
                }
                isTranscribing = false
                transcriptionJob = null
            }
        }
    }

    private fun injectText(text: String) {
        val ic = currentInputConnection ?: return
        ic.commitText(text, 1)
    }

    private fun updateUiState(state: UiState) {
        when (state) {
            UiState.IDLE -> {
                val mode = PreferencesManager.getEngineMode(applicationContext)
                val engineLabel = if (mode == PreferencesManager.EngineMode.REMOTE_SERVER) "Server" else "Offline Edge"
                tvStatus?.text = "Listening ($engineLabel)..."
                tvPrompt?.text = "Hold to talk"
                btnMic?.setBackgroundResource(R.drawable.bg_mic_button_idle)
                progressBar?.visibility = View.GONE
                btnCancelTranscription?.visibility = View.GONE
                btnMic?.isEnabled = true
            }
            UiState.RECORDING -> {
                tvStatus?.text = "Listening..."
                tvPrompt?.text = "Release to send"
                btnMic?.setBackgroundResource(R.drawable.bg_mic_button_recording)
                progressBar?.visibility = View.GONE
                btnCancelTranscription?.visibility = View.GONE
                btnMic?.isEnabled = true
            }
            UiState.TRANSCRIBING -> {
                val mode = PreferencesManager.getEngineMode(applicationContext)
                val statusText = if (mode == PreferencesManager.EngineMode.REMOTE_SERVER) "Transcribing (Remote)..." else "Transcribing (Edge NDK)..."
                tvStatus?.text = statusText
                tvPrompt?.text = "Processing audio..."
                progressBar?.visibility = View.VISIBLE
                btnCancelTranscription?.visibility = View.VISIBLE
                btnMic?.isEnabled = false
            }
            is UiState.ERROR -> {
                tvStatus?.text = state.message
                tvPrompt?.text = "Tap mic to retry"
                btnMic?.setBackgroundResource(R.drawable.bg_mic_button_idle)
                progressBar?.visibility = View.GONE
                btnCancelTranscription?.visibility = View.GONE
                btnMic?.isEnabled = true

                Handler(Looper.getMainLooper()).postDelayed({
                    if (!audioRecorderManager.isRecording && !isTranscribing) {
                        updateUiState(UiState.IDLE)
                    }
                }, 2500)
            }
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        progressiveBackspace.stop()
        if (audioRecorderManager.isRecording) {
            audioRecorderManager.stopRecording()
            updateUiState(UiState.IDLE)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        progressiveBackspace.stop()
        transcriptionJob?.cancel()
        audioRecorderManager.releaseRecorder()
        OnDeviceTranscriber.releaseContext()
        try {
            toneGenerator?.release()
        } catch (_: Exception) {}
        toneGenerator = null
        serviceJob.cancel()
    }

    private sealed class UiState {
        object IDLE : UiState()
        object RECORDING : UiState()
        object TRANSCRIBING : UiState()
        data class ERROR(val message: String) : UiState()
    }
}
