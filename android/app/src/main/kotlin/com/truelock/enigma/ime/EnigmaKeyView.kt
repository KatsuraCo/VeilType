package com.truelock.enigma.ime

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import androidx.appcompat.widget.AppCompatTextView
import com.truelock.enigma.R

class EnigmaKeyView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatTextView(context, attrs, defStyleAttr) {

    init {
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = false
        isAllCaps = false
        includeFontPadding = false
        ellipsize = TextUtils.TruncateAt.END
        maxLines = 1
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        setPadding(dp(4), dp(2), dp(4), dp(2))
        setTextColor(Color.parseColor("#102030"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        background = context.getDrawable(R.drawable.bg_key)
    }

    fun applyUtilityStyle() {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
        background = context.getDrawable(R.drawable.bg_key_utility)
    }

    fun applyCharacterStyle() {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        background = context.getDrawable(R.drawable.bg_key)
    }

    fun applySuggestionStyle() {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        background = context.getDrawable(R.drawable.bg_key_utility)
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics,
        ).toInt()
}
