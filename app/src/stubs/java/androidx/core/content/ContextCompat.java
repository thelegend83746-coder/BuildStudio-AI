package androidx.core.content;

import android.content.Context;
import android.graphics.drawable.Drawable;

public class ContextCompat {
    public static int getColor(Context context, int id) {
        return context.getResources().getColor(id);
    }
    public static Drawable getDrawable(Context context, int id) {
        return context.getResources().getDrawable(id);
    }
}
