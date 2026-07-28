package com.t1erno.whisperkeyboard.ui

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputConnection

class ProgressiveBackspace(private val inputConnectionProvider: () -> InputConnection?) {

    private val deleteHandler = Handler(Looper.getMainLooper())
    private var holdStartTime = 0L

    private val deleteRunnable = object : Runnable {
        override fun run() {
            executeBackspace()

            val elapsedMs = System.currentTimeMillis() - holdStartTime
            val nextDelayMs = when {
                elapsedMs < PHASE1_DURATION_MS -> PHASE1_INTERVAL_MS
                elapsedMs < PHASE2_DURATION_MS -> PHASE2_INTERVAL_MS
                else -> MAX_SPEED_INTERVAL_MS
            }

            deleteHandler.postDelayed(this, nextDelayMs)
        }
    }

    fun executeBackspace() {
        val ic = inputConnectionProvider() ?: return
        val selectedText = ic.getSelectedText(0)
        if (!selectedText.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else {
            val eventTime = System.currentTimeMillis()
            ic.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL, 0))
            ic.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL, 0))
        }
    }

    fun bind(backspaceButton: View) {
        backspaceButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    VibrationHelper.vibrateKey(backspaceButton.context, 18L)
                    holdStartTime = System.currentTimeMillis()
                    executeBackspace()
                    deleteHandler.removeCallbacks(deleteRunnable)
                    deleteHandler.postDelayed(deleteRunnable, PHASE1_INTERVAL_MS)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stop()
                    true
                }
                else -> false
            }
        }
    }

    fun start() {
        holdStartTime = System.currentTimeMillis()
        executeBackspace()
        deleteHandler.removeCallbacks(deleteRunnable)
        deleteHandler.postDelayed(deleteRunnable, PHASE1_INTERVAL_MS)
    }

    fun stop() {
        deleteHandler.removeCallbacks(deleteRunnable)
    }

    companion object {
        private const val PHASE1_DURATION_MS = 1000L
        private const val PHASE2_DURATION_MS = 3000L
        private const val PHASE1_INTERVAL_MS = 500L
        private const val PHASE2_INTERVAL_MS = 150L
        private const val MAX_SPEED_INTERVAL_MS = 20L
    }
}
