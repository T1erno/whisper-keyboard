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
                v.parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val totalDeltaX = event.rawX - initialX
                val totalDeltaY = event.rawY - initialY

                if (!isSwiping && (abs(totalDeltaX) > SWIPE_ACTIVATION_THRESHOLD_PX || abs(totalDeltaY) > SWIPE_ACTIVATION_THRESHOLD_PX)) {
                    isSwiping = true
                }

                if (isSwiping) {
                    v.parent?.requestDisallowInterceptTouchEvent(true)

                    val stepDeltaX = event.rawX - lastStepX
                    val stepDeltaY = event.rawY - lastStepY
                    val ic = inputConnectionProvider()

                    // Horizontal cursor movement (Left / Right)
                    if (abs(stepDeltaX) >= STEP_DISTANCE_HORIZONTAL_PX && ic != null) {
                        val moved = if (stepDeltaX > 0) moveCursorRight(ic) else moveCursorLeft(ic)
                        if (moved) {
                            VibrationHelper.vibrateKey(v.context, 12L)
                        }
                        lastStepX = event.rawX
                    }

                    // Vertical cursor movement (Up / Down across multi-line text)
                    if (abs(stepDeltaY) >= STEP_DISTANCE_VERTICAL_PX && ic != null) {
                        val moved = if (stepDeltaY > 0) moveCursorDown(ic) else moveCursorUp(ic)
                        if (moved) {
                            VibrationHelper.vibrateKey(v.context, 12L)
                        }
                        lastStepY = event.rawY
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.parent?.requestDisallowInterceptTouchEvent(false)
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

    private fun moveCursorLeft(ic: InputConnection): Boolean {
        val before = ic.getTextBeforeCursor(1, 0) ?: ""
        if (before.isEmpty()) return false
        val keycode = KeyEvent.KEYCODE_DPAD_LEFT
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keycode))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keycode))
        return true
    }

    private fun moveCursorRight(ic: InputConnection): Boolean {
        val after = ic.getTextAfterCursor(1, 0) ?: ""
        if (after.isEmpty()) return false
        val keycode = KeyEvent.KEYCODE_DPAD_RIGHT
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keycode))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keycode))
        return true
    }

    private fun moveCursorUp(ic: InputConnection): Boolean {
        val before = ic.getTextBeforeCursor(2000, 0) ?: ""
        if (before.isEmpty()) return false

        val lastLineBreak = before.lastIndexOf('\n')
        if (lastLineBreak < 0) {
            // Already on line 0! Stop here, don't bubble DPAD_UP to parent ScrollView
            return false
        }

        val currentColumn = before.length - 1 - lastLineBreak
        val textBeforePrevLine = before.substring(0, lastLineBreak)
        val prevLineBreak = textBeforePrevLine.lastIndexOf('\n')
        val prevLineStart = if (prevLineBreak < 0) 0 else prevLineBreak + 1
        val prevLineLength = lastLineBreak - prevLineStart

        val targetColumn = currentColumn.coerceAtMost(prevLineLength)
        val targetPos = prevLineStart + targetColumn

        return ic.setSelection(targetPos, targetPos)
    }

    private fun moveCursorDown(ic: InputConnection): Boolean {
        val before = ic.getTextBeforeCursor(2000, 0) ?: ""
        val after = ic.getTextAfterCursor(2000, 0) ?: ""
        if (after.isEmpty()) return false

        val nextLineBreak = after.indexOf('\n')
        if (nextLineBreak < 0) {
            // Already on last line! Stop here, don't bubble DPAD_DOWN to parent ScrollView
            return false
        }

        val lastLineBreakBefore = before.lastIndexOf('\n')
        val currentColumn = if (lastLineBreakBefore < 0) before.length else before.length - 1 - lastLineBreakBefore

        val currentPos = before.length
        val nextLineStart = currentPos + nextLineBreak + 1
        val afterNextLine = after.substring(nextLineBreak + 1)
        val nextLineBreak2 = afterNextLine.indexOf('\n')
        val nextLineLength = if (nextLineBreak2 < 0) afterNextLine.length else nextLineBreak2

        val targetColumn = currentColumn.coerceAtMost(nextLineLength)
        val targetPos = nextLineStart + targetColumn

        return ic.setSelection(targetPos, targetPos)
    }

    companion object {
        private const val SWIPE_ACTIVATION_THRESHOLD_PX = 16f
        private const val STEP_DISTANCE_HORIZONTAL_PX = 18f
        private const val STEP_DISTANCE_VERTICAL_PX = 22f
    }
}
