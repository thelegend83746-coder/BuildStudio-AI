package com.apk.builder

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Environment
import com.apk.builder.logger.Logger
import java.io.File
import java.io.InputStream
import dalvik.system.DexClassLoader

class ApplicationLoader : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        initCompilerEnvironment()
    }

    private fun initCompilerEnvironment() {
        Thread {
            try {
                val filesDir = filesDir

                // Check external engine directory fallback (/storage/emulated/0/test-folder/compiler_engine/)
                val extEngineDir = File("/storage/emulated/0/test-folder/compiler_engine")

                // 1. Android platform framework jar
                val androidJar = File(filesDir, "android.jar")
                if (!androidJar.exists() || androidJar.length() == 0L) {
                    val extAndroidJar = File(extEngineDir, "android.jar")
                    if (extAndroidJar.exists()) {
                        FileUtil.copyFile(extAndroidJar, androidJar)
                    }
                }

                // 2. Signing keys
                val keysDir = File(filesDir, "keys")
                if (!keysDir.exists()) keysDir.mkdirs()
                val pk8 = File(keysDir, "testkey.pk8")
                val pem = File(keysDir, "testkey.x509.pem")
                if (!pk8.exists() || pk8.length() == 0L) {
                    try {
                        assets.open("keys/testkey.pk8").use { input ->
                            pk8.outputStream().use { output -> input.copyTo(output) }
                        }
                    } catch (_: Exception) {}
                    if (!pk8.exists() || pk8.length() == 0L) {
                        val extPk8 = File(extEngineDir, "testkey.pk8")
                        if (extPk8.exists()) FileUtil.copyFile(extPk8, pk8)
                    }
                }
                if (!pem.exists() || pem.length() == 0L) {
                    try {
                        assets.open("keys/testkey.x509.pem").use { input ->
                            pem.outputStream().use { output -> input.copyTo(output) }
                        }
                    } catch (_: Exception) {}
                    if (!pem.exists() || pem.length() == 0L) {
                        val extPem = File(extEngineDir, "testkey.x509.pem")
                        if (extPem.exists()) FileUtil.copyFile(extPem, pem)
                    }
                }

                // 3. AAPT2 binary
                val aapt2 = getAAPT2Binary()
                if (!aapt2.exists()) {
                    val extAapt2 = File(extEngineDir, "aapt2")
                    if (extAapt2.exists()) {
                        val internalBin = File(filesDir, "bin/aapt2").apply { parentFile?.mkdirs() }
                        FileUtil.copyFile(extAapt2, internalBin)
                        internalBin.setExecutable(true, false)
                    }
                }
                if (aapt2.exists()) {
                    try { aapt2.setExecutable(true, false) } catch (_: Throwable) {}
                }

                // 4. Dex ClassLoader for offline jars if present
                val toolchainJars = mutableListOf<String>()
                val cpJar = File(filesDir, "cp-android-v6.jar")
                if (cpJar.exists()) toolchainJars.add(cpJar.absolutePath)
                val extD8 = File(extEngineDir, "d8.jar")
                if (extD8.exists()) toolchainJars.add(extD8.absolutePath)
                val extEcj = File(extEngineDir, "ecj.jar")
                if (extEcj.exists()) toolchainJars.add(extEcj.absolutePath)
                val extKotlinc = File(extEngineDir, "kotlinc-embeddable.jar")
                if (extKotlinc.exists()) toolchainJars.add(extKotlinc.absolutePath)

                if (toolchainJars.isNotEmpty()) {
                    val dexOptDir = File(cacheDir, "dex_opt").apply { mkdirs() }
                    toolchainClassLoaderInternal = DexClassLoader(
                        toolchainJars.joinToString(File.pathSeparator),
                        dexOptDir.absolutePath,
                        applicationInfo.nativeLibraryDir,
                        classLoader
                    )
                }

            } catch (e: Exception) {
                Logger.log("[ApplicationLoader] Environment init: ${e.message}")
            }
        }.start()
    }

    fun getAAPT2Binary(): File {
        // 1. Native library dir from APK installation (extractNativeLibs="true")
        try {
            val nativeAapt2 = File(applicationInfo.nativeLibraryDir, "libaapt2.so")
            if (nativeAapt2.exists() && nativeAapt2.length() > 0L) {
                nativeAapt2.setExecutable(true, false)
                return nativeAapt2
            }
        } catch (_: Throwable) {}

        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        val archName = when {
            abi.contains("arm64") -> "arm64-v8a"
            abi.contains("armeabi") -> "armeabi-v7a"
            abi.contains("x86_64") -> "x86_64"
            else -> "x86"
        }

        // 2. Internal storage locations
        val bin1 = File(filesDir, "bin/aapt2")
        if (bin1.exists() && bin1.length() > 0L) return bin1
        val bin2 = File(filesDir, "bin/$archName/aapt2")
        if (bin2.exists() && bin2.length() > 0L) return bin2

        // 3. Check external engine directory fallback
        val extBin = File("/storage/emulated/0/test-folder/compiler_engine/aapt2")
        if (extBin.exists() && extBin.length() > 0L) return extBin
        val extArchBin = File("/storage/emulated/0/test-folder/compiler_engine/$archName/aapt2")
        if (extArchBin.exists() && extArchBin.length() > 0L) return extArchBin

        val nativeFallback = File(applicationInfo.nativeLibraryDir, "libaapt2.so")
        return if (nativeFallback.exists()) nativeFallback else bin1
    }

    fun getAndroidJar(targetSdk: Int): File {
        // Priority 1: Specific platform jar in internal filesDir
        val pJar = File(filesDir, "platforms/android-$targetSdk/android.jar")
        if (pJar.exists() && pJar.length() > 0L) return pJar

        // Extract specific platform jar from assets if present
        try {
            pJar.parentFile?.mkdirs()
            assets.open("platforms/android-$targetSdk/android.jar").use { input ->
                pJar.outputStream().use { output -> input.copyTo(output) }
            }
            if (pJar.exists() && pJar.length() > 0L) return pJar
        } catch (_: Exception) {}

        // Priority 2: Generic android.jar in filesDir
        val mainJar = File(filesDir, "android.jar")
        if (mainJar.exists() && mainJar.length() > 0L) return mainJar

        // Extract generic android.jar from assets
        try {
            assets.open("android.jar").use { input ->
                mainJar.outputStream().use { output -> input.copyTo(output) }
            }
            if (mainJar.exists() && mainJar.length() > 0L) return mainJar
        } catch (_: Exception) {}

        // Priority 3: Extract from android.jar.zip in assets
        try {
            assets.open("android.jar.zip").use { input ->
                java.util.zip.ZipInputStream(input).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name.endsWith("android.jar")) {
                            mainJar.outputStream().use { output -> zis.copyTo(output) }
                            break
                        }
                        entry = zis.nextEntry
                    }
                }
            }
            if (mainJar.exists() && mainJar.length() > 0L) return mainJar
        } catch (_: Exception) {}

        // Priority 4: External compiler engine directory
        val extPlatformJar = File("/storage/emulated/0/test-folder/compiler_engine/platforms/android-$targetSdk/android.jar")
        if (extPlatformJar.exists() && extPlatformJar.length() > 0L) return extPlatformJar

        val extJar = File("/storage/emulated/0/test-folder/compiler_engine/android.jar")
        if (extJar.exists() && extJar.length() > 0L) return extJar

        // Priority 5: Fallback to any available platform jar in assets
        for (sdk in listOf(34, 33, 32, 31, 30, 35, 36)) {
            val fallbackPJar = File(filesDir, "platforms/android-$sdk/android.jar")
            if (fallbackPJar.exists() && fallbackPJar.length() > 0L) return fallbackPJar
            try {
                fallbackPJar.parentFile?.mkdirs()
                assets.open("platforms/android-$sdk/android.jar").use { input ->
                    fallbackPJar.outputStream().use { output -> input.copyTo(output) }
                }
                if (fallbackPJar.exists() && fallbackPJar.length() > 0L) return fallbackPJar
            } catch (_: Exception) {}
        }

        return mainJar
    }

    fun getAndroidxJar(): File? {
        val f1 = File(filesDir, "libs/androidx-stubs.jar")
        if (f1.exists()) return f1
        val ext = File("/storage/emulated/0/test-folder/compiler_engine/androidx-stubs.jar")
        if (ext.exists()) return ext
        return null
    }

    fun getToolchainClassLoader(): ClassLoader {
        return toolchainClassLoaderInternal ?: classLoader
    }

    fun checkCompilerToolchainStatus(targetSdk: Int): Map<String, Boolean> {
        val status = mutableMapOf<String, Boolean>()
        val aapt2 = getAAPT2Binary()
        val androidJar = getAndroidJar(targetSdk)
        val keysDir = File(filesDir, "keys")
        val pk8 = File(keysDir, "testkey.pk8")
        val pem = File(keysDir, "testkey.x509.pem")
        val extDir = File("/storage/emulated/0/test-folder/compiler_engine")

        status["aapt2"] = aapt2.exists() && aapt2.canExecute()
        status["android.jar"] = androidJar.exists() && androidJar.length() > 0L
        status["testkey"] = (pk8.exists() && pem.exists()) || (File(extDir, "testkey.pk8").exists() && File(extDir, "testkey.x509.pem").exists())
        status["dexer"] = try {
            Class.forName("com.android.tools.r8.D8", false, getToolchainClassLoader())
            true
        } catch (e: Throwable) {
            File(extDir, "d8.jar").exists() || File(filesDir, "cp-android-v6.jar").exists()
        }
        status["compiler"] = try {
            Class.forName("org.eclipse.jdt.internal.compiler.batch.Main", false, getToolchainClassLoader())
            true
        } catch (e: Throwable) {
            File(extDir, "ecj.jar").exists() || File(extDir, "kotlinc-embeddable.jar").exists()
        }

        return status
    }

    companion object {
        @JvmStatic
        lateinit var instance: ApplicationLoader
            private set

        private var toolchainClassLoaderInternal: ClassLoader? = null

        @JvmStatic
        fun getContext(): Context? {
            return if (::instance.isInitialized) instance.applicationContext else null
        }

        @JvmStatic
        fun initEnvironment() {
            if (::instance.isInitialized) {
                instance.initCompilerEnvironment()
            }
        }
    }
}
