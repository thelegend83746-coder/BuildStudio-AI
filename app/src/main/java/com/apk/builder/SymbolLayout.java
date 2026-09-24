package com.apk.builder;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.build.studio.R;
import io.github.rosemoe.sora.widget.CodeEditor;

public class SymbolLayout extends HorizontalScrollView {

    private LinearLayout container;
    private CodeEditor targetEditor;

    private static final String[] DEFAULT_SYMBOLS = {
            "(", ")", "{", "}", "[", "]", ";", """, "'", "=",
            "<", ">", "/", "\\", "+", "-", "*", "?", ":", "!",
            "&", "|", ".", ",", "@", "#", "$", "%", "^", "~", "`"
    };

    public SymbolLayout(Context context) {
        super(context);
        init(context);
    }

    public SymbolLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SymbolLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setHorizontalScrollBarEnabled(false);
        setBackgroundColor(Color.parseColor("#121820"));

        container = new LinearLayout(context);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER_VERTICAL);
        int padH = LayoutToolKit.dpToPx(context, 8);
        int padV = LayoutToolKit.dpToPx(context, 4);
        container.setPadding(padH, padV, padH, padV);
        addView(container, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));

        populateSymbols(DEFAULT_SYMBOLS);
    }

    public void setTargetEditor(CodeEditor editor) {
        this.targetEditor = editor;
    }

    public void populateSymbols(String[] symbols) {
        container.removeAllViews();
        Context ctx = getContext();
        for (String sym : symbols) {
            TextView chip = new TextView(ctx);
            chip.setText(sym);
            chip.setTextSize(15f);
            chip.setTextColor(Color.parseColor("#FFFFFF"));
            chip.setGravity(Gravity.CENTER);

            int pad = LayoutToolKit.dpToPx(ctx, 10);
            int padTop = LayoutToolKit.dpToPx(ctx, 6);
            chip.setPadding(pad, padTop, pad, padTop);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(LayoutToolKit.dpToPx(ctx, 3), 0, LayoutToolKit.dpToPx(ctx, 3), 0);
            chip.setLayoutParams(lp);

            chip.setBackground(LayoutToolKit.createRippleDrawable(
                    Color.parseColor("#1A222D"),
                    Color.parseColor("#2979FF"),
                    8f,
                    ctx
            ));

            chip.setOnClickListener(v -> {
                if (targetEditor != null) {
                    targetEditor.insertText(sym, 1);
                }
            });

            container.addView(chip);
        }
    }
}
