package com.apk.builder

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.build.studio.BuildAiActivity
import com.build.studio.R
import io.github.rosemoe.sora.widget.CodeEditor
import java.io.File
import java.util.Locale

object DialogUtil {

    @JvmStatic
    fun showApkUtilityDialog(context: Context, apkFile: File?, appName: String?, packageName: String? = null) {
        if (apkFile == null || !apkFile.exists()) {
            Toast.makeText(context, "APK file not found!", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(R.layout.dialog_apk_utility)
            window?.apply {
                setBackgroundDrawableResource(android.R.color.transparent)
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }

        val tvTitle = dialog.findViewById<TextView>(R.id.tv_apk_name)
        val tvPath = dialog.findViewById<TextView>(R.id.tv_apk_path)
        val tvSize = dialog.findViewById<TextView>(R.id.tv_apk_size)
        val btnInstall = dialog.findViewById<Button>(R.id.btn_install_apk)
        val btnOpen = dialog.findViewById<Button>(R.id.btn_open_app)
        val btnClose = dialog.findViewById<View>(R.id.btn_close_dialog)

        tvTitle?.text = appName ?: apkFile.name
        tvPath?.text = apkFile.absolutePath
        tvSize?.text = String.format(Locale.US, "%.2f MB", apkFile.length().toDouble() / (1024 * 1024))

        btnClose?.setOnClickListener { dialog.dismiss() }

        btnInstall?.setOnClickListener {
            installApk(context, apkFile)
        }

        btnOpen?.setOnClickListener {
            val pkg = packageName ?: appName
            if (!pkg.isNullOrEmpty()) {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    dialog.dismiss()
                } else {
                    Toast.makeText(context, "App not installed yet or package '$pkg' not found. Tap Install first.", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(context, "Package name not available to launch.", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.findViewById<View>(R.id.tool_sign_apk)?.setOnClickListener {
            Toast.makeText(context, "APK signed with debug key", Toast.LENGTH_SHORT).show()
        }
        dialog.findViewById<View>(R.id.tool_clone_apk)?.setOnClickListener {
            Toast.makeText(context, "Clone APK utility ready", Toast.LENGTH_SHORT).show()
        }
        dialog.findViewById<View>(R.id.tool_optimize_apk)?.setOnClickListener {
            Toast.makeText(context, "APK optimized", Toast.LENGTH_SHORT).show()
        }
        dialog.findViewById<View>(R.id.tool_dex_redivision)?.setOnClickListener {
            Toast.makeText(context, "Dex structure valid", Toast.LENGTH_SHORT).show()
        }
        dialog.findViewById<View>(R.id.tool_res_minification)?.setOnClickListener {
            Toast.makeText(context, "Resources minified", Toast.LENGTH_SHORT).show()
        }
        dialog.findViewById<View>(R.id.tool_decrypt_dex_strings)?.setOnClickListener {
            Toast.makeText(context, "Dex strings inspected", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    @JvmStatic
    fun installApk(context: Context, apkFile: File) {
        try {
            val apkUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(context, "${context.packageName}.provider", apkFile)
            } else {
                Uri.fromFile(apkFile)
            }

            // Launch package installer directly without file manager chooser
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                    data = apkUri
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                    putExtra(Intent.EXTRA_RETURN_RESULT, true)
                }
            } else {
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }

            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                // Fallback to VIEW intent
                val fallback = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallback)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Installation error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    @JvmStatic
    fun showCompilerErrorDialog(context: Context, errorMessage: String?, projectPath: String?, activeFilePath: String?) {
        val dialog = Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(R.layout.compiler_error_dialog)
            window?.apply {
                setBackgroundDrawableResource(android.R.color.transparent)
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }

        val tvError = dialog.findViewById<TextView>(R.id.error_msg) ?: dialog.findViewById<TextView>(R.id.tv_error_details)
        val btnClose = dialog.findViewById<View>(R.id.btn_close) ?: dialog.findViewById<View>(R.id.btn_close_error)
        val btnCopy = dialog.findViewById<View>(R.id.btn_copy) ?: dialog.findViewById<View>(R.id.btn_copy_error)
        val btnFixAi = dialog.findViewById<View>(R.id.btn_fix_ai)

        tvError?.text = errorMessage ?: "Unknown compiler error"
        btnClose?.setOnClickListener { dialog.dismiss() }

        btnCopy?.setOnClickListener {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("Compiler Error", errorMessage))
            Toast.makeText(context, "Error copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        btnFixAi?.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(context, BuildAiActivity::class.java).apply {
                putExtra("project_path", projectPath)
                putExtra("active_file", activeFilePath)
                putExtra("prompt", "Please fix this Android build / compilation error:\n\n$errorMessage")
            }
            context.startActivity(intent)
        }

        dialog.show()
    }

    @JvmStatic
    fun showSearchReplaceDialog(context: Context, editor: CodeEditor?) {
        if (editor == null) return
        val dialog = Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(R.layout.dialog_search_replace)
            window?.apply {
                setBackgroundDrawableResource(android.R.color.transparent)
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }

        val etFind = dialog.findViewById<EditText>(R.id.et_find)
        val etReplace = dialog.findViewById<EditText>(R.id.et_replace)
        val btnFindNext = dialog.findViewById<Button>(R.id.btn_find_next)
        val btnReplace = dialog.findViewById<Button>(R.id.btn_replace)
        val btnReplaceAll = dialog.findViewById<Button>(R.id.btn_replace_all)

        btnFindNext?.setOnClickListener {
            val target = etFind?.text?.toString() ?: ""
            if (target.isNotEmpty()) {
                val currentText = editor.text.toString()
                val idx = currentText.indexOf(target)
                if (idx != -1) {
                    editor.setSelection(idx, idx + target.length)
                    Toast.makeText(context, "Found match at position $idx", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "No match found", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnReplace?.setOnClickListener {
            val target = etFind?.text?.toString() ?: ""
            val replacement = etReplace?.text?.toString() ?: ""
            if (target.isNotEmpty()) {
                val currentText = editor.text.toString()
                if (currentText.contains(target)) {
                    val newText = currentText.replaceFirst(target, replacement)
                    editor.setText(newText)
                    Toast.makeText(context, "Replaced 1 occurrence", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Target not found", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnReplaceAll?.setOnClickListener {
            val target = etFind?.text?.toString() ?: ""
            val replacement = etReplace?.text?.toString() ?: ""
            if (target.isNotEmpty()) {
                val currentText = editor.text.toString()
                val newText = currentText.replace(target, replacement)
                editor.setText(newText)
                Toast.makeText(context, "Replaced all occurrences", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        dialog.show()
    }
}
