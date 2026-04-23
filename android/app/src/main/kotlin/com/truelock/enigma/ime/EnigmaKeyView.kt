package com.truelock.enigma.ime

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import androidx.appcompat.content.res.AppCompatResources
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
        ellipsize = null
        maxLines = 1
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        setPadding(dp(4), dp(2), dp(4), dp(2))
        setTextColor(Color.parseColor("#F6F8FC"))
        typeface = Typeface.DEFAULT_BOLD
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        background = AppCompatResources.getDrawable(context, R.drawable.bg_key)
    }

    fun applyUtilityStyle() {
        ellipsize = TextUtils.TruncateAt.END
        setTextColor(Color.parseColor("#F2F8FF"))
        typeface = Typeface.DEFAULT_BOLD
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        background = AppCompatResources.getDrawable(context, R.drawable.bg_key_utility)
    }

    fun applyCharacterStyle() {
        ellipsize = null
        setTextColor(Color.parseColor("#F6F8FC"))
        typeface = Typeface.DEFAULT_BOLD
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        background = AppCompatResources.getDrawable(context, R.drawable.bg_key)
    }

    fun applySuggestionStyle() {
        setTextColor(Color.parseColor("#F2F8FF"))
        typeface = Typeface.DEFAULT_BOLD
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
        background = AppCompatResources.getDrawable(context, R.drawable.bg_key_utility)
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics,
        ).toInt()
}
