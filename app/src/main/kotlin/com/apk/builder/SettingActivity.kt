package com.apk.builder

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.build.studio.OllamaSettingsActivity
import com.build.studio.R
import java.io.File

class SettingActivity : AppCompatActivity() {

    private lateinit var spFontSize: Spinner
    private lateinit var swWordWrap: Switch
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings)
        overridePendingTransition(R.anim.animate_slide_left_enter, R.anim.animate_slide_left_exit)

        prefs = getSharedPreferences("build_studio_settings", Context.MODE_PRIVATE)

        initViews()
    }

    private fun initViews() {
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        findViewById<View>(R.id.card_ai_settings)?.setOnClickListener {
            startActivity(Intent(this, OllamaSettingsActivity::class.java))
            overridePendingTransition(R.anim.animate_slide_left_enter, R.anim.animate_slide_left_exit)
        }

        findViewById<Button>(R.id.btn_clear_cache)?.setOnClickListener {
            clearAppCache()
        }

        spFontSize = findViewById(R.id.sp_editor_font_size)
        swWordWrap = findViewById(R.id.sw_word_wrap)

        setupEditorSettings()
    }

    private fun setupEditorSettings() {
        val sizes = arrayOf("12 sp (Small)", "14 sp (Medium)", "16 sp (Default)", "18 sp (Large)", "20 sp (Extra Large)")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sizes)
        spFontSize.adapter = adapter

        val savedSize = prefs.getInt("editor_font_size", 14)
        val selectionIndex = when (savedSize) {
            12 -> 0
            14 -> 1
            16 -> 2
            18 -> 3
            20 -> 4
            else -> 1
        }
        spFontSize.setSelection(selectionIndex)

        spFontSize.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val sizeValues = intArrayOf(12, 14, 16, 18, 20)
                prefs.edit().putInt("editor_font_size", sizeValues[position]).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val savedWrap = prefs.getBoolean("editor_word_wrap", false)
        swWordWrap.isChecked = savedWrap
        swWordWrap.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("editor_word_wrap", isChecked).apply()
            Toast.makeText(this, "Word wrap " + (if (isChecked) "enabled" else "disabled"), Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearAppCache() {
        try {
            FileUtil.deleteDir(cacheDir)
            FileUtil.deleteDir(codeCacheDir)
            externalCacheDir?.let { FileUtil.deleteDir(it) }
            Toast.makeText(this, "App cache cleared successfully!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Cache clear failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
