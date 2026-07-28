package com.t1erno.whisperkeyboard.ui

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputConnection
import kotlin.math.abs

class SpacebarTouchListener(
    private val inputConnectionProvider: () -> InputConnection?,
    private val onSpaceClick: () -> Unit
) : View.OnTouchListener {

    private var initialX = 0f
    private var initialY = 0f
    private var lastStepX = 0f
    private var lastStepY = 0f
    private var isSwiping = false

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialX = event.rawX
                initialY = event.rawY
                lastStepX = event.rawX
                lastStepY = event.rawY
                isSwiping = false
                v.isPressed = true
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val totalDeltaX = event.rawX - initialX
                val totalDeltaY = event.rawY - initialY

                if (!isSwiping && (abs(totalDeltaX) > SWIPE_ACTIVATION_THRESHOLD_PX || abs(totalDeltaY) > SWIPE_ACTIVATION_THRESHOLD_PX)) {
                    isSwiping = true
                }

                if (isSwiping) {
                    val stepDeltaX = event.rawX - lastStepX
                    val stepDeltaY = event.rawY - lastStepY
                    val ic = inputConnectionProvider()

                    // Horizontal cursor movement (Left / Right)
                    if (abs(stepDeltaX) >= STEP_DISTANCE_HORIZONTAL_PX) {
                        if (ic != null) {
                            val keycode = if (stepDeltaX > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
                            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keycode))
                            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keycode))
                            VibrationHelper.vibrateKey(v.context, 12L)
                        }
                        lastStepX = event.rawX
                    }

                    // Vertical cursor movement (Up / Down across lines)
                    if (abs(stepDeltaY) >= STEP_DISTANCE_VERTICAL_PX) {
                        if (ic != null) {
                            val keycode = if (stepDeltaY > 0) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP
                            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keycode))
                            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keycode))
                            VibrationHelper.vibrateKey(v.context, 12L)
                        }
                        lastStepY = event.rawY
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.isPressed = false
                if (!isSwiping && event.actionMasked == MotionEvent.ACTION_UP) {
                    onSpaceClick()
                }
                isSwiping = false
                return true
            }
        }
        return false
    }

    companion object {
        private const val SWIPE_ACTIVATION_THRESHOLD_PX = 16f
        private const val STEP_DISTANCE_HORIZONTAL_PX = 18f
        private const val STEP_DISTANCE_VERTICAL_PX = 22f
    }
}
