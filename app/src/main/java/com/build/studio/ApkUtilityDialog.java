package com.build.studio;

import android.content.Context;
import com.apk.builder.DialogUtil;
import java.io.File;

public class ApkUtilityDialog {
    public static void show(Context context, File apkFile, String appName) {
        DialogUtil.showApkUtilityDialog(context, apkFile, appName);
    }
}
