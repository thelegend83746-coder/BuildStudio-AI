package com.tyron.compiler

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.apk.builder.logger.Logger
import com.apk.builder.model.Project
import java.io.File
import java.util.concurrent.Executors

class CompilerAsyncTask(
    private val context: Context,
    private val project: Project,
    private val callback: CompilerCallback
) {

    fun interface CompilerCallback {
        fun onCompleted(result: CompilerResult)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    fun execute() {
        Logger.clear()
        executor.execute {
            val startTime = System.currentTimeMillis()
            try {
                Logger.log("==================================================")
                Logger.log("   🚀 INITIATING BUILD PIPELINE: ${project.name}")
                Logger.log("   Package: ${project.packageName}")
                Logger.log("   Target SDK: ${project.targetSdk} | Min SDK: ${project.minSdk}")
                Logger.log("   Language: ${project.language}")
                Logger.log("==================================================")

                // Step 1: AAPT2 Resource Compilation & Linking
                val aapt2 = AAPT2Compiler(project).apply {
                    progressListener = { msg, s, t -> postProgress(msg, 1, 7) }
                }
                aapt2.run()

                // Step 2: Source Code Compilation (Kotlin / Java)
                val sourceCompiler = KotlinJavaCompiler(project).apply {
                    progressListener = { msg, s, t -> postProgress(msg, 2, 7) }
                }
                sourceCompiler.run()

                // Step 3: D8 Dex Conversion
                val d8 = D8Compiler(project).apply {
                    progressListener = { msg, s, t -> postProgress(msg, 3, 7) }
                }
                d8.run()

                // Steps 4, 5, 6: Packaging + ZipAlign + Signing
                val finalApk = ZipSignerWrapper.packageAndSign(project) { msg, step, total ->
                    postProgress(msg, step, total)
                }

                // Step 7: Completed
                val durationMs = System.currentTimeMillis() - startTime
                val successMsg = "✅ [Build Studio] Build SUCCESS in ${durationMs}ms"
                postProgress(successMsg, 7, 7)
                Logger.log(successMsg)
                Logger.log("📦 Output APK: ${finalApk.absolutePath} (${finalApk.length()} bytes)")

                val result = CompilerResult(true, finalApk, Logger.getLogs(), null)
                mainHandler.post { callback.onCompleted(result) }

            } catch (e: Exception) {
                val durationMs = System.currentTimeMillis() - startTime
                val errMsg = e.message ?: e.toString()
                Logger.log("❌ [Build Studio] BUILD FAILED after ${durationMs}ms:\n$errMsg")
                val result = CompilerResult(false, null, Logger.getLogs(), errMsg)
                mainHandler.post { callback.onCompleted(result) }
            }
        }
    }

    private fun postProgress(msg: String, s: Int, t: Int) {
        Logger.log("[$s/$t] $msg")
    }
}
