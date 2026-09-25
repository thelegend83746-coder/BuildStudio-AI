package com.tyron.compiler

import com.apk.builder.ApplicationLoader
import com.apk.builder.FileUtil
import com.apk.builder.logger.Logger
import com.apk.builder.model.Project
import java.io.*
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipSignerWrapper {

    fun interface SignCallback {
        fun onProgress(message: String, step: Int, total: Int)
    }

    @JvmStatic
    fun packageAndSign(project: Project, callback: SignCallback?): File {
        val binDir = project.binDir
        val resourcesAp = File(binDir, "resources.ap_")
        val dexFile = File(binDir, "classes.dex")

        val unalignedApk = File(binDir, "${project.name}-unaligned.apk")
        val alignedApk = File(binDir, "${project.name}-aligned.apk")
        val finalSignedApk = File(binDir, "${project.name}-signed.apk")

        if (!resourcesAp.exists()) {
            throw FileNotFoundException("resources.ap_ not found in ${binDir.absolutePath}")
        }
        if (!dexFile.exists()) {
            throw FileNotFoundException("classes.dex not found in ${binDir.absolutePath}")
        }

        callback?.onProgress("[APK Builder] Packaging classes.dex and resources into APK archive...", 4, 7)
        Logger.log("[Packager] Combining resources.ap_ and classes.dex into ${unalignedApk.name}")

        packageApk(resourcesAp, dexFile, unalignedApk)

        callback?.onProgress("[APK Builder] Aligning zip boundaries (ZipAlign)...", 5, 7)
        Logger.log("[ZipAlign] 4-byte boundary optimization")
        ZipAligner.align(unalignedApk, alignedApk)

        callback?.onProgress("[APK Builder] Signing APK with test debug keystore...", 6, 7)
        signApk(alignedApk, finalSignedApk)

        return finalSignedApk
    }

    private fun packageApk(resourcesAp: File, dexFile: File, outputApk: File) {
        outputApk.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(outputApk)).use { zos ->
            // Copy all entries from resources.ap_
            ZipInputStream(FileInputStream(resourcesAp)).use { zis ->
                var entry: ZipEntry?
                while (zis.nextEntry.also { entry = it } != null) {
                    val e = entry!!
                    val newEntry = ZipEntry(e.name).apply {
                        time = e.time
                    }
                    zos.putNextEntry(newEntry)
                    val buffer = ByteArray(8192)
                    var len: Int
                    while (zis.read(buffer).also { len = it } > 0) {
                        zos.write(buffer, 0, len)
                    }
                    zos.closeEntry()
                    zis.closeEntry()
                }
            }

            // Append classes.dex
            val dexEntry = ZipEntry("classes.dex").apply {
                time = System.currentTimeMillis()
            }
            zos.putNextEntry(dexEntry)
            FileInputStream(dexFile).use { fis ->
                val buffer = ByteArray(8192)
                var len: Int
                while (fis.read(buffer).also { len = it } > 0) {
                    zos.write(buffer, 0, len)
                }
            }
            zos.closeEntry()
        }
    }

    @JvmStatic
    fun signApk(inputApk: File, outputApk: File) {
        var key: PrivateKey? = null
        var cert: X509Certificate? = null

        try {
            val filesDir = ApplicationLoader.instance.filesDir
            val extDir = File("/storage/emulated/0/test-folder/compiler_engine")

            var debugKey = File(filesDir, "keys/testkey.pk8")
            if (!debugKey.exists()) debugKey = File(extDir, "testkey.pk8")

            var debugCert = File(filesDir, "keys/testkey.x509.pem")
            if (!debugCert.exists()) debugCert = File(extDir, "testkey.x509.pem")

            if (debugKey.exists() && debugCert.exists()) {
                key = loadPrivateKey(debugKey)
                cert = loadCertificate(debugCert)
            }
        } catch (ignored: Throwable) {}

        if (key != null && cert != null) {
            Logger.log("[ApkSigner] Signing APK with testkey.pk8 & testkey.x509.pem")
            signWithApkSigner(inputApk, outputApk, key, cert)
        } else {
            Logger.log("[ApkSigner] Notice: testkey not found, copying aligned APK directly")
            FileUtil.copyFile(inputApk, outputApk)
        }
    }

    private fun loadPrivateKey(pk8File: File): PrivateKey {
        val bytes = ByteArray(pk8File.length().toInt())
        FileInputStream(pk8File).use { it.read(bytes) }
        val spec = PKCS8EncodedKeySpec(bytes)
        return KeyFactory.getInstance("RSA").generatePrivate(spec)
    }

    private fun loadCertificate(certFile: File): X509Certificate {
        FileInputStream(certFile).use { fis ->
            return CertificateFactory.getInstance("X.509").generateCertificate(fis) as X509Certificate
        }
    }

    private fun signWithApkSigner(inputApk: File, outputApk: File, key: PrivateKey, cert: X509Certificate) {
        try {
            val cl = ApplicationLoader.instance.getToolchainClassLoader()
            val signerBuilderClass = Class.forName("com.android.apksig.ApkSigner\$Builder", true, cl)
            val signerClass = Class.forName("com.android.apksig.ApkSigner", true, cl)

            val signerConfigBuilderClass = Class.forName("com.android.apksig.ApkSigner\$SignerConfig\$Builder", true, cl)
            val configBuilder = signerConfigBuilderClass.getConstructor(String::class.java, PrivateKey::class.java, List::class.java)
                .newInstance("CERT", key, listOf(cert))
            val config = signerConfigBuilderClass.getMethod("build").invoke(configBuilder)

            val builder = signerBuilderClass.getConstructor(List::class.java).newInstance(listOf(config))
            signerBuilderClass.getMethod("setInputApk", File::class.java).invoke(builder, inputApk)
            signerBuilderClass.getMethod("setOutputApk", File::class.java).invoke(builder, outputApk)
            signerBuilderClass.getMethod("setMinSdkVersion", Int::class.javaPrimitiveType).invoke(builder, 21)

            val signer = signerBuilderClass.getMethod("build").invoke(builder)
            signerClass.getMethod("sign").invoke(signer)
            Logger.log("[ApkSigner] APK signed successfully via ApkSigner API")
        } catch (e: Exception) {
            Logger.log("[ApkSigner] Reflection signer fallback: ${e.message}")
            FileUtil.copyFile(inputApk, outputApk)
        }
    }
}
