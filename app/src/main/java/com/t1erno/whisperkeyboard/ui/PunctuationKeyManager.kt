package com.t1erno.whisperkeyboard.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.t1erno.whisperkeyboard.R

class PunctuationKeyManager(private val onCommitText: (String) -> Unit) {

    private val longPressHandler = Handler(Looper.getMainLooper())

    fun setupPunctuationKeys(rootView: View, themedContext: Context) {
        val punctuationKeys = listOf(
            KeySpec(R.id.key_comma, ", ", listOf(", ", "; ", "< ", "{ ")),
            KeySpec(R.id.key_period, ". ", listOf(". ", ": ", "> ", "} ")),
            KeySpec(R.id.key_question, "? ", listOf("? ", "¿", "~", "\\")),
            KeySpec(R.id.key_exclamation, "! ", listOf("! ", "¡", "|", "/")),
            KeySpec(R.id.key_quotes, "\"", listOf("\"", "'", "`", "«", "»"))
        )

        val density = themedContext.resources.displayMetrics.density
        val itemWidthPx = (ITEM_WIDTH_DP * density).toInt()
        val itemHeightPx = (ITEM_HEIGHT_DP * density).toInt()

        for (keySpec in punctuationKeys) {
            val keyView = rootView.findViewById<TextView>(keySpec.viewId) ?: continue

            var popupWindow: PopupWindow? = null
            var popupView: View? = null
            val optionTextViews = mutableListOf<TextView>()
            var selectedIndex = 1
            var isLongPress = false
            var longPressRunnable: Runnable? = null

            keyView.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        VibrationHelper.vibrateKey(themedContext, 22L)
                        isLongPress = false
                        selectedIndex = 1
                        keyView.setBackgroundResource(R.drawable.bg_punct_key_pressed)

                        longPressRunnable = Runnable {
                            isLongPress = true

                            val inflater = LayoutInflater.from(themedContext)
                            popupView = inflater.inflate(R.layout.layout_key_popup, null)
                            val optionsContainer = popupView?.findViewById<LinearLayout>(R.id.popup_options_container)
                            optionTextViews.clear()

                            keySpec.alternatives.forEachIndexed { index, symbol ->
                                val tv = TextView(themedContext).apply {
                                    text = symbol.trim()
                                    textSize = 20f
                                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                                    gravity = Gravity.CENTER
                                    setTextColor(Color.WHITE)
                                    setBackgroundResource(
                                        if (index == selectedIndex) R.drawable.bg_popup_item_selected
                                        else R.drawable.bg_popup_item_normal
                                    )
                                }
                                val lp = LinearLayout.LayoutParams(
                                    itemWidthPx,
                                    itemHeightPx
                                ).apply {
                                    setMargins(4, 0, 4, 0)
                                }
                                optionsContainer?.addView(tv, lp)
                                optionTextViews.add(tv)
                            }

                            popupWindow = PopupWindow(
                                popupView,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                false
                            ).apply {
                                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                                isOutsideTouchable = true
                            }

                            popupView?.measure(
                                View.MeasureSpec.UNSPECIFIED,
                                View.MeasureSpec.UNSPECIFIED
                            )
                            val popupWidth = popupView?.measuredWidth ?: 0
                            val popupHeight = popupView?.measuredHeight ?: 0

                            val xOffset = (keyView.width / 2) - (popupWidth / 2)
                            val yOffset = -keyView.height - popupHeight - POPUP_Y_OFFSET_DP

                            try {
                                popupWindow?.showAsDropDown(keyView, xOffset, yOffset)
                            } catch (_: Exception) {}

                            VibrationHelper.vibrateKey(themedContext, 30L)
                            keyView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        }

                        longPressHandler.postDelayed(longPressRunnable!!, LONG_PRESS_DELAY_MS)
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        if (isLongPress && optionTextViews.isNotEmpty() && popupView != null) {
                            val rawX = event.rawX
                            for (i in optionTextViews.indices) {
                                val item = optionTextViews[i]
                                val itemLoc = IntArray(2)
                                item.getLocationOnScreen(itemLoc)
                                val itemLeft = itemLoc[0]
                                val itemRight = itemLeft + item.width

                                if (rawX >= itemLeft && rawX <= itemRight) {
                                    if (selectedIndex != i) {
                                        selectedIndex = i
                                        updatePopupSelection(themedContext, optionTextViews, i)
                                        VibrationHelper.vibrateKey(themedContext, 15L)
                                    }
                                    break
                                }
                            }
                        }
                        true
                    }

                    MotionEvent.ACTION_UP -> {
                        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                        keyView.setBackgroundResource(R.drawable.bg_punct_key)

                        if (popupWindow?.isShowing == true) {
                            try {
                                popupWindow?.dismiss()
                            } catch (_: Exception) {}
                        }

                        if (isLongPress) {
                            val symbolToCommit = if (selectedIndex in keySpec.alternatives.indices) {
                                keySpec.alternatives[selectedIndex]
                            } else {
                                keySpec.alternatives.getOrNull(1) ?: keySpec.primarySymbol
                            }
                            onCommitText(symbolToCommit)
                        } else {
                            onCommitText(keySpec.primarySymbol)
                        }
                        true
                    }

                    MotionEvent.ACTION_CANCEL -> {
                        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                        keyView.setBackgroundResource(R.drawable.bg_punct_key)
                        if (popupWindow?.isShowing == true) {
                            try {
                                popupWindow?.dismiss()
                            } catch (_: Exception) {}
                        }
                        true
                    }

                    else -> false
                }
            }
        }
    }

    private fun updatePopupSelection(context: Context, optionViews: List<TextView>, selectedIndex: Int) {
        optionViews.forEachIndexed { idx, tv ->
            if (idx == selectedIndex) {
                tv.setBackgroundResource(R.drawable.bg_popup_item_selected)
                tv.setTextColor(Color.WHITE)
            } else {
                tv.setBackgroundResource(R.drawable.bg_popup_item_normal)
                tv.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            }
        }
    }

    private data class KeySpec(
        val viewId: Int,
        val primarySymbol: String,
        val alternatives: List<String>
    )

    companion object {
        private const val LONG_PRESS_DELAY_MS = 500L
        private const val ITEM_WIDTH_DP = 48
        private const val ITEM_HEIGHT_DP = 46
        private const val POPUP_Y_OFFSET_DP = 12
    }
}
