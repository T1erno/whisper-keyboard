package com.t1erno.whisperkeyboard.ui

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputConnection

class ProgressiveBackspace(private val inputConnectionProvider: () -> InputConnection?) {

    private val deleteHandler = Handler(Looper.getMainLooper())
    private var holdStartTime = 0L

    private val deleteRunnable = object : Runnable {
        override fun run() {
            inputConnectionProvider()?.deleteSurroundingText(1, 0)

            val elapsedMs = System.currentTimeMillis() - holdStartTime
            val nextDelayMs = when {
                elapsedMs < PHASE1_DURATION_MS -> PHASE1_INTERVAL_MS
                elapsedMs < PHASE2_DURATION_MS -> PHASE2_INTERVAL_MS
                else -> MAX_SPEED_INTERVAL_MS
            }

            deleteHandler.postDelayed(this, nextDelayMs)
        }
    }

    fun bind(backspaceButton: View) {
        backspaceButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    VibrationHelper.vibrateKey(backspaceButton.context, 18L)
                    holdStartTime = System.currentTimeMillis()
                    inputConnectionProvider()?.deleteSurroundingText(1, 0)
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
