package com.t1erno.whisperkeyboard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.t1erno.whisperkeyboard.nativeengine.ModelManager
import com.t1erno.whisperkeyboard.nativeengine.OnDeviceTranscriber
import com.t1erno.whisperkeyboard.network.TcpPingHelper
import com.t1erno.whisperkeyboard.network.TcpPingHelper.toHumanReadablePingError
import com.t1erno.whisperkeyboard.ui.VibrationHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var etServerUrl: EditText
    private lateinit var cardRemoteSettings: MaterialCardView
    private lateinit var vStatusDot: View
    private lateinit var tvPingInfo: TextView
    private lateinit var btnSaveUrl: Button
    private lateinit var switchHaptic: SwitchMaterial

    private lateinit var switchEngineMode: SwitchMaterial
    private lateinit var tvEngineModeDesc: TextView

    private lateinit var rgModels: RadioGroup
    private lateinit var rbModelLargeV3: RadioButton
    private lateinit var rbModelLargeTurbo: RadioButton
    private lateinit var rbModelMedium: RadioButton
    private lateinit var rbModelSmall: RadioButton
    private lateinit var rbModelBase: RadioButton
    private lateinit var rbModelTiny: RadioButton

    private lateinit var tvModelStatus: TextView
    private lateinit var pbModelDownload: ProgressBar
    private lateinit var btnDownloadModel: MaterialButton

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
        cardRemoteSettings = findViewById(R.id.card_remote_settings)
        vStatusDot = findViewById(R.id.v_status_dot)
        tvPingInfo = findViewById(R.id.tv_ping_info)
        btnSaveUrl = findViewById(R.id.btn_save_url)
        switchHaptic = findViewById(R.id.switch_haptic)

        switchEngineMode = findViewById(R.id.switch_engine_mode)
        tvEngineModeDesc = findViewById(R.id.tv_engine_mode_desc)

        rgModels = findViewById(R.id.rg_models)
        rbModelLargeV3 = findViewById(R.id.rb_model_large_v3)
        rbModelLargeTurbo = findViewById(R.id.rb_model_large_turbo)
        rbModelMedium = findViewById(R.id.rb_model_medium)
        rbModelSmall = findViewById(R.id.rb_model_small)
        rbModelBase = findViewById(R.id.rb_model_base)
        rbModelTiny = findViewById(R.id.rb_model_tiny)

        tvModelStatus = findViewById(R.id.tv_model_status)
        pbModelDownload = findViewById(R.id.pb_model_download)
        btnDownloadModel = findViewById(R.id.btn_download_model)

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

        setupEngineModeUI()
        setupModelSelectionUI()

        switchHaptic.isChecked = PreferencesManager.isHapticEnabled(this)
        switchHaptic.setOnCheckedChangeListener { _, isChecked ->
            PreferencesManager.setHapticEnabled(this, isChecked)
            if (isChecked) {
                VibrationHelper.vibrateKey(this, 30L)
            }
        }

        btnSaveUrl.setOnClickListener {
            val urlInput = etServerUrl.text.toString().trim()
            if (urlInput.isNotEmpty()) {
                if (urlInput.startsWith("http://", ignoreCase = true)) {
                    showHttpWarningDialog(urlInput)
                } else {
                    saveAndApplyServerUrl(urlInput)
                }
            } else {
                Toast.makeText(this, "Please enter a valid URL", Toast.LENGTH_SHORT).show()
            }
        }

        btnGrantPermission.setOnClickListener {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        btnEnableKeyboard.setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        btnSelectKeyboard.setOnClickListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }
    }

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
        Toast.makeText(this, "Server URL saved!", Toast.LENGTH_SHORT).show()
        startPeriodicTcpPing()
    }

    private fun setupEngineModeUI() {
        val currentMode = PreferencesManager.getEngineMode(this)
        val isEdge = currentMode == PreferencesManager.EngineMode.EDGE_ON_DEVICE
        switchEngineMode.isChecked = isEdge

        updateEngineModeViews(isEdge)

        switchEngineMode.setOnCheckedChangeListener { _, isChecked ->
            val newMode = if (isChecked) PreferencesManager.EngineMode.EDGE_ON_DEVICE else PreferencesManager.EngineMode.REMOTE_SERVER
            PreferencesManager.setEngineMode(this, newMode)
            VibrationHelper.vibrateKey(this, 30L)
            updateEngineModeViews(isChecked)
        }
    }

    private fun updateEngineModeViews(isEdge: Boolean) {
        if (isEdge) {
            tvEngineModeDesc.text = "Edge On-Device (Offline whisper.cpp NDK)"
            tvEngineModeDesc.setTextColor(ContextCompat.getColor(this, R.color.accent_purple))

            // Disable & Grey out Remote Server settings
            etServerUrl.isEnabled = false
            btnSaveUrl.isEnabled = false
            cardRemoteSettings.alpha = 0.5f
            startPeriodicTcpPing()
        } else {
            tvEngineModeDesc.text = "Remote Server"
            tvEngineModeDesc.setTextColor(ContextCompat.getColor(this, R.color.accent_purple))

            // Enable & Restore Remote Server settings
            etServerUrl.isEnabled = true
            btnSaveUrl.isEnabled = true
            cardRemoteSettings.alpha = 1.0f
            startPeriodicTcpPing()
        }
        updateModelStatusUI()
    }

    private fun setupModelSelectionUI() {
        val currentModel = PreferencesManager.getSelectedModelFileName(this)
        when (currentModel) {
            ModelManager.MODEL_LARGE_V3.fileName -> rbModelLargeV3.isChecked = true
            ModelManager.MODEL_MEDIUM.fileName -> rbModelMedium.isChecked = true
            ModelManager.MODEL_SMALL.fileName -> rbModelSmall.isChecked = true
            ModelManager.MODEL_BASE.fileName -> rbModelBase.isChecked = true
            ModelManager.MODEL_TINY.fileName -> rbModelTiny.isChecked = true
            else -> rbModelLargeTurbo.isChecked = true
        }

        rgModels.setOnCheckedChangeListener { _, checkedId ->
            val selectedModel = when (checkedId) {
                R.id.rb_model_large_v3 -> ModelManager.MODEL_LARGE_V3
                R.id.rb_model_medium -> ModelManager.MODEL_MEDIUM
                R.id.rb_model_small -> ModelManager.MODEL_SMALL
                R.id.rb_model_base -> ModelManager.MODEL_BASE
                R.id.rb_model_tiny -> ModelManager.MODEL_TINY
                else -> ModelManager.MODEL_LARGE_V3_TURBO
            }

            PreferencesManager.setSelectedModelFileName(this, selectedModel.fileName)
            OnDeviceTranscriber.releaseContext()
            updateModelStatusUI()
        }

        btnDownloadModel.setOnClickListener {
            startParallelModelDownload()
        }
    }

    private fun updateModelStatusUI() {
        val selectedFileName = PreferencesManager.getSelectedModelFileName(this)
        val modelInfo = ModelManager.getModelInfoByFileName(selectedFileName)
        val isDownloaded = ModelManager.isModelDownloaded(this, selectedFileName)
        val isDownloading = ModelManager.isModelDownloading(selectedFileName)
        val progress = ModelManager.getDownloadProgress(selectedFileName)
        val currentEngineMode = PreferencesManager.getEngineMode(this)

        if (currentEngineMode == PreferencesManager.EngineMode.REMOTE_SERVER) {
            tvModelStatus.text = "✓ Active for Remote Server: ${modelInfo.name} (key: '${modelInfo.serverKey}')"
            tvModelStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_purple))
            btnDownloadModel.visibility = View.GONE
            pbModelDownload.visibility = View.GONE
        } else {
            // Edge On-Device Mode
            if (isDownloaded) {
                tvModelStatus.text = "✓ Offline model ready: ${modelInfo.name}"
                tvModelStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_purple))
                btnDownloadModel.visibility = View.GONE
                pbModelDownload.visibility = View.GONE
            } else if (isDownloading) {
                tvModelStatus.text = "Downloading ${modelInfo.name} (Parallel 4-Stream)... ${progress ?: 0}%"
                tvModelStatus.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
                btnDownloadModel.visibility = View.VISIBLE
                btnDownloadModel.isEnabled = false
                btnDownloadModel.text = "Downloading..."
                pbModelDownload.visibility = View.VISIBLE
                pbModelDownload.progress = progress ?: 0
            } else {
                tvModelStatus.text = "Model missing for Offline Edge: ${modelInfo.name}. Tap download below."
                tvModelStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                btnDownloadModel.text = "Download ${modelInfo.name} (Parallel 4-Stream)"
                btnDownloadModel.visibility = View.VISIBLE
                btnDownloadModel.isEnabled = true
                pbModelDownload.visibility = View.GONE
            }
        }
    }

    private fun startParallelModelDownload() {
        val selectedFileName = PreferencesManager.getSelectedModelFileName(this)
        val modelInfo = ModelManager.getModelInfoByFileName(selectedFileName)

        if (ModelManager.isModelDownloading(selectedFileName)) return

        updateModelStatusUI()

        lifecycleScope.launch {
            val result = ModelManager.downloadModelParallel(
                context = this@MainActivity,
                modelInfo = modelInfo,
                numThreads = 4
            ) { _ ->
                lifecycleScope.launch {
                    val currentSelected = PreferencesManager.getSelectedModelFileName(this@MainActivity)
                    if (currentSelected == modelInfo.fileName) {
                        updateModelStatusUI()
                    }
                }
            }

            result.fold(
                onSuccess = { _ ->
                    Toast.makeText(this@MainActivity, "${modelInfo.name} downloaded successfully!", Toast.LENGTH_LONG).show()
                    updateModelStatusUI()
                },
                onFailure = { error ->
                    Toast.makeText(this@MainActivity, "Download failed: ${error.message}", Toast.LENGTH_LONG).show()
                    updateModelStatusUI()
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        checkKeyboardStatus()
        startPeriodicTcpPing()
        updateModelStatusUI()
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
                val targetUrl = if (inputUrl.isNotEmpty()) inputUrl else PreferencesManager.getServerUrl(this@MainActivity)
                runTcpPing(targetUrl)
                delay(2000L)
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

        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
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
}
