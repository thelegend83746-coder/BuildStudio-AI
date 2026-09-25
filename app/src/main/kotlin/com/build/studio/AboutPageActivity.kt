package com.build.studio

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.blogspot.atifsoftwares.animatoolib.Animatoo

class AboutPageActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.about_page)

        findViewById<View>(R.id.imageview1)?.setOnClickListener { finish(); Animatoo.animateSlideRight(this) }

        // Social Links
        findViewById<View>(R.id.l5)?.setOnClickListener {
            openUrl("https://instagram.com")
        }

        findViewById<View>(R.id.l6)?.setOnClickListener {
            openUrl("https://t.me")
        }

        findViewById<View>(R.id.l7)?.setOnClickListener {
            openUrl("https://youtube.com")
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            // ignore
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        Animatoo.animateSlideRight(this)
    }
}
