package com.tyron.compiler

import com.apk.builder.ApplicationLoader
import com.apk.builder.BinaryExecutor
import com.apk.builder.FileUtil
import com.apk.builder.logger.Logger
import com.apk.builder.model.Project
import java.io.File

class AAPT2Compiler(project: Project) : Compiler(project) {

    override fun run() {
        notifyProgress("[AAPT2] Checking resource compiler requirements...", 1, 7)

        val aapt2Binary = ApplicationLoader.instance.getAAPT2Binary()
        if (!aapt2Binary.exists()) {
            throw IllegalStateException(
                "❌ [Build Engine Missing File] AAPT2 binary not found!\n" +
                "Expected path: ${aapt2Binary.absolutePath} or /storage/emulated/0/test-folder/compiler_engine/aapt2\n" +
                "Please add the offline aapt2 binary as instructed in readme.txt."
            )
        }
        try { aapt2Binary.setExecutable(true, false) } catch (_: Throwable) {}

        val androidJar = ApplicationLoader.instance.getAndroidJar(project.targetSdk)
        if (!androidJar.exists() || androidJar.length() == 0L) {
            throw IllegalStateException(
                "❌ [Build Engine Missing File] android.jar platform framework not found!\n" +
                "Expected path: ${androidJar.absolutePath} or /storage/emulated/0/test-folder/compiler_engine/android.jar\n" +
                "Please add android.jar (SDK API ${project.targetSdk}) as instructed in readme.txt."
            )
        }

        val buildDir = project.buildDir
        val compiledResDir = File(buildDir, "compiled_res")
        val genDir = File(buildDir, "gen")
        val binDir = project.binDir

        deleteDirectory(compiledResDir)
        deleteDirectory(genDir)
        compiledResDir.mkdirs()
        genDir.mkdirs()
        binDir.mkdirs()

        val resDir = project.resDir
        if (!resDir.exists()) resDir.mkdirs()

        val valuesDir = File(resDir, "values")
        if (!valuesDir.exists()) valuesDir.mkdirs()

        val stringsXml = File(valuesDir, "strings.xml")
        if (!stringsXml.exists()) {
            FileUtil.writeFile(
                stringsXml.absolutePath,
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n    <string name=\"app_name\">${project.name}</string>\n</resources>\n"
            )
        }

        val manifestFile = project.manifestFile
        if (!manifestFile.exists()) {
            FileUtil.writeFile(
                manifestFile.absolutePath,
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "    package=\"${project.packageName}\">\n" +
                "    <application\n" +
                "        android:allowBackup=\"true\"\n" +
                "        android:label=\"${project.name}\">\n" +
                "        <activity\n" +
                "            android:name=\".MainActivity\"\n" +
                "            android:exported=\"true\">\n" +
                "            <intent-filter>\n" +
                "                <action android:name=\"android.intent.action.MAIN\" />\n" +
                "                <category android:name=\"android.intent.category.LAUNCHER\" />\n" +
                "            </intent-filter>\n" +
                "        </activity>\n" +
                "    </application>\n" +
                "</manifest>\n"
            )
        }

        // AAPT2 Compile Stage: res directory -> compiled_res.zip
        val compiledZip = File(compiledResDir, "resources.zip")
        val compileCmd = mutableListOf<String>().apply {
            add(aapt2Binary.absolutePath)
            add("compile")
            add("--dir")
            add(resDir.absolutePath)
            add("-o")
            add(compiledZip.absolutePath)
        }

        notifyProgress("[AAPT2] Compiling Android XML & binary resources...", 1, 7)
        Logger.log("[AAPT2] Executing: ${compileCmd.joinToString(" ")}")
        val compileResult = BinaryExecutor.execute(compileCmd, buildDir)
        if (!compileResult.isSuccess) {
            throw Exception("AAPT2 resource compile failed:\n${compileResult.output}")
        }

        // AAPT2 Link Stage: compiled_res.zip + android.jar + manifest -> resources.ap_ + R.java
        val resourcesApk = File(binDir, "resources.ap_")
        val linkCmd = mutableListOf<String>().apply {
            add(aapt2Binary.absolutePath)
            add("link")
            add("-I")
            add(androidJar.absolutePath)
            add("--manifest")
            add(manifestFile.absolutePath)
            add("--java")
            add(genDir.absolutePath)
            add("-o")
            add(resourcesApk.absolutePath)
            add("--auto-add-overlay")
            add(compiledZip.absolutePath)
        }

        notifyProgress("[AAPT2] Linking resources & generating R.java symbol table...", 1, 7)
        Logger.log("[AAPT2] Executing: ${linkCmd.joinToString(" ")}")
        val linkResult = BinaryExecutor.execute(linkCmd, buildDir)
        if (!linkResult.isSuccess) {
            throw Exception("AAPT2 resource link failed:\n${linkResult.output}")
        }

        if (!resourcesApk.exists() || resourcesApk.length() == 0L) {
            throw Exception("AAPT2 link completed but resources.ap_ was not created.")
        }

        Logger.log("[AAPT2] Resources successfully compiled and packaged into resources.ap_ (${resourcesApk.length()} bytes)")
    }
}
