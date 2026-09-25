package com.apk.builder

import android.app.Service
import android.content.Intent
import android.os.IBinder

class BuildService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
