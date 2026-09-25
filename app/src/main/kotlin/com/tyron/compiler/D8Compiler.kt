package com.tyron.compiler

import com.apk.builder.ApplicationLoader
import com.apk.builder.logger.Logger
import com.apk.builder.model.Project
import java.io.File

class D8Compiler(project: Project) : Compiler(project) {

    override fun run() {
        val toolName = project.dexer
        notifyProgress("[$toolName] Converting .class bytecode into Android Dalvik executable (classes.dex)...", 3, 7)

        val androidJar = ApplicationLoader.instance.getAndroidJar(project.targetSdk)
        val classesDir = File(project.buildDir, "bin/classes")
        val binDir = project.binDir
        binDir.mkdirs()

        val classFiles = mutableListOf<File>()
        collectClassFiles(classesDir, classFiles)

        if (classFiles.isEmpty()) {
            throw Exception("No .class bytecode files found in ${classesDir.absolutePath} for DEX conversion.")
        }

        Logger.log("[$toolName] Converting ${classFiles.size} bytecode files into classes.dex")
        val cl = ApplicationLoader.instance.getToolchainClassLoader()

        val args = mutableListOf<String>().apply {
            add("--min-api")
            add(project.minSdk.toString())
            add("--lib")
            add(androidJar.absolutePath)
            add("--output")
            add(binDir.absolutePath)
            add("--release")
            for (f in classFiles) {
                add(f.absolutePath)
            }
        }

        try {
            val d8Class = Class.forName("com.android.tools.r8.D8", true, cl)
            val mainMethod = d8Class.getMethod("main", Array<String>::class.java)
            mainMethod.invoke(null, args.toTypedArray())
        } catch (e: ClassNotFoundException) {
            throw IllegalStateException(
                "❌ [Build Engine Missing File] D8 / Dex compiler engine not found on classpath!\n" +
                "Expected d8.jar / r8.jar inside app files or /storage/emulated/0/test-folder/compiler_engine/\n" +
                "Please add d8.jar as instructed in readme.txt."
            )
        } catch (e: Exception) {
            throw Exception("D8 execution error: ${e.message}", e)
        }

        val dexFile = File(binDir, "classes.dex")
        if (!dexFile.exists() || dexFile.length() == 0L) {
            throw Exception("$toolName completed but failed to produce classes.dex")
        }

        Logger.log("[$toolName] classes.dex generated successfully (${dexFile.length()} bytes)")
    }

    private fun collectClassFiles(dir: File, list: MutableList<File>) {
        if (!dir.exists()) return
        val files = dir.listFiles() ?: return
        for (f in files) {
            if (f.isDirectory) {
                collectClassFiles(f, list)
            } else if (f.name.endsWith(".class")) {
                list.add(f)
            }
        }
    }
}
