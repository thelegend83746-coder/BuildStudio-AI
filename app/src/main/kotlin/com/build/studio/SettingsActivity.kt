package com.build.studio

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.blogspot.atifsoftwares.animatoolib.Animatoo

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings)

        findViewById<View>(R.id.imageview1)?.setOnClickListener { finish(); Animatoo.animateSlideRight(this) }

        // 1. Application
        findViewById<View>(R.id.application)?.setOnClickListener {
            startActivity(Intent(this, ApplicationSettingsActivity::class.java))
            Animatoo.animateSlideLeft(this)
        }

        // 2. Editor
        findViewById<View>(R.id.editor)?.setOnClickListener {
            startActivity(Intent(this, EditorSettingsActivity::class.java))
            Animatoo.animateSlideLeft(this)
        }

        // 3. Build & Run
        findViewById<View>(R.id.build_run)?.setOnClickListener {
            startActivity(Intent(this, BuildRunSettingsActivity::class.java))
            Animatoo.animateSlideLeft(this)
        }

        // 4. About Us
        findViewById<View>(R.id.about_us)?.setOnClickListener {
            startActivity(Intent(this, AboutPageActivity::class.java))
            Animatoo.animateSlideLeft(this)
        }

        // 5. Build AI Settings
        (findViewById<View>(R.id.card_ai_settings) ?: findViewById<View>(R.id.ai_settings))?.setOnClickListener {
            startActivity(Intent(this, OllamaSettingsActivity::class.java))
            Animatoo.animateSlideLeft(this)
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        Animatoo.animateSlideRight(this)
    }
}
