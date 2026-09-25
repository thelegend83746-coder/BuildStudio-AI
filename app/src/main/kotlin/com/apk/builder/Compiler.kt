package com.apk.builder

import android.content.Context
import com.apk.builder.model.Project
import com.tyron.compiler.CompilerAsyncTask
import com.tyron.compiler.CompilerResult

class Compiler(
    private val context: Context,
    private val project: Project
) {

    interface CompilerCallback {
        fun onProgress(message: String, step: Int, totalSteps: Int)
        fun onCompleted(result: CompilerResult)
    }

    private var callback: CompilerCallback? = null

    fun setCallback(callback: CompilerCallback?) {
        this.callback = callback
    }

    fun run() {
        val task = CompilerAsyncTask(
            context,
            project
        ) { result ->
            callback?.onCompleted(result)
        }
        task.execute()
    }
}
