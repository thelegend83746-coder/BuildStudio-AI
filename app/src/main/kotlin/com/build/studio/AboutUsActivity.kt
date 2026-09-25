package com.build.studio

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.blogspot.atifsoftwares.animatoolib.Animatoo

class AboutUsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_about_us)

        findViewById<View>(R.id.btn_back_about).setOnClickListener { finish(); Animatoo.animateSlideRight(this) }

        findViewById<View>(R.id.link_instagram).setOnClickListener {
            openUrl("https://www.instagram.com/sun_ley_coder?igsh=ZzVpM2ExcWFudzl1")
        }

        findViewById<View>(R.id.link_telegram).setOnClickListener {
            openUrl("https://t.me/SUNLEYCODER")
        }

        findViewById<View>(R.id.link_youtube).setOnClickListener {
            openUrl("https://www.youtube.com/@SUN-LEY_CODER")
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (ignored: Exception) {}
    }

    override fun onBackPressed() {
        super.onBackPressed()
        Animatoo.animateSlideRight(this)
    }
}
