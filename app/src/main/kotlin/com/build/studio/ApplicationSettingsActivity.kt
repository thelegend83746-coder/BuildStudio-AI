package com.build.studio

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.blogspot.atifsoftwares.animatoolib.Animatoo
import com.apk.builder.FileUtil

class ApplicationSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.application_settings)

        findViewById<View>(R.id.imageview1)?.setOnClickListener { finish(); Animatoo.animateSlideRight(this) }

        val prefs = getSharedPreferences("build_studio_app_prefs", Context.MODE_PRIVATE)

        val swDarkMode = findViewById<Switch>(R.id.switch_dark_mode)
        val swConfirm = findViewById<Switch>(R.id.switch_confirm_delete)
        val rowClearCache = findViewById<View>(R.id.clear_cache)
        val tvCacheSize = findViewById<TextView>(R.id.cache_size_text)

        swDarkMode?.isChecked = prefs.getBoolean("dark_mode", false)
        swConfirm?.isChecked = prefs.getBoolean("confirm_delete", true)

        swDarkMode?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dark_mode", isChecked).apply()
            Toast.makeText(this, "Theme preference saved", Toast.LENGTH_SHORT).show()
        }

        swConfirm?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("confirm_delete", isChecked).apply()
        }

        rowClearCache?.setOnClickListener {
            FileUtil.deleteDir(cacheDir)
            externalCacheDir?.let { FileUtil.deleteDir(it) }
            tvCacheSize?.text = "0.00 KB"
            Toast.makeText(this, "App cache cleared", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        Animatoo.animateSlideRight(this)
    }
}
