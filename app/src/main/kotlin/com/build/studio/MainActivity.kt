package com.build.studio

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.blogspot.atifsoftwares.animatoolib.Animatoo

class MainActivity : AppCompatActivity() {

    private var permissionDialog: AlertDialog? = null
    private var hasNavigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)

        val logoBg = findViewById<LinearLayout>(R.id.logo_bg)
        val tvVersion = findViewById<TextView>(R.id.textview1)

        val anim = AnimationUtils.loadAnimation(this, R.anim.animate_card_enter)
        logoBg?.startAnimation(anim)

        if (hasStoragePermission()) {
            scheduleNavigation(1500)
        } else {
            showPermissionGateDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasStoragePermission()) {
            permissionDialog?.dismiss()
            if (!hasNavigated) {
                scheduleNavigation(300)
            }
        } else {
            showPermissionGateDialog()
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun scheduleNavigation(delayMs: Long) {
        if (hasNavigated) return
        hasNavigated = true
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing && !isDestroyed) {
                startActivity(Intent(this, ProjectListActivity::class.java))
                Animatoo.animateZoom(this)
                finish()
            }
        }, delayMs)
    }

    private fun showPermissionGateDialog() {
        if (isFinishing || isDestroyed) return
        if (permissionDialog?.isShowing == true) return

        permissionDialog = AlertDialog.Builder(this)
            .setTitle("All Files Access Required")
            .setMessage("Build Studio is an on-device Android IDE. It requires All Files Access permission to create, edit, save, and compile Android apps on your storage.\n\nPlease grant All Files Access to continue.")
            .setCancelable(false)
            .setPositiveButton("Grant Access") { _, _ ->
                requestStoragePermission()
            }
            .setNegativeButton("Exit") { _, _ ->
                finishAffinity()
            }
            .create()
        permissionDialog?.show()
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        } else {
            requestPermissions(
                arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                1001
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                scheduleNavigation(300)
            } else {
                showPermissionGateDialog()
            }
        }
    }
}
