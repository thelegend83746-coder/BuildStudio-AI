package com.build.studio

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.blogspot.atifsoftwares.animatoolib.Animatoo

class SettingsMenuActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_menu)

        findViewById<View>(R.id.btn_back_settings).setOnClickListener { finish(); Animatoo.animateSlideRight(this) }

        // 1. Application
        findViewById<View>(R.id.card_setting_app).setOnClickListener {
            startActivity(Intent(this, ApplicationSettingsActivity::class.java))
        }

        // 2. Editor
        findViewById<View>(R.id.card_setting_editor).setOnClickListener {
            startActivity(Intent(this, EditorSettingsActivity::class.java))
        }

        // 3. Build & Run
        findViewById<View>(R.id.card_setting_build_run).setOnClickListener {
            startActivity(Intent(this, BuildRunSettingsActivity::class.java))
        }

        // 4. About Us
        findViewById<View>(R.id.card_setting_about).setOnClickListener {
            startActivity(Intent(this, AboutUsActivity::class.java))
        }

        // 5. Build AI Settings
        findViewById<View>(R.id.card_setting_ai).setOnClickListener {
            startActivity(Intent(this, OllamaSettingsActivity::class.java))
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        Animatoo.animateSlideRight(this)
    }
}
