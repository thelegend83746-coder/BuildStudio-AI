package com.apk.builder.logger

import java.util.concurrent.CopyOnWriteArrayList

object Logger {
    private val logEntries = CopyOnWriteArrayList<String>()
    private var logListener: ((String) -> Unit)? = null

    @JvmStatic
    fun log(message: String) {
        logEntries.add(message)
        logListener?.invoke(message)
    }

    @JvmStatic
    fun clear() {
        logEntries.clear()
    }

    @JvmStatic
    fun getLogs(): String {
        return logEntries.joinToString("\n")
    }

    @JvmStatic
    fun setListener(listener: ((String) -> Unit)?) {
        this.logListener = listener
    }
}
