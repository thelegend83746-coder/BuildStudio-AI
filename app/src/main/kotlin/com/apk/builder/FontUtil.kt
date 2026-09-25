package com.apk.builder

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.build.studio.R

object FontUtil {

    private var boldTypeface: Typeface? = null
    private var mediumTypeface: Typeface? = null
    private var regularTypeface: Typeface? = null

    @JvmStatic
    fun getBold(context: Context): Typeface {
        if (boldTypeface == null) {
            boldTypeface = try {
                ResourcesCompat.getFont(context, R.font.en_bold)
            } catch (e: Exception) {
                try {
                    Typeface.createFromAsset(context.assets, "fonts/en_bold.ttf")
                } catch (ignored: Exception) {
                    Typeface.DEFAULT_BOLD
                }
            }
        }
        return boldTypeface ?: Typeface.DEFAULT_BOLD
    }

    @JvmStatic
    fun getMedium(context: Context): Typeface {
        if (mediumTypeface == null) {
            mediumTypeface = try {
                ResourcesCompat.getFont(context, R.font.en_medium)
            } catch (e: Exception) {
                try {
                    Typeface.createFromAsset(context.assets, "fonts/en_medium.ttf")
                } catch (ignored: Exception) {
                    Typeface.DEFAULT
                }
            }
        }
        return mediumTypeface ?: Typeface.DEFAULT
    }

    @JvmStatic
    fun getRegular(context: Context): Typeface {
        if (regularTypeface == null) {
            regularTypeface = try {
                ResourcesCompat.getFont(context, R.font.opensans_regular)
            } catch (e: Exception) {
                try {
                    Typeface.createFromAsset(context.assets, "fonts/opensans_regular.ttf")
                } catch (ignored: Exception) {
                    Typeface.DEFAULT
                }
            }
        }
        return regularTypeface ?: Typeface.DEFAULT
    }

    @JvmStatic
    fun applyFont(view: View?) {
        if (view == null) return
        if (view is TextView) {
            val current = view.typeface
            if (current != null && current.isBold) {
                view.typeface = getBold(view.context)
            } else {
                view.typeface = getMedium(view.context)
            }
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyFont(view.getChildAt(i))
            }
        }
    }
}
