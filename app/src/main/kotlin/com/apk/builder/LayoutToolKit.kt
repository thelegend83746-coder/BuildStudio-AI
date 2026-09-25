package com.apk.builder

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.View
import android.view.animation.AlphaAnimation

object LayoutToolKit {

    @JvmStatic
    fun dpToPx(context: Context, dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }

    @JvmStatic
    fun pxToDp(context: Context, px: Float): Float {
        return px / context.resources.displayMetrics.density
    }

    @JvmStatic
    fun createRippleDrawable(normalColor: Int, rippleColor: Int, cornerRadiusDp: Float, context: Context): RippleDrawable {
        val content = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(normalColor)
            cornerRadius = dpToPx(context, cornerRadiusDp).toFloat()
        }

        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.WHITE)
            cornerRadius = dpToPx(context, cornerRadiusDp).toFloat()
        }

        return RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask)
    }

    @JvmStatic
    fun fadeIn(view: View?, durationMs: Long) {
        if (view == null) return
        view.visibility = View.VISIBLE
        val anim = AlphaAnimation(0f, 1f).apply {
            duration = durationMs
        }
        view.startAnimation(anim)
    }
}
