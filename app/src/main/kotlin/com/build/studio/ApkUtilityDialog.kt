package com.build.studio

import android.content.Context
import com.apk.builder.DialogUtil
import java.io.File

object ApkUtilityDialog {
    @JvmStatic
    fun show(context: Context, apkFile: File, appName: String) {
        DialogUtil.showApkUtilityDialog(context, apkFile, appName)
    }
}
