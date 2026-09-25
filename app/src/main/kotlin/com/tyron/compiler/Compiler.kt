package com.tyron.compiler

import com.apk.builder.model.Project
import java.io.File

abstract class Compiler(protected val project: Project) {

    var progressListener: ((message: String, currentStep: Int, totalSteps: Int) -> Unit)? = null

    abstract fun run()

    protected fun notifyProgress(message: String, step: Int, total: Int) {
        progressListener?.invoke(message, step, total)
    }

    protected fun deleteDirectory(dir: File?): Boolean {
        if (dir != null && dir.isDirectory) {
            val children = dir.list()
            if (children != null) {
                for (child in children) {
                    val success = deleteDirectory(File(dir, child))
                    if (!success) return false
                }
            }
            return dir.delete()
        } else if (dir != null && dir.isFile) {
            return dir.delete()
        }
        return false
    }
}
