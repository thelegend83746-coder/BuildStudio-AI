package com.apk.builder

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.rosemoe.sora.widget.CodeEditor

class SymbolLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    private lateinit var container: LinearLayout
    private var targetEditor: CodeEditor? = null

    companion object {
        val DEFAULT_SYMBOLS = arrayOf(
            "(", ")", "{", "}", "[", "]", ";", "\"", "'", "=",
            "<", ">", "/", "\\", "+", "-", "*", "?", ":", "!",
            "&", "|", ".", ",", "@", "#", "$", "%", "^", "~", "`",
            "fun", "val", "var", "class", "return", "import"
        )
    }

    init {
        initView(context)
    }

    private fun initView(context: Context) {
        isHorizontalScrollBarEnabled = false
        setBackgroundColor(Color.parseColor("#121820"))

        container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padH = LayoutToolKit.dpToPx(context, 8f)
            val padV = LayoutToolKit.dpToPx(context, 4f)
            setPadding(padH, padV, padH, padV)
        }
        addView(container, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))

        populateSymbols(DEFAULT_SYMBOLS)
    }

    fun setTargetEditor(editor: CodeEditor?) {
        this.targetEditor = editor
    }

    fun populateSymbols(symbols: Array<String>) {
        container.removeAllViews()
        val ctx = context
        for (sym in symbols) {
            val chip = TextView(ctx).apply {
                text = sym
                textSize = 15f
                setTextColor(Color.parseColor("#FFFFFF"))
                gravity = Gravity.CENTER

                val pad = LayoutToolKit.dpToPx(ctx, 10f)
                val padTop = LayoutToolKit.dpToPx(ctx, 6f)
                setPadding(pad, padTop, pad, padTop)

                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(LayoutToolKit.dpToPx(ctx, 3f), 0, LayoutToolKit.dpToPx(ctx, 3f), 0)
                }
                layoutParams = lp

                background = LayoutToolKit.createRippleDrawable(
                    Color.parseColor("#1A222D"),
                    Color.parseColor("#2979FF"),
                    8f,
                    ctx
                )

                setOnClickListener {
                    targetEditor?.insertText(sym, sym.length)
                }
            }
            container.addView(chip)
        }
    }
}
