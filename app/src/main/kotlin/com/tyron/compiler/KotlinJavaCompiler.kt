package com.tyron.compiler

import com.apk.builder.ApplicationLoader
import com.apk.builder.logger.Logger
import com.apk.builder.model.Project
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class KotlinJavaCompiler(project: Project) : Compiler(project) {

    override fun run() {
        val lang = project.language
        notifyProgress("[$lang] Compiling source code files against android.jar...", 2, 7)

        val androidJar = ApplicationLoader.instance.getAndroidJar(project.targetSdk)
        if (!androidJar.exists() || androidJar.length() == 0L) {
            throw IllegalStateException(
                "❌ [Build Engine Missing File] android.jar not found at: ${androidJar.absolutePath}\n" +
                "Please add android.jar as specified in readme.txt."
            )
        }

        val genDir = File(project.buildDir, "gen")
        val srcDir = project.srcDir
        val classesDir = File(project.buildDir, "bin/classes").apply { mkdirs() }

        val javaFiles = mutableListOf<File>()
        val kotlinFiles = mutableListOf<File>()

        collectSourceFiles(srcDir, javaFiles, kotlinFiles)
        collectSourceFiles(genDir, javaFiles, kotlinFiles)

        if (javaFiles.isEmpty() && kotlinFiles.isEmpty()) {
            throw Exception("No Kotlin or Java source files found in ${srcDir.absolutePath}")
        }

        val cl = ApplicationLoader.instance.getToolchainClassLoader()
        var compilerRan = false

        // Compile Java files via ECJ batch compiler if present
        if (javaFiles.isNotEmpty()) {
            Logger.log("[Java Compiler] Compiling ${javaFiles.size} Java files via ECJ...")
            val args = mutableListOf<String>().apply {
                add("-1.8")
                add("-bootclasspath")
                add(androidJar.absolutePath)
                add("-cp")
                add(androidJar.absolutePath)
                add("-d")
                add(classesDir.absolutePath)
                add("-proc:none")
                add("-nowarn")
                add("-encoding")
                add("UTF-8")
                for (f in javaFiles) {
                    add(f.absolutePath)
                }
            }

            val outWriter = StringWriter()
            val errWriter = StringWriter()
            val outPw = PrintWriter(outWriter)
            val errPw = PrintWriter(errWriter)

            var success = false
            try {
                val ecjClass = Class.forName("org.eclipse.jdt.internal.compiler.batch.Main", true, cl)
                val method = ecjClass.getMethod("compile", Array<String>::class.java, PrintWriter::class.java, PrintWriter::class.java, Any::class.java)
                val res = method.invoke(null, args.toTypedArray(), outPw, errPw, null)
                if (res is Boolean) success = res
                compilerRan = true
            } catch (e: ClassNotFoundException) {
                try {
                    val batchClass = Class.forName("org.eclipse.jdt.core.compiler.batch.BatchCompiler", true, cl)
                    val method = batchClass.getMethod("compile", Array<String>::class.java, PrintWriter::class.java, PrintWriter::class.java, Any::class.java)
                    val res = method.invoke(null, args.toTypedArray(), outPw, errPw, null)
                    if (res is Boolean) success = res
                    compilerRan = true
                } catch (ignored: Exception) {
                    // ECJ not bundled yet
                }
            }

            outPw.flush()
            errPw.flush()

            if (compilerRan && !success) {
                val err = errWriter.toString().trim().ifEmpty { outWriter.toString().trim() }
                throw Exception("Java Compilation Failed:\n$err")
            }
        }

        // Compile Kotlin files if present
        if (kotlinFiles.isNotEmpty()) {
            Logger.log("[Kotlin Compiler] Compiling ${kotlinFiles.size} Kotlin files...")
            try {
                val k2JvmClass = Class.forName("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", true, cl)
                val compilerInstance = k2JvmClass.getDeclaredConstructor().newInstance()
                val execMethod = k2JvmClass.getMethod("exec", PrintWriter::class.java, Array<String>::class.java)

                val ktArgs = mutableListOf<String>().apply {
                    add("-jvm-target")
                    add("1.8")
                    add("-classpath")
                    add(androidJar.absolutePath)
                    add("-d")
                    add(classesDir.absolutePath)
                    for (f in kotlinFiles) {
                        add(f.absolutePath)
                    }
                }

                val errWriter = StringWriter()
                val res = execMethod.invoke(compilerInstance, PrintWriter(errWriter), ktArgs.toTypedArray())
                val code = res?.toString() ?: "0"
                if (code != "OK" && code != "0") {
                    throw Exception("Kotlin Compilation Failed:\n$errWriter")
                }
                compilerRan = true
            } catch (e: ClassNotFoundException) {
                // Not found
            }
        }

        if (!compilerRan) {
            throw IllegalStateException(
                "❌ [Build Engine Missing File] Offline Kotlin/Java compiler engine not found!\n" +
                "Expected ecj.jar / kotlinc-embeddable.jar inside app files or /storage/emulated/0/test-folder/compiler_engine/\n" +
                "Please add the offline compiler jar as instructed in readme.txt."
            )
        }

        Logger.log("[Source Compiler] Compilation successful. Bytecode generated at: ${classesDir.absolutePath}")
    }

    private fun collectSourceFiles(dir: File, javaList: MutableList<File>, ktList: MutableList<File>) {
        if (!dir.exists()) return
        val files = dir.listFiles() ?: return
        for (f in files) {
            if (f.isDirectory) {
                collectSourceFiles(f, javaList, ktList)
            } else if (f.name.endsWith(".java")) {
                javaList.add(f)
            } else if (f.name.endsWith(".kt")) {
                ktList.add(f)
            }
        }
    }
}
