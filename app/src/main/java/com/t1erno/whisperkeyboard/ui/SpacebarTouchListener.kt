package com.t1erno.whisperkeyboard.ui

import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.ExtractedTextRequest
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

                    // Vertical cursor movement (Up / Down) - Pure selection based, ZERO DPAD key dispatching
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
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
        if (extracted != null && extracted.text != null) {
            val cursor = extracted.selectionStart
            if (cursor <= 0) return false // At start of text field! Stop safely without scrolling app.
            val targetPos = (cursor - 1).coerceAtLeast(0)
            return ic.setSelection(targetPos, targetPos)
        }

        val before = ic.getTextBeforeCursor(1, 0) ?: ""
        if (before.isEmpty()) return false
        val targetBefore = ic.getTextBeforeCursor(2000, 0) ?: ""
        if (targetBefore.isEmpty()) return false
        val newPos = (targetBefore.length - 1).coerceAtLeast(0)
        return ic.setSelection(newPos, newPos)
    }

    private fun moveCursorRight(ic: InputConnection): Boolean {
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
        if (extracted != null && extracted.text != null) {
            val cursor = extracted.selectionStart
            val textLength = extracted.text.length
            if (cursor >= textLength) return false // At end of text field! Stop safely without scrolling app.
            val targetPos = (cursor + 1).coerceAtMost(textLength)
            return ic.setSelection(targetPos, targetPos)
        }

        val after = ic.getTextAfterCursor(1, 0) ?: ""
        if (after.isEmpty()) return false
        val before = ic.getTextBeforeCursor(2000, 0) ?: ""
        val newPos = before.length + 1
        return ic.setSelection(newPos, newPos)
    }

    private fun moveCursorUp(ic: InputConnection): Boolean {
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
        if (extracted != null && extracted.text != null) {
            val fullText = extracted.text.toString()
            val cursor = extracted.selectionStart.coerceIn(0, fullText.length)

            if (cursor <= 0) return false

            val textBefore = fullText.substring(0, cursor)
            val lastLineBreak = textBefore.lastIndexOf('\n')
            if (lastLineBreak >= 0) {
                val currentColumn = textBefore.length - 1 - lastLineBreak
                val textBeforePrevLine = textBefore.substring(0, lastLineBreak)
                val prevLineBreak = textBeforePrevLine.lastIndexOf('\n')
                val prevLineStart = if (prevLineBreak < 0) 0 else prevLineBreak + 1
                val prevLineLength = lastLineBreak - prevLineStart

                val targetColumn = currentColumn.coerceAtMost(prevLineLength)
                val targetPos = prevLineStart + targetColumn
                return ic.setSelection(targetPos, targetPos)
            } else {
                // If no explicit newline, jump up by average line length (approx 40 chars)
                if (cursor <= 0) return false
                val targetPos = (cursor - ESTIMATED_LINE_CHARS).coerceAtLeast(0)
                return ic.setSelection(targetPos, targetPos)
            }
        }

        val before = ic.getTextBeforeCursor(2000, 0) ?: ""
        if (before.isEmpty()) return false

        val lastLineBreak = before.lastIndexOf('\n')
        if (lastLineBreak >= 0) {
            val currentColumn = before.length - 1 - lastLineBreak
            val textBeforePrevLine = before.substring(0, lastLineBreak)
            val prevLineBreak = textBeforePrevLine.lastIndexOf('\n')
            val prevLineStart = if (prevLineBreak < 0) 0 else prevLineBreak + 1
            val prevLineLength = lastLineBreak - prevLineStart

            val targetColumn = currentColumn.coerceAtMost(prevLineLength)
            val targetPos = prevLineStart + targetColumn
            return ic.setSelection(targetPos, targetPos)
        } else {
            val targetPos = (before.length - ESTIMATED_LINE_CHARS).coerceAtLeast(0)
            return ic.setSelection(targetPos, targetPos)
        }
    }

    private fun moveCursorDown(ic: InputConnection): Boolean {
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
        if (extracted != null && extracted.text != null) {
            val fullText = extracted.text.toString()
            val cursor = extracted.selectionStart.coerceIn(0, fullText.length)

            if (cursor >= fullText.length) return false

            val textAfter = fullText.substring(cursor)
            val nextLineBreak = textAfter.indexOf('\n')
            if (nextLineBreak >= 0) {
                val textBefore = fullText.substring(0, cursor)
                val lastLineBreakBefore = textBefore.lastIndexOf('\n')
                val currentColumn = if (lastLineBreakBefore < 0) textBefore.length else textBefore.length - 1 - lastLineBreakBefore

                val currentPos = cursor
                val nextLineStart = currentPos + nextLineBreak + 1
                val textAfterNextLine = textAfter.substring(nextLineBreak + 1)
                val nextLineBreak2 = textAfterNextLine.indexOf('\n')
                val nextLineLength = if (nextLineBreak2 < 0) textAfterNextLine.length else nextLineBreak2

                val targetColumn = currentColumn.coerceAtMost(nextLineLength)
                val targetPos = nextLineStart + targetColumn
                return ic.setSelection(targetPos, targetPos)
            } else {
                // If no explicit newline, jump down by average line length
                val targetPos = (cursor + ESTIMATED_LINE_CHARS).coerceAtMost(fullText.length)
                return ic.setSelection(targetPos, targetPos)
            }
        }

        val before = ic.getTextBeforeCursor(2000, 0) ?: ""
        val after = ic.getTextAfterCursor(2000, 0) ?: ""
        if (after.isEmpty()) return false

        val nextLineBreak = after.indexOf('\n')
        if (nextLineBreak >= 0) {
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
        } else {
            val totalLength = before.length + after.length
            val targetPos = (before.length + ESTIMATED_LINE_CHARS).coerceAtMost(totalLength)
            return ic.setSelection(targetPos, targetPos)
        }
    }

    companion object {
        private const val SWIPE_ACTIVATION_THRESHOLD_PX = 16f
        private const val STEP_DISTANCE_HORIZONTAL_PX = 18f
        private const val STEP_DISTANCE_VERTICAL_PX = 22f
        private const val ESTIMATED_LINE_CHARS = 35
    }
}
