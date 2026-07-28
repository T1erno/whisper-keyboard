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

    // Preferred column index to maintain straight vertical up/down movement
    private var preferredColumn: Int = -1

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialX = event.rawX
                initialY = event.rawY
                lastStepX = event.rawX
                lastStepY = event.rawY
                isSwiping = false
                preferredColumn = -1
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
                        preferredColumn = -1
                        val moved = if (stepDeltaX > 0) moveCursorRight(ic) else moveCursorLeft(ic)
                        if (moved) {
                            VibrationHelper.vibrateKey(v.context, 12L)
                        }
                        lastStepX = event.rawX
                    }

                    // Vertical cursor movement (Up / Down)
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
                preferredColumn = -1
                return true
            }
        }
        return false
    }

    private fun moveCursorLeft(ic: InputConnection): Boolean {
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
        if (extracted != null && extracted.text != null) {
            val cursor = extracted.selectionStart
            if (cursor <= 0) return false
            val targetPos = (cursor - 1).coerceAtLeast(0)
            return ic.setSelection(targetPos, targetPos)
        }

        val before = ic.getTextBeforeCursor(2000, 0) ?: ""
        if (before.isEmpty()) return false
        val newPos = (before.length - 1).coerceAtLeast(0)
        return ic.setSelection(newPos, newPos)
    }

    private fun moveCursorRight(ic: InputConnection): Boolean {
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
        if (extracted != null && extracted.text != null) {
            val cursor = extracted.selectionStart
            val textLength = extracted.text.length
            if (cursor >= textLength) return false
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
        val fullText = extracted?.text?.toString()
        val beforeFallback = ic.getTextBeforeCursor(2000, 0) ?: ""
        val cursor = extracted?.selectionStart ?: beforeFallback.length

        if (cursor <= 0) return false

        val textBefore = if (fullText != null && cursor <= fullText.length) {
            fullText.substring(0, cursor)
        } else {
            beforeFallback
        }

        if (textBefore.isEmpty()) return false

        val lastLineBreak = textBefore.lastIndexOf('\n')
        if (lastLineBreak >= 0) {
            // Explicit newline present in preceding text
            if (preferredColumn < 0) {
                preferredColumn = textBefore.length - 1 - lastLineBreak
            }
            val textBeforePrevLine = textBefore.substring(0, lastLineBreak)
            val prevLineBreak = textBeforePrevLine.lastIndexOf('\n')
            val prevLineStart = if (prevLineBreak < 0) 0 else prevLineBreak + 1
            val prevLineLength = lastLineBreak - prevLineStart

            val targetColumn = preferredColumn.coerceAtMost(prevLineLength)
            val targetPos = prevLineStart + targetColumn
            return ic.setSelection(targetPos, targetPos)
        } else {
            // Word-wrapped text line (no explicit \n)
            if (textBefore.length <= AVG_LINE_CHARS) {
                // Top line of word-wrapped text! Stop safely at index 0 without app scrolling.
                return ic.setSelection(0, 0)
            }

            val searchStart = (textBefore.length - AVG_LINE_CHARS).coerceAtLeast(0)
            val spaceIndex = textBefore.lastIndexOf(' ', searchStart)
            val targetPos = if (spaceIndex >= 0) spaceIndex else searchStart

            return ic.setSelection(targetPos, targetPos)
        }
    }

    private fun moveCursorDown(ic: InputConnection): Boolean {
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
        val fullText = extracted?.text?.toString()
        val beforeFallback = ic.getTextBeforeCursor(2000, 0) ?: ""
        val afterFallback = ic.getTextAfterCursor(2000, 0) ?: ""
        val cursor = extracted?.selectionStart ?: beforeFallback.length
        val totalLength = fullText?.length ?: (beforeFallback.length + afterFallback.length)

        if (cursor >= totalLength) return false

        val textBefore = if (fullText != null && cursor <= fullText.length) {
            fullText.substring(0, cursor)
        } else {
            beforeFallback
        }

        val textAfter = if (fullText != null && cursor <= fullText.length) {
            fullText.substring(cursor)
        } else {
            afterFallback
        }

        val nextLineBreak = textAfter.indexOf('\n')
        if (nextLineBreak >= 0) {
            // Explicit newline present in following text
            val lastLineBreakBefore = textBefore.lastIndexOf('\n')
            if (preferredColumn < 0) {
                preferredColumn = if (lastLineBreakBefore < 0) textBefore.length else textBefore.length - 1 - lastLineBreakBefore
            }

            val nextLineStart = cursor + nextLineBreak + 1
            val textAfterNextLine = textAfter.substring(nextLineBreak + 1)
            val nextLineBreak2 = textAfterNextLine.indexOf('\n')
            val nextLineLength = if (nextLineBreak2 < 0) textAfterNextLine.length else nextLineBreak2

            val targetColumn = preferredColumn.coerceAtMost(nextLineLength)
            val targetPos = nextLineStart + targetColumn
            return ic.setSelection(targetPos, targetPos)
        } else {
            // Word-wrapped text line (no explicit \n)
            if (textAfter.length <= AVG_LINE_CHARS) {
                // Bottom line of word-wrapped text! Stop safely at totalLength without app scrolling.
                return ic.setSelection(totalLength, totalLength)
            }

            val searchStart = AVG_LINE_CHARS.coerceAtMost(textAfter.length - 1)
            val spaceIndex = textAfter.indexOf(' ', searchStart)
            val offset = if (spaceIndex >= 0) spaceIndex else searchStart
            val targetPos = (cursor + offset).coerceAtMost(totalLength)

            return ic.setSelection(targetPos, targetPos)
        }
    }

    companion object {
        private const val SWIPE_ACTIVATION_THRESHOLD_PX = 16f
        private const val STEP_DISTANCE_HORIZONTAL_PX = 18f
        private const val STEP_DISTANCE_VERTICAL_PX = 22f
        private const val AVG_LINE_CHARS = 35
    }
}
