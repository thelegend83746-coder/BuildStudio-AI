package com.apk.builder;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.AlphaAnimation;

public class LayoutToolKit {

    public static int dpToPx(Context context, float dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                context.getResources().getDisplayMetrics()
        );
    }

    public static float pxToDp(Context context, float px) {
        return px / context.getResources().getDisplayMetrics().density;
    }

    public static RippleDrawable createRippleDrawable(int normalColor, int rippleColor, float cornerRadiusDp, Context context) {
        GradientDrawable content = new GradientDrawable();
        content.setShape(GradientDrawable.RECTANGLE);
        content.setColor(normalColor);
        content.setCornerRadius(dpToPx(context, cornerRadiusDp));

        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.RECTANGLE);
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dpToPx(context, cornerRadiusDp));

        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask);
    }

    public static void fadeIn(View view, long durationMs) {
        if (view == null) return;
        view.setVisibility(View.VISIBLE);
        AlphaAnimation anim = new AlphaAnimation(0f, 1f);
        anim.setDuration(durationMs);
        view.startAnimation(anim);
    }
}
