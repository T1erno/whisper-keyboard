@file:Suppress("SpellCheckingInspection")

package com.t1erno.whisperkeyboard

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.t1erno.whisperkeyboard.nativeengine.ModelManager
import com.t1erno.whisperkeyboard.nativeengine.OnDeviceTranscriber
import com.t1erno.whisperkeyboard.network.TcpPingHelper
import com.t1erno.whisperkeyboard.network.TcpPingHelper.toHumanReadablePingError
import com.t1erno.whisperkeyboard.network.WhisperApiClient
import com.t1erno.whisperkeyboard.ui.VibrationHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

@SuppressLint("SetTextI18n")
class MainActivity : AppCompatActivity() {

    private lateinit var etServerUrl: EditText
    private lateinit var btnServerUrlHistory: ImageButton
    private lateinit var cardRemoteSettings: MaterialCardView
    private lateinit var vStatusDot: View
    private lateinit var tvPingInfo: TextView
    private lateinit var btnSaveUrl: Button
    private lateinit var switchHaptic: SwitchMaterial
    private lateinit var switchAutoSendSilence: SwitchMaterial

    private lateinit var toggleEngineMode: MaterialButtonToggleGroup
    private lateinit var tvEngineModeDesc: TextView

    // Remote Server Models Card Views
    private lateinit var cardRemoteModels: MaterialCardView
    private lateinit var tvRemoteModelStatus: TextView
    private lateinit var rgRemoteModels: RadioGroup
    private lateinit var rbRemoteLargeV3: RadioButton
    private lateinit var rbRemoteLargeTurbo: RadioButton
    private lateinit var rbRemoteMedium: RadioButton
    private lateinit var rbRemoteSmall: RadioButton
    private lateinit var rbRemoteBase: RadioButton
    private lateinit var rbRemoteTiny: RadioButton
    private lateinit var rbRemoteCustom: RadioButton
    private lateinit var layoutCustomModelInput: LinearLayout
    private lateinit var etCustomRemoteModel: EditText
    private lateinit var btnCustomRemoteModelHistory: ImageButton

    // Offline Edge Models Card Views
    private lateinit var cardOfflineModels: MaterialCardView
    private lateinit var rgOfflineModels: RadioGroup
    private lateinit var rbOfflineLargeV3: RadioButton
    private lateinit var rbOfflineLargeTurbo: RadioButton
    private lateinit var rbOfflineMedium: RadioButton
    private lateinit var rbOfflineSmall: RadioButton
    private lateinit var rbOfflineBase: RadioButton
    private lateinit var rbOfflineTiny: RadioButton
    private lateinit var rbOfflineCustom: RadioButton
    private lateinit var layoutCustomOfflineModelInput: LinearLayout
    private lateinit var etCustomOfflineModel: EditText
    private lateinit var btnCustomOfflineModelHistory: ImageButton
    private lateinit var tvOfflineModelStatus: TextView
    private lateinit var layoutActiveDownloads: LinearLayout
    private lateinit var btnDownloadModel: MaterialButton
    private lateinit var btnDeleteModel: MaterialButton

    private lateinit var tvStep1Status: TextView
    private lateinit var btnGrantPermission: Button

    private lateinit var tvStep2Status: TextView
    private lateinit var btnEnableKeyboard: Button

    private lateinit var tvStep3Status: TextView
    private lateinit var btnSelectKeyboard: Button

    private lateinit var etTestInput: EditText

    private var pingJob: Job? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        checkKeyboardStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etServerUrl = findViewById(R.id.et_server_url)
        btnServerUrlHistory = findViewById(R.id.btn_server_url_history)
        cardRemoteSettings = findViewById(R.id.card_remote_settings)
        vStatusDot = findViewById(R.id.v_status_dot)
        tvPingInfo = findViewById(R.id.tv_ping_info)
        btnSaveUrl = findViewById(R.id.btn_save_url)
        switchHaptic = findViewById(R.id.switch_haptic)
        switchAutoSendSilence = findViewById(R.id.switch_auto_send_silence)

        toggleEngineMode = findViewById(R.id.toggle_engine_mode)
        tvEngineModeDesc = findViewById(R.id.tv_engine_mode_desc)

        // Remote Models Card
        cardRemoteModels = findViewById(R.id.card_remote_models)
        tvRemoteModelStatus = findViewById(R.id.tv_remote_model_status)
        rgRemoteModels = findViewById(R.id.rg_remote_models)
        rbRemoteLargeV3 = findViewById(R.id.rb_remote_large_v3)
        rbRemoteLargeTurbo = findViewById(R.id.rb_remote_large_turbo)
        rbRemoteMedium = findViewById(R.id.rb_remote_medium)
        rbRemoteSmall = findViewById(R.id.rb_remote_small)
        rbRemoteBase = findViewById(R.id.rb_remote_base)
        rbRemoteTiny = findViewById(R.id.rb_remote_tiny)
        rbRemoteCustom = findViewById(R.id.rb_remote_custom)
        layoutCustomModelInput = findViewById(R.id.layout_custom_model_input)
        etCustomRemoteModel = findViewById(R.id.et_custom_remote_model)
        btnCustomRemoteModelHistory = findViewById(R.id.btn_custom_remote_model_history)

        // Offline Models Card
        cardOfflineModels = findViewById(R.id.card_offline_models)
        rgOfflineModels = findViewById(R.id.rg_offline_models)
        rbOfflineLargeV3 = findViewById(R.id.rb_offline_large_v3)
        rbOfflineLargeTurbo = findViewById(R.id.rb_offline_large_turbo)
        rbOfflineMedium = findViewById(R.id.rb_offline_medium)
        rbOfflineSmall = findViewById(R.id.rb_offline_small)
        rbOfflineBase = findViewById(R.id.rb_offline_base)
        rbOfflineTiny = findViewById(R.id.rb_offline_tiny)
        rbOfflineCustom = findViewById(R.id.rb_offline_custom)
        layoutCustomOfflineModelInput = findViewById(R.id.layout_custom_offline_model_input)
        etCustomOfflineModel = findViewById(R.id.et_custom_offline_model)
        btnCustomOfflineModelHistory = findViewById(R.id.btn_custom_offline_model_history)
        tvOfflineModelStatus = findViewById(R.id.tv_offline_model_status)
        layoutActiveDownloads = findViewById(R.id.layout_active_downloads)
        btnDownloadModel = findViewById(R.id.btn_download_model)
        btnDeleteModel = findViewById(R.id.btn_delete_model)

        tvStep1Status = findViewById(R.id.tv_step1_status)
        btnGrantPermission = findViewById(R.id.btn_grant_permission)

        tvStep2Status = findViewById(R.id.tv_step2_status)
        btnEnableKeyboard = findViewById(R.id.btn_enable_keyboard)

        tvStep3Status = findViewById(R.id.tv_step3_status)
        btnSelectKeyboard = findViewById(R.id.btn_select_keyboard)

        etTestInput = findViewById(R.id.et_test_input)
        setupTestInputScrolling()

        val currentUrl = PreferencesManager.getServerUrl(this)
        etServerUrl.setText(currentUrl)

        handlePermissionIntent(intent)
        setupEngineModeUI()
        setupRemoteModelSelectionUI()
        setupOfflineModelSelectionUI()

        switchAutoSendSilence.isChecked = PreferencesManager.isAutoSendOnSilenceEnabled(this)
        switchAutoSendSilence.setOnCheckedChangeListener { _, isChecked ->
            PreferencesManager.setAutoSendOnSilenceEnabled(this, isChecked)
            if (PreferencesManager.isHapticEnabled(this)) {
                VibrationHelper.vibrateKey(this, 30L)
            }
        }

        switchHaptic.isChecked = PreferencesManager.isHapticEnabled(this)
        switchHaptic.setOnCheckedChangeListener { _, isChecked ->
            PreferencesManager.setHapticEnabled(this, isChecked)
            if (isChecked) {
                VibrationHelper.vibrateKey(this, 30L)
            }
        }

        btnSaveUrl.setOnClickListener {
            val urlInput = etServerUrl.text.toString().trim()
            val validationResult = TcpPingHelper.normalizeAndValidateUrl(urlInput)
            if (validationResult.isFailure) {
                val errorMsg = validationResult.exceptionOrNull()?.message ?: "Please enter a valid URL"
                Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
            } else if (urlInput.startsWith("http://", ignoreCase = true)) {
                showHttpWarningDialog(urlInput)
            } else {
                saveAndApplyServerUrl(urlInput)
            }
        }

        btnServerUrlHistory.setOnClickListener {
            showHistoryDialog(
                title = "Server URL History",
                historyKey = PreferencesManager.KEY_HISTORY_SERVER_URL,
                currentValueGetter = { etServerUrl.text.toString().trim() },
                onItemSelected = { selectedUrl ->
                    val validationResult = TcpPingHelper.normalizeAndValidateUrl(selectedUrl)
                    if (validationResult.isFailure) {
                        val errorMsg = validationResult.exceptionOrNull()?.message ?: "Please enter a valid URL"
                        Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
                    } else if (selectedUrl.startsWith("http://", ignoreCase = true)) {
                        showHttpWarningDialog(selectedUrl)
                    } else {
                        saveAndApplyServerUrl(selectedUrl)
                    }
                },
                onItemModified = { oldVal, newVal ->
                    if (etServerUrl.text.toString().trim().equals(oldVal, ignoreCase = true)) {
                        saveAndApplyServerUrl(newVal)
                    }
                }
            )
        }

        etServerUrl.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                btnSaveUrl.performClick()
                true
            } else {
                false
            }
        }

        btnGrantPermission.setOnClickListener {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        btnEnableKeyboard.setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        btnSelectKeyboard.setOnClickListener {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTestInputScrolling() {
        // Keep default ArrowKeyMovementMethod to preserve full text selection, handles & context action bar
        etTestInput.setOnTouchListener { v, event ->
            if (v.hasFocus()) {
                v.parent.requestDisallowInterceptTouchEvent(true)
                if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                    v.parent.requestDisallowInterceptTouchEvent(false)
                }
            }
            false // Return false so EditText handles native touch, cursor positioning & text selection
        }
    }

    private fun showHttpWarningDialog(httpUrl: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("⚠️ Unencrypted Connection (HTTP)")
            .setMessage("You are connecting using unencrypted HTTP ($httpUrl).\n\nAudio recordings and transcriptions will be transmitted over the network in plain text without SSL/TLS encryption.\n\nDo you want to proceed anyway or switch to HTTPS?")
            .setPositiveButton("Proceed (HTTP)") { _, _ ->
                saveAndApplyServerUrl(httpUrl)
            }
            .setNegativeButton("Use HTTPS Instead") { _, _ ->
                val httpsUrl = httpUrl.replaceFirst("http://", "https://", ignoreCase = true)
                saveAndApplyServerUrl(httpsUrl)
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun saveAndApplyServerUrl(url: String) {
        PreferencesManager.saveServerUrl(this, url)
        val updatedUrl = PreferencesManager.getServerUrl(this)
        etServerUrl.setText(updatedUrl)
        PreferencesManager.addToHistory(this, PreferencesManager.KEY_HISTORY_SERVER_URL, updatedUrl)
        Toast.makeText(this, "Server URL saved!", Toast.LENGTH_SHORT).show()
        startPeriodicTcpPing()
        fetchRemoteServerModels()
    }

    private fun fetchRemoteServerModels() {
        if (PreferencesManager.getEngineMode(this) != PreferencesManager.EngineMode.REMOTE_SERVER) return
        lifecycleScope.launch {
            WhisperApiClient.fetchServerModels(this@MainActivity)
            updateRemoteModelStatusUI()
        }
    }

    private fun setupEngineModeUI() {
        val currentMode = PreferencesManager.getEngineMode(this)
        val isEdge = currentMode == PreferencesManager.EngineMode.EDGE_ON_DEVICE

        if (isEdge) {
            toggleEngineMode.check(R.id.btn_mode_edge)
        } else {
            toggleEngineMode.check(R.id.btn_mode_remote)
        }

        updateEngineModeViews(isEdge)

        toggleEngineMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val isEdgeSelected = (checkedId == R.id.btn_mode_edge)
                val newMode = if (isEdgeSelected) PreferencesManager.EngineMode.EDGE_ON_DEVICE else PreferencesManager.EngineMode.REMOTE_SERVER
                PreferencesManager.setEngineMode(this, newMode)
                VibrationHelper.vibrateKey(this, 30L)
                updateEngineModeViews(isEdgeSelected)
            }
        }
    }

    private fun updateEngineModeViews(isEdge: Boolean) {
        if (isEdge) {
            tvEngineModeDesc.text = "Transcribes 100% offline on-device via whisper.cpp NDK"
            tvEngineModeDesc.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))

            // Disable & Grey out Remote Server settings
            etServerUrl.isEnabled = false
            btnSaveUrl.isEnabled = false
            btnServerUrlHistory.isEnabled = false
            cardRemoteSettings.alpha = 0.5f

            // Decouple UI: Show Offline Edge Model Selection
            cardRemoteModels.visibility = View.GONE
            cardOfflineModels.visibility = View.VISIBLE
            updateOfflineModelStatusUI()
            startPeriodicTcpPing()
        } else {
            tvEngineModeDesc.text = "Transcribes online via your self-hosted Whisper API server"
            tvEngineModeDesc.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))

            // Enable & Restore Remote Server settings
            etServerUrl.isEnabled = true
            btnSaveUrl.isEnabled = true
            btnServerUrlHistory.isEnabled = true
            cardRemoteSettings.alpha = 1.0f

            // Decouple UI: Show Remote Server Model Selection
            cardRemoteModels.visibility = View.VISIBLE
            cardOfflineModels.visibility = View.GONE
            updateRemoteModelStatusUI()
            startPeriodicTcpPing()
            fetchRemoteServerModels()
        }
    }

    private fun setupRemoteModelSelectionUI() {
        val currentRemote = PreferencesManager.getRemoteModel(this)
        val isCustom = PreferencesManager.isCustomRemoteModel(this)

        if (isCustom) {
            rbRemoteCustom.isChecked = true
            layoutCustomModelInput.visibility = View.VISIBLE
            rgRemoteModels.clearCheck()
        } else {
            rbRemoteCustom.isChecked = false
            layoutCustomModelInput.visibility = View.GONE
            when (currentRemote) {
                "large-v3" -> rbRemoteLargeV3.isChecked = true
                "medium" -> rbRemoteMedium.isChecked = true
                "small" -> rbRemoteSmall.isChecked = true
                "base" -> rbRemoteBase.isChecked = true
                "tiny" -> rbRemoteTiny.isChecked = true
                else -> rbRemoteLargeTurbo.isChecked = true
            }
        }

        rgRemoteModels.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId != -1) {
                rbRemoteCustom.isChecked = false
                layoutCustomModelInput.visibility = View.GONE
                PreferencesManager.setIsCustomRemoteModel(this, false)

                val selectedKey = when (checkedId) {
                    R.id.rb_remote_large_v3 -> "large-v3"
                    R.id.rb_remote_medium -> "medium"
                    R.id.rb_remote_small -> "small"
                    R.id.rb_remote_base -> "base"
                    R.id.rb_remote_tiny -> "tiny"
                    else -> "large-v3-turbo"
                }

                PreferencesManager.setRemoteModel(this, selectedKey)
                VibrationHelper.vibrateKey(this, 20L)
                updateRemoteModelStatusUI()
            }
        }

        rbRemoteCustom.setOnClickListener {
            rgRemoteModels.clearCheck()
            rbRemoteCustom.isChecked = true
            layoutCustomModelInput.visibility = View.VISIBLE
            PreferencesManager.setIsCustomRemoteModel(this, true)
            VibrationHelper.vibrateKey(this, 20L)
            updateRemoteModelStatusUI()
        }

        etCustomRemoteModel.setText(PreferencesManager.getCustomRemoteModel(this))
        etCustomRemoteModel.doAfterTextChanged { text ->
            val custom = text?.toString()?.trim() ?: ""
            PreferencesManager.setCustomRemoteModel(this, custom)
            if (rbRemoteCustom.isChecked) {
                PreferencesManager.setIsCustomRemoteModel(this, true)
                updateRemoteModelStatusUI()
            }
        }

        etCustomRemoteModel.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val custom = etCustomRemoteModel.text.toString().trim()
                if (custom.isNotBlank()) {
                    PreferencesManager.addToHistory(this, PreferencesManager.KEY_HISTORY_REMOTE_MODEL, custom)
                }
            }
            false
        }

        etCustomRemoteModel.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val custom = etCustomRemoteModel.text.toString().trim()
                if (custom.isNotBlank()) {
                    PreferencesManager.addToHistory(this, PreferencesManager.KEY_HISTORY_REMOTE_MODEL, custom)
                }
            }
        }

        btnCustomRemoteModelHistory.setOnClickListener {
            showHistoryDialog(
                title = "Remote Model History",
                historyKey = PreferencesManager.KEY_HISTORY_REMOTE_MODEL,
                currentValueGetter = { etCustomRemoteModel.text.toString().trim() },
                onItemSelected = { selectedModel ->
                    etCustomRemoteModel.setText(selectedModel)
                    PreferencesManager.setCustomRemoteModel(this, selectedModel)
                    PreferencesManager.setIsCustomRemoteModel(this, true)
                    rbRemoteCustom.isChecked = true
                    layoutCustomModelInput.visibility = View.VISIBLE
                    rgRemoteModels.clearCheck()
                    updateRemoteModelStatusUI()
                    VibrationHelper.vibrateKey(this, 20L)
                    Toast.makeText(this, "Selected: $selectedModel", Toast.LENGTH_SHORT).show()
                },
                onItemModified = { oldVal, newVal ->
                    if (etCustomRemoteModel.text.toString().trim().equals(oldVal, ignoreCase = true)) {
                        etCustomRemoteModel.setText(newVal)
                        PreferencesManager.setCustomRemoteModel(this, newVal)
                        updateRemoteModelStatusUI()
                    }
                }
            )
        }

        updateRemoteModelStatusUI()
    }

    private fun updateRemoteModelStatusUI() {
        val currentRemote = PreferencesManager.getRemoteModel(this)
        val isCustom = PreferencesManager.isCustomRemoteModel(this)
        val modelsResponse = WhisperApiClient.lastServerModelsResponse

        val remoteModelsMap = listOf(
            rbRemoteLargeV3 to ("large-v3" to "Large v3 • Maximum precision"),
            rbRemoteLargeTurbo to ("large-v3-turbo" to "Large v3 Turbo • Recommended • Fast & High Precision"),
            rbRemoteMedium to ("medium" to "Medium • High accuracy"),
            rbRemoteSmall to ("small" to "Small • Balanced speed & memory"),
            rbRemoteBase to ("base" to "Base • Lightweight"),
            rbRemoteTiny to ("tiny" to "Tiny • Fastest, low memory")
        )

        for ((rb, pair) in remoteModelsMap) {
            val (key, baseText) = pair
            val isLoaded = modelsResponse?.loadedModels?.any { it.equals(key, ignoreCase = true) } == true
            val statusTag = if (isLoaded) " • [Ready on Server]" else ""
            rb.text = "$baseText$statusTag"
        }

        if (isCustom) {
            val custom = PreferencesManager.getCustomRemoteModel(this).trim()
            val isBareRepo = custom.contains("/") && !custom.contains(".") && !listOf("large", "medium", "small", "base", "tiny").any { custom.contains(it, ignoreCase = true) }

            if (custom.isBlank()) {
                tvRemoteModelStatus.text = "Enter custom model URL or repo/file above"
                tvRemoteModelStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            } else if (isBareRepo) {
                tvRemoteModelStatus.text = "⚠️ Especifica el archivo .bin (ej. $custom/ggml-large-v2.bin)"
                tvRemoteModelStatus.setTextColor(ContextCompat.getColor(this, R.color.mic_recording_start))
            } else {
                val clean = if (custom.contains("/")) custom.substringAfterLast("/") else custom
                tvRemoteModelStatus.text = "Active Remote Model: $clean"
                tvRemoteModelStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_purple))
            }
        } else {
            val displayName = when (currentRemote) {
                "large-v3" -> "Large v3"
                "medium" -> "Medium"
                "small" -> "Small"
                "base" -> "Base"
                "tiny" -> "Tiny"
                else -> "Large v3 Turbo"
            }

            if (modelsResponse != null) {
                val isLoaded = modelsResponse.loadedModels.any { it.equals(currentRemote, ignoreCase = true) }
                if (isLoaded) {
                    tvRemoteModelStatus.text = "✓ Server Ready: $displayName (Cached in VRAM)"
                    tvRemoteModelStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_purple))
                } else {
                    val isAvailable = modelsResponse.availableModels.any { it.equals(currentRemote, ignoreCase = true) }
                    if (isAvailable) {
                        tvRemoteModelStatus.text = "Remote Model Selected: $displayName"
                        tvRemoteModelStatus.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
                    } else {
                        tvRemoteModelStatus.text = "⚠️ Model not loaded on server: $displayName"
                        tvRemoteModelStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                    }
                }
            } else {
                tvRemoteModelStatus.text = "Model: $displayName (Server offline / checking...)"
                tvRemoteModelStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            }
        }
    }

    private fun setupOfflineModelSelectionUI() {
        val currentModel = PreferencesManager.getSelectedOfflineModel(this)
        val isCustom = PreferencesManager.isCustomOfflineModel(this)

        if (isCustom) {
            rbOfflineCustom.isChecked = true
            layoutCustomOfflineModelInput.visibility = View.VISIBLE
            rgOfflineModels.clearCheck()
        } else {
            rbOfflineCustom.isChecked = false
            layoutCustomOfflineModelInput.visibility = View.GONE
            when (currentModel) {
                ModelManager.MODEL_LARGE_V3.fileName -> rbOfflineLargeV3.isChecked = true
                ModelManager.MODEL_MEDIUM.fileName -> rbOfflineMedium.isChecked = true
                ModelManager.MODEL_SMALL.fileName -> rbOfflineSmall.isChecked = true
                ModelManager.MODEL_BASE.fileName -> rbOfflineBase.isChecked = true
                ModelManager.MODEL_TINY.fileName -> rbOfflineTiny.isChecked = true
                else -> rbOfflineLargeTurbo.isChecked = true
            }
        }

        rgOfflineModels.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId != -1) {
                rbOfflineCustom.isChecked = false
                layoutCustomOfflineModelInput.visibility = View.GONE
                PreferencesManager.setIsCustomOfflineModel(this, false)

                val selectedModel = when (checkedId) {
                    R.id.rb_offline_large_v3 -> ModelManager.MODEL_LARGE_V3
                    R.id.rb_offline_medium -> ModelManager.MODEL_MEDIUM
                    R.id.rb_offline_small -> ModelManager.MODEL_SMALL
                    R.id.rb_offline_base -> ModelManager.MODEL_BASE
                    R.id.rb_offline_tiny -> ModelManager.MODEL_TINY
                    else -> ModelManager.MODEL_LARGE_V3_TURBO
                }

                PreferencesManager.setSelectedOfflineModel(this, selectedModel.fileName)
                OnDeviceTranscriber.releaseContext()
                VibrationHelper.vibrateKey(this, 20L)
                updateOfflineModelStatusUI()
            }
        }

        rbOfflineCustom.setOnClickListener {
            rgOfflineModels.clearCheck()
            rbOfflineCustom.isChecked = true
            layoutCustomOfflineModelInput.visibility = View.VISIBLE
            PreferencesManager.setIsCustomOfflineModel(this, true)
            VibrationHelper.vibrateKey(this, 20L)
            updateOfflineModelStatusUI()
        }

        etCustomOfflineModel.setText(PreferencesManager.getCustomOfflineModel(this))
        etCustomOfflineModel.doAfterTextChanged { text ->
            val custom = text?.toString()?.trim() ?: ""
            PreferencesManager.setCustomOfflineModel(this, custom)
            if (rbOfflineCustom.isChecked) {
                PreferencesManager.setIsCustomOfflineModel(this, true)
                updateOfflineModelStatusUI()
            }
        }

        etCustomOfflineModel.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val custom = etCustomOfflineModel.text.toString().trim()
                if (custom.isNotBlank()) {
                    PreferencesManager.addToHistory(this, PreferencesManager.KEY_HISTORY_OFFLINE_MODEL, custom)
                }
            }
            false
        }

        etCustomOfflineModel.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val custom = etCustomOfflineModel.text.toString().trim()
                if (custom.isNotBlank()) {
                    PreferencesManager.addToHistory(this, PreferencesManager.KEY_HISTORY_OFFLINE_MODEL, custom)
                }
            }
        }

        btnCustomOfflineModelHistory.setOnClickListener {
            showHistoryDialog(
                title = "Offline Model History",
                historyKey = PreferencesManager.KEY_HISTORY_OFFLINE_MODEL,
                currentValueGetter = { etCustomOfflineModel.text.toString().trim() },
                onItemSelected = { selectedModel ->
                    etCustomOfflineModel.setText(selectedModel)
                    PreferencesManager.setCustomOfflineModel(this, selectedModel)
                    PreferencesManager.setIsCustomOfflineModel(this, true)
                    rbOfflineCustom.isChecked = true
                    layoutCustomOfflineModelInput.visibility = View.VISIBLE
                    rgOfflineModels.clearCheck()
                    updateOfflineModelStatusUI()
                    VibrationHelper.vibrateKey(this, 20L)
                    Toast.makeText(this, "Selected: $selectedModel", Toast.LENGTH_SHORT).show()
                },
                onItemModified = { oldVal, newVal ->
                    if (etCustomOfflineModel.text.toString().trim().equals(oldVal, ignoreCase = true)) {
                        etCustomOfflineModel.setText(newVal)
                        PreferencesManager.setCustomOfflineModel(this, newVal)
                        updateOfflineModelStatusUI()
                    }
                }
            )
        }

        btnDownloadModel.setOnClickListener {
            startModelDownload()
        }

        btnDeleteModel.setOnClickListener {
            val isCustomActive = PreferencesManager.isCustomOfflineModel(this)
            val selectedFileName = PreferencesManager.getSelectedOfflineModel(this)
            val customInput = PreferencesManager.getCustomOfflineModel(this)
            val modelInfo = if (isCustomActive) {
                ModelManager.buildCustomModelInfo(customInput.ifEmpty { selectedFileName })
            } else {
                ModelManager.getModelInfoByFileName(selectedFileName)
            }
            val sizeFormatted = ModelManager.getDownloadedModelSizeFormatted(this, modelInfo.fileName)

            MaterialAlertDialogBuilder(this)
                .setTitle("Delete ${modelInfo.name}?")
                .setMessage("Delete local model file ($sizeFormatted) to free storage space on your phone?")
                .setPositiveButton("Delete") { _, _ ->
                    ModelManager.deleteModel(this, modelInfo.fileName)
                    OnDeviceTranscriber.releaseContext()
                    Toast.makeText(this, "${modelInfo.name} deleted to free space", Toast.LENGTH_SHORT).show()
                    updateOfflineModelStatusUI()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        updateOfflineModelStatusUI()
    }

    private fun updateOfflineModelStatusUI() {
        val isCustom = PreferencesManager.isCustomOfflineModel(this)
        val selectedFileName = PreferencesManager.getSelectedOfflineModel(this)
        val customInput = PreferencesManager.getCustomOfflineModel(this)
        val modelInfo = if (isCustom) {
            ModelManager.buildCustomModelInfo(customInput.ifEmpty { selectedFileName })
        } else {
            ModelManager.getModelInfoByFileName(selectedFileName)
        }
        val isDownloaded = ModelManager.isModelDownloaded(this, modelInfo.fileName)
        val isDownloading = ModelManager.isModelDownloading(modelInfo.fileName)
        val progress = ModelManager.getDownloadProgress(modelInfo.fileName)

        val modelsMap = listOf(
            rbOfflineLargeV3 to ModelManager.MODEL_LARGE_V3,
            rbOfflineLargeTurbo to ModelManager.MODEL_LARGE_V3_TURBO,
            rbOfflineMedium to ModelManager.MODEL_MEDIUM,
            rbOfflineSmall to ModelManager.MODEL_SMALL,
            rbOfflineBase to ModelManager.MODEL_BASE,
            rbOfflineTiny to ModelManager.MODEL_TINY
        )

        for ((rb, model) in modelsMap) {
            val hasFile = ModelManager.isModelDownloaded(this, model.fileName)
            val statusTag = if (hasFile) " • [Downloaded]" else ""
            rb.text = "${model.name} • ${model.description}$statusTag"
        }

        if (isCustom && customInput.isBlank()) {
            tvOfflineModelStatus.text = "Enter custom Hugging Face repo ID or model URL above"
            tvOfflineModelStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            tvOfflineModelStatus.visibility = View.VISIBLE
            btnDownloadModel.visibility = View.GONE
            btnDeleteModel.visibility = View.GONE
        } else if (isDownloaded) {
            val size = ModelManager.getDownloadedModelSizeFormatted(this, modelInfo.fileName)
            tvOfflineModelStatus.text = "✓ Offline model ready on device: ${modelInfo.name} ($size)"
            tvOfflineModelStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_purple))
            tvOfflineModelStatus.visibility = View.VISIBLE
            btnDownloadModel.visibility = View.GONE
            btnDeleteModel.visibility = View.VISIBLE
            btnDeleteModel.text = "DELETE MODEL FILE ($size)"
        } else if (isDownloading) {
            tvOfflineModelStatus.visibility = View.GONE
            btnDownloadModel.visibility = View.VISIBLE
            btnDownloadModel.isEnabled = false
            btnDownloadModel.text = "DOWNLOADING ${modelInfo.name.uppercase()}..."
            btnDeleteModel.visibility = View.GONE
        } else {
            tvOfflineModelStatus.text = "Model missing for Offline Edge: ${modelInfo.name}. Tap download below."
            tvOfflineModelStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            tvOfflineModelStatus.visibility = View.VISIBLE
            btnDownloadModel.text = "DOWNLOAD ${modelInfo.name.uppercase()}"
            btnDownloadModel.visibility = View.VISIBLE
            btnDownloadModel.isEnabled = true
            btnDeleteModel.visibility = View.GONE
        }

        // Dynamically render a progress bar for each active download
        layoutActiveDownloads.removeAllViews()
        val allDownloadingModels = mutableListOf<ModelManager.ModelInfo>()
        for (m in ModelManager.AVAILABLE_MODELS) {
            if (ModelManager.isModelDownloading(m.fileName)) {
                allDownloadingModels.add(m)
            }
        }
        if (isCustom && ModelManager.isModelDownloading(modelInfo.fileName) && allDownloadingModels.none { it.fileName == modelInfo.fileName }) {
            allDownloadingModels.add(modelInfo)
        }

        for (model in allDownloadingModels) {
            val prog = ModelManager.getDownloadProgress(model.fileName) ?: 0

            val headerLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (10 * resources.displayMetrics.density).toInt()
                    bottomMargin = (4 * resources.displayMetrics.density).toInt()
                }
            }

            val tvName = TextView(this).apply {
                text = model.name
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
                textSize = 13f
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvPercent = TextView(this).apply {
                text = "$prog%"
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.accent_purple))
                textSize = 13f
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            headerLayout.addView(tvName)
            headerLayout.addView(tvPercent)

            val pb = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progressTintList = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.accent_purple))
                progressBackgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.key_bg))
                setProgress(prog)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            layoutActiveDownloads.addView(headerLayout)
            layoutActiveDownloads.addView(pb)
        }
    }

    private fun startModelDownload() {
        val isCustom = PreferencesManager.isCustomOfflineModel(this)
        val selectedFileName = PreferencesManager.getSelectedOfflineModel(this)
        val customInput = PreferencesManager.getCustomOfflineModel(this)
        val modelInfo = if (isCustom) {
            ModelManager.buildCustomModelInfo(customInput.ifEmpty { selectedFileName })
        } else {
            ModelManager.getModelInfoByFileName(selectedFileName)
        }

        if (ModelManager.isModelDownloading(modelInfo.fileName)) return

        if (isCustom && customInput.isNotBlank()) {
            PreferencesManager.addToHistory(this, PreferencesManager.KEY_HISTORY_OFFLINE_MODEL, customInput)
        }

        updateOfflineModelStatusUI()

        lifecycleScope.launch {
            val result = ModelManager.downloadModel(
                context = this@MainActivity,
                modelInfo = modelInfo
            ) { _ ->
                lifecycleScope.launch {
                    updateOfflineModelStatusUI()
                }
            }

            result.fold(
                onSuccess = { _ ->
                    Toast.makeText(this@MainActivity, "${modelInfo.name} downloaded successfully!", Toast.LENGTH_LONG).show()
                    updateOfflineModelStatusUI()
                },
                onFailure = { error ->
                    Toast.makeText(this@MainActivity, "Download failed: ${error.message}", Toast.LENGTH_LONG).show()
                    updateOfflineModelStatusUI()
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        checkKeyboardStatus()
        startPeriodicTcpPing()
        if (PreferencesManager.getEngineMode(this) == PreferencesManager.EngineMode.REMOTE_SERVER) {
            fetchRemoteServerModels()
        } else {
            updateOfflineModelStatusUI()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePermissionIntent(intent)
    }

    private fun handlePermissionIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("request_permission", false) == true) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        stopPeriodicTcpPing()
    }

    private fun startPeriodicTcpPing() {
        stopPeriodicTcpPing()

        // Only ping if in Remote Server mode
        if (PreferencesManager.getEngineMode(this) == PreferencesManager.EngineMode.EDGE_ON_DEVICE) {
            vStatusDot.setBackgroundResource(R.drawable.bg_status_dot_checking)
            tvPingInfo.text = "Ping disabled (Edge Mode Active)"
            return
        }

        vStatusDot.setBackgroundResource(R.drawable.bg_status_dot_checking)
        tvPingInfo.text = "Checking connection..."

        pingJob = lifecycleScope.launch {
            while (isActive) {
                val inputUrl = etServerUrl.text.toString().trim()
                val targetUrl = inputUrl.ifEmpty { PreferencesManager.getServerUrl(this@MainActivity) }
                runTcpPing(targetUrl)
                delay(2.seconds)
            }
        }
    }

    private fun stopPeriodicTcpPing() {
        pingJob?.cancel()
        pingJob = null
    }

    private suspend fun runTcpPing(url: String) {
        val result = TcpPingHelper.ping(url)
        result.fold(
            onSuccess = { rttMs ->
                vStatusDot.setBackgroundResource(R.drawable.bg_status_dot_green)
                tvPingInfo.text = "Server Online (${rttMs}ms response)"
            },
            onFailure = { error ->
                vStatusDot.setBackgroundResource(R.drawable.bg_status_dot_red)
                tvPingInfo.text = "Server Offline: ${error.toHumanReadablePingError()}"
            }
        )
    }

    private fun checkKeyboardStatus() {
        val purpleColor = ContextCompat.getColor(this, R.color.accent_purple)
        val defaultBtnColor = ContextCompat.getColor(this, R.color.key_bg)

        val isPermissionGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (isPermissionGranted) {
            tvStep1Status.text = getString(R.string.step_1_granted)
            btnGrantPermission.isEnabled = false
            btnGrantPermission.alpha = 0.5f
            btnGrantPermission.backgroundTintList = ColorStateList.valueOf(purpleColor)
        } else {
            tvStep1Status.text = getString(R.string.step_1_title)
            btnGrantPermission.isEnabled = true
            btnGrantPermission.alpha = 1.0f
            btnGrantPermission.backgroundTintList = ColorStateList.valueOf(purpleColor)
        }

        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val isKeyboardEnabled = imm.enabledInputMethodList.any {
            it.packageName == packageName
        }

        if (isKeyboardEnabled) {
            tvStep2Status.text = "2. Keyboard Enabled in Settings ✓"
            btnEnableKeyboard.isEnabled = false
            btnEnableKeyboard.alpha = 0.5f
            btnEnableKeyboard.backgroundTintList = ColorStateList.valueOf(purpleColor)
        } else {
            tvStep2Status.text = getString(R.string.step_2_title)
            btnEnableKeyboard.isEnabled = true
            btnEnableKeyboard.alpha = 1.0f
            btnEnableKeyboard.backgroundTintList = ColorStateList.valueOf(defaultBtnColor)
        }

        val currentDefaultIme = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val isKeyboardSelected = currentDefaultIme != null && currentDefaultIme.contains(packageName)

        if (isKeyboardSelected) {
            tvStep3Status.text = "3. Selected as Active Keyboard ✓"
            btnSelectKeyboard.isEnabled = false
            btnSelectKeyboard.alpha = 0.5f
            btnSelectKeyboard.backgroundTintList = ColorStateList.valueOf(purpleColor)
        } else {
            tvStep3Status.text = getString(R.string.step_3_title)
            btnSelectKeyboard.isEnabled = true
            btnSelectKeyboard.alpha = 1.0f
            btnSelectKeyboard.backgroundTintList = ColorStateList.valueOf(defaultBtnColor)
        }
    }

    private fun showHistoryDialog(
        title: String,
        historyKey: String,
        currentValueGetter: () -> String,
        onItemSelected: (String) -> Unit,
        onItemModified: ((oldVal: String, newVal: String) -> Unit)? = null
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_history, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tv_dialog_title)
        val layoutHistoryList = dialogView.findViewById<LinearLayout>(R.id.layout_history_list)
        val scrollHistory = dialogView.findViewById<View>(R.id.scroll_history)
        val tvEmptyHistory = dialogView.findViewById<TextView>(R.id.tv_empty_history)
        val btnAddEntry = dialogView.findViewById<Button>(R.id.btn_add_entry)
        val btnClose = dialogView.findViewById<Button>(R.id.btn_close_dialog)

        tvTitle.text = title

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        fun populateList() {
            layoutHistoryList.removeAllViews()
            val history = PreferencesManager.getHistory(this, historyKey)
            val currentVal = currentValueGetter().trim()

            if (history.isEmpty()) {
                tvEmptyHistory.visibility = View.VISIBLE
                scrollHistory.visibility = View.GONE
            } else {
                tvEmptyHistory.visibility = View.GONE
                scrollHistory.visibility = View.VISIBLE

                for (item in history) {
                    val itemView = layoutInflater.inflate(R.layout.item_history_entry, layoutHistoryList, false)
                    val tvValue = itemView.findViewById<TextView>(R.id.tv_history_value)
                    val btnEdit = itemView.findViewById<ImageButton>(R.id.btn_edit_history_item)
                    val btnDelete = itemView.findViewById<ImageButton>(R.id.btn_delete_history_item)

                    val isCurrent = item.equals(currentVal, ignoreCase = true)
                    tvValue.text = if (isCurrent) "✓ $item" else item
                    if (isCurrent) {
                        tvValue.setTextColor(ContextCompat.getColor(this, R.color.accent_purple))
                    } else {
                        tvValue.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
                    }

                    itemView.setOnClickListener {
                        dialog.dismiss()
                        onItemSelected(item)
                    }

                    btnEdit.setOnClickListener {
                        showEditHistoryEntryDialog(item) { updatedValue ->
                            PreferencesManager.updateHistoryItem(this, historyKey, item, updatedValue)
                            onItemModified?.invoke(item, updatedValue)
                            populateList()
                            Toast.makeText(this, "Entry updated", Toast.LENGTH_SHORT).show()
                        }
                    }

                    btnDelete.setOnClickListener {
                        MaterialAlertDialogBuilder(this)
                            .setTitle("Delete from History?")
                            .setMessage("Remove this item from history?\n\n$item")
                            .setPositiveButton("Delete") { _, _ ->
                                PreferencesManager.removeFromHistory(this, historyKey, item)
                                populateList()
                                Toast.makeText(this, "Removed from history", Toast.LENGTH_SHORT).show()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }

                    layoutHistoryList.addView(itemView)
                }
            }
        }

        btnAddEntry.setOnClickListener {
            val defaultVal = currentValueGetter().trim()
            val history = PreferencesManager.getHistory(this, historyKey)
            val prefill = if (defaultVal.isNotBlank() && !history.contains(defaultVal)) defaultVal else ""
            showAddHistoryEntryDialog(prefill) { newEntry ->
                PreferencesManager.addToHistory(this, historyKey, newEntry)
                populateList()
                Toast.makeText(this, "Added to history", Toast.LENGTH_SHORT).show()
            }
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        populateList()
        dialog.show()
    }

    private fun showEditHistoryEntryDialog(
        initialValue: String,
        onSave: (String) -> Unit
    ) {
        val input = EditText(this).apply {
            setText(initialValue)
            setSelection(initialValue.length)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
            setBackgroundResource(R.drawable.bg_punct_key)
            val padH = (14 * resources.displayMetrics.density).toInt()
            val padV = (12 * resources.displayMetrics.density).toInt()
            setPadding(padH, padV, padH, padV)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }

        val container = FrameLayout(this).apply {
            val padH = (20 * resources.displayMetrics.density).toInt()
            val padTop = (12 * resources.displayMetrics.density).toInt()
            val padBottom = (4 * resources.displayMetrics.density).toInt()
            setPadding(padH, padTop, padH, padBottom)
            addView(input)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Edit History Entry")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val newValue = input.text.toString().trim()
                if (newValue.isNotBlank()) {
                    onSave(newValue)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddHistoryEntryDialog(
        defaultText: String,
        onAdd: (String) -> Unit
    ) {
        val input = EditText(this).apply {
            setText(defaultText)
            if (defaultText.isNotEmpty()) setSelection(defaultText.length)
            hint = "Enter URL or Model ID"
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
            setBackgroundResource(R.drawable.bg_punct_key)
            val padH = (14 * resources.displayMetrics.density).toInt()
            val padV = (12 * resources.displayMetrics.density).toInt()
            setPadding(padH, padV, padH, padV)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }

        val container = FrameLayout(this).apply {
            val padH = (20 * resources.displayMetrics.density).toInt()
            val padTop = (12 * resources.displayMetrics.density).toInt()
            val padBottom = (4 * resources.displayMetrics.density).toInt()
            setPadding(padH, padTop, padH, padBottom)
            addView(input)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Add to History")
            .setView(container)
            .setPositiveButton("Add") { _, _ ->
                val newValue = input.text.toString().trim()
                if (newValue.isNotBlank()) {
                    onAdd(newValue)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
