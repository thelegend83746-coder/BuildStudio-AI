package com.apk.builder;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.core.content.res.ResourcesCompat;
import com.build.studio.R;

public class FontUtil {

    private static Typeface boldTypeface;
    private static Typeface mediumTypeface;
    private static Typeface regularTypeface;

    public static Typeface getBold(Context context) {
        if (boldTypeface == null) {
            try {
                boldTypeface = ResourcesCompat.getFont(context, R.font.en_bold);
            } catch (Exception e) {
                try {
                    boldTypeface = Typeface.createFromAsset(context.getAssets(), "fonts/en_bold.ttf");
                } catch (Exception ignored) {
                    boldTypeface = Typeface.DEFAULT_BOLD;
                }
            }
        }
        return boldTypeface;
    }

    public static Typeface getMedium(Context context) {
        if (mediumTypeface == null) {
            try {
                mediumTypeface = ResourcesCompat.getFont(context, R.font.en_medium);
            } catch (Exception e) {
                try {
                    mediumTypeface = Typeface.createFromAsset(context.getAssets(), "fonts/en_medium.ttf");
                } catch (Exception ignored) {
                    mediumTypeface = Typeface.DEFAULT;
                }
            }
        }
        return mediumTypeface;
    }

    public static Typeface getRegular(Context context) {
        if (regularTypeface == null) {
            try {
                regularTypeface = ResourcesCompat.getFont(context, R.font.opensans_regular);
            } catch (Exception e) {
                try {
                    regularTypeface = Typeface.createFromAsset(context.getAssets(), "fonts/opensans_regular.ttf");
                } catch (Exception ignored) {
                    regularTypeface = Typeface.DEFAULT;
                }
            }
        }
        return regularTypeface;
    }

    public static void applyFont(View view) {
        if (view == null) return;
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            Typeface current = tv.getTypeface();
            if (current != null && current.isBold()) {
                tv.setTypeface(getBold(view.getContext()));
            } else {
                tv.setTypeface(getMedium(view.getContext()));
            }
        } else if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                applyFont(vg.getChildAt(i));
            }
        }
    }
}
