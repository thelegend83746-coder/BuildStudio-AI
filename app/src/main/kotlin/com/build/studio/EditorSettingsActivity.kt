package com.build.studio

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.blogspot.atifsoftwares.animatoolib.Animatoo

class EditorSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.editor_settings)

        findViewById<View>(R.id.imageview1)?.setOnClickListener { finish(); Animatoo.animateSlideRight(this) }

        val prefs = getSharedPreferences("build_studio_editor_prefs", Context.MODE_PRIVATE)

        val tvFontSize = findViewById<TextView>(R.id.font_size_value)
        val sbFontSize = findViewById<SeekBar>(R.id.seekbar_font_size)
        val tvTabSize = findViewById<TextView>(R.id.tab_size_value)
        val sbTabSize = findViewById<SeekBar>(R.id.seekbar_tab_size)

        val swWordWrap = findViewById<Switch>(R.id.switch_word_wrap)
        val swLineNumbers = findViewById<Switch>(R.id.switch_line_numbers)
        val swHighlightLine = findViewById<Switch>(R.id.switch_highlight_line)
        val swAutoComplete = findViewById<Switch>(R.id.switch_auto_complete)
        val swDarkTheme = findViewById<Switch>(R.id.switch_editor_dark_theme)

        val fontSize = prefs.getInt("editor_font_size", 14)
        val tabSize = prefs.getInt("editor_tab_size", 4)

        tvFontSize?.text = "${fontSize}sp"
        sbFontSize?.progress = fontSize - 10

        tvTabSize?.text = "$tabSize spaces"
        sbTabSize?.progress = tabSize - 2

        swWordWrap?.isChecked = prefs.getBoolean("editor_word_wrap", false)
        swLineNumbers?.isChecked = prefs.getBoolean("editor_line_numbers", true)
        swHighlightLine?.isChecked = prefs.getBoolean("editor_highlight_line", true)
        swAutoComplete?.isChecked = prefs.getBoolean("editor_auto_complete", true)
        swDarkTheme?.isChecked = prefs.getBoolean("editor_dark_theme", false)

        sbFontSize?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val size = progress + 10
                tvFontSize?.text = "${size}sp"
                prefs.edit().putInt("editor_font_size", size).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        sbTabSize?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val size = progress + 2
                tvTabSize?.text = "$size spaces"
                prefs.edit().putInt("editor_tab_size", size).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        swWordWrap?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("editor_word_wrap", isChecked).apply()
        }

        swLineNumbers?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("editor_line_numbers", isChecked).apply()
        }

        swHighlightLine?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("editor_highlight_line", isChecked).apply()
        }

        swAutoComplete?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("editor_auto_complete", isChecked).apply()
        }

        swDarkTheme?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("editor_dark_theme", isChecked).apply()
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        Animatoo.animateSlideRight(this)
    }
}
