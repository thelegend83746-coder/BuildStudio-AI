package com.build.studio

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.blogspot.atifsoftwares.animatoolib.Animatoo

class BuildRunSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.build_run_settings)

        findViewById<View>(R.id.imageview1)?.setOnClickListener { finish(); Animatoo.animateSlideRight(this) }

        val prefs = getSharedPreferences("build_studio_run_prefs", Context.MODE_PRIVATE)

        val rowMinSdk = findViewById<View>(R.id.row_min_sdk)
        val tvMinSdk = findViewById<TextView>(R.id.min_sdk_value)
        val rowTargetSdk = findViewById<View>(R.id.row_target_sdk)
        val tvTargetSdk = findViewById<TextView>(R.id.target_sdk_value)

        val swAutoSave = findViewById<Switch>(R.id.switch_auto_save_build)
        val swShowLogs = findViewById<Switch>(R.id.switch_show_logs)

        val minSdk = prefs.getInt("default_min_sdk", 21)
        val targetSdk = prefs.getInt("default_target_sdk", 34)

        tvMinSdk?.text = minSdk.toString()
        tvTargetSdk?.text = targetSdk.toString()

        swAutoSave?.isChecked = prefs.getBoolean("auto_save_before_build", true)
        swShowLogs?.isChecked = prefs.getBoolean("show_build_logs", true)

        swAutoSave?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_save_before_build", isChecked).apply()
        }

        swShowLogs?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("show_build_logs", isChecked).apply()
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        Animatoo.animateSlideRight(this)
    }
}
