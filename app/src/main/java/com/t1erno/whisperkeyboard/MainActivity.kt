package com.t1erno.whisperkeyboard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.switchmaterial.SwitchMaterial
import com.t1erno.whisperkeyboard.network.TcpPingHelper
import com.t1erno.whisperkeyboard.network.TcpPingHelper.toHumanReadablePingError
import com.t1erno.whisperkeyboard.ui.VibrationHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var etServerUrl: EditText
    private lateinit var vStatusDot: View
    private lateinit var tvPingInfo: TextView
    private lateinit var btnSaveUrl: Button
    private lateinit var switchHaptic: SwitchMaterial

    private lateinit var tvStep1Status: TextView
    private lateinit var btnGrantPermission: Button

    private lateinit var tvStep2Status: TextView
    private lateinit var btnEnableKeyboard: Button

    private lateinit var tvStep3Status: TextView
    private lateinit var btnSelectKeyboard: Button

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
        vStatusDot = findViewById(R.id.v_status_dot)
        tvPingInfo = findViewById(R.id.tv_ping_info)
        btnSaveUrl = findViewById(R.id.btn_save_url)
        switchHaptic = findViewById(R.id.switch_haptic)

        tvStep1Status = findViewById(R.id.tv_step1_status)
        btnGrantPermission = findViewById(R.id.btn_grant_permission)

        tvStep2Status = findViewById(R.id.tv_step2_status)
        btnEnableKeyboard = findViewById(R.id.btn_enable_keyboard)

        tvStep3Status = findViewById(R.id.tv_step3_status)
        btnSelectKeyboard = findViewById(R.id.btn_select_keyboard)

        val currentUrl = PreferencesManager.getServerUrl(this)
        etServerUrl.setText(currentUrl)

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
                PreferencesManager.saveServerUrl(this, urlInput)
                val updatedUrl = PreferencesManager.getServerUrl(this)
                etServerUrl.setText(updatedUrl)
                Toast.makeText(this, "Server URL saved!", Toast.LENGTH_SHORT).show()
                startPeriodicTcpPing()
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

    override fun onResume() {
        super.onResume()
        checkKeyboardStatus()
        startPeriodicTcpPing()
    }

    override fun onPause() {
        super.onPause()
        stopPeriodicTcpPing()
    }

    private fun startPeriodicTcpPing() {
        stopPeriodicTcpPing()
        pingJob = lifecycleScope.launch {
            while (isActive) {
                val inputUrl = etServerUrl.text.toString().trim()
                val targetUrl = if (inputUrl.isNotEmpty()) inputUrl else PreferencesManager.getServerUrl(this@MainActivity)
                runTcpPing(targetUrl)
                delay(2000L) // Ping every 2 seconds while in config view
            }
        }
    }

    private fun stopPeriodicTcpPing() {
        pingJob?.cancel()
        pingJob = null
    }

    private suspend fun runTcpPing(url: String) {
        val result = TcpPingHelper.ping(url, timeoutMs = 2500)
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

        // Step 1 Check: Microphone Permission
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

        // Step 2 Check: Keyboard Enabled in System Settings
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

        // Step 3 Check: Selected as Active Default IME
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
