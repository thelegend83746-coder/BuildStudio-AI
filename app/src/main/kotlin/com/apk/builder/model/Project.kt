package com.apk.builder.model

import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

data class Project(
    var name: String,
    var packageName: String,
    var rootPath: String
) {
    var minSdk: Int = 26
    var targetSdk: Int = 34
    var versionCode: Int = 1
    var versionName: String = "1.0"
    var javaVersion: String = "1.8"
    var language: String = "Kotlin" // "Kotlin" or "Java"
    var dexer: String = "D8"
    var stringFog: Boolean = false
    var r8Shrink: Boolean = false
    var useAppCompat: Boolean = true
    var useMaterial: Boolean = true
    var iconPath: String? = null
    var lastModified: Long = System.currentTimeMillis()

    init {
        loadConfig()
    }

    val srcDir: File
        get() {
            val ktDir = File(rootPath, "app/src/main/kotlin")
            if (ktDir.exists()) return ktDir
            val javaDir = File(rootPath, "app/src/main/java")
            if (javaDir.exists()) return javaDir
            val srcMain = File(rootPath, "src/main/java")
            if (srcMain.exists()) return srcMain
            return if (language.equals("Kotlin", ignoreCase = true)) ktDir else javaDir
        }

    val resDir: File
        get() {
            val r1 = File(rootPath, "app/src/main/res")
            if (r1.exists()) return r1
            val r2 = File(rootPath, "src/main/res")
            if (r2.exists()) return r2
            return r1
        }

    val manifestFile: File
        get() {
            val f1 = File(rootPath, "app/src/main/AndroidManifest.xml")
            if (f1.exists()) return f1
            val f2 = File(rootPath, "src/main/AndroidManifest.xml")
            if (f2.exists()) return f2
            return f1
        }

    val buildDir: File
        get() {
            val f1 = File(rootPath, "app/build")
            if (f1.exists() || File(rootPath, "app").exists()) return f1
            return File(rootPath, "build")
        }

    val binDir: File
        get() {
            val outDir = File(rootPath, "output/bin")
            if (outDir.exists()) return outDir
            val appBin = File(buildDir, "bin")
            if (appBin.exists()) return appBin
            outDir.mkdirs()
            return outDir
        }

    fun saveConfig() {
        try {
            val configFile = File(rootPath, "project.json")
            val json = JSONObject().apply {
                put("name", name)
                put("packageName", packageName)
                put("minSdk", minSdk)
                put("targetSdk", targetSdk)
                put("versionCode", versionCode)
                put("versionName", versionName)
                put("javaVersion", javaVersion)
                put("language", language)
                put("dexer", dexer)
                put("stringFog", stringFog)
                put("r8Shrink", r8Shrink)
                put("useAppCompat", useAppCompat)
                put("useMaterial", useMaterial)
                iconPath?.let { put("iconPath", it) }
                put("lastModified", System.currentTimeMillis())
            }
            FileOutputStream(configFile).use { fos ->
                fos.write(json.toString(2).toByteArray(StandardCharsets.UTF_8))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadConfig() {
        try {
            val configFile = File(rootPath, "project.json")
            if (configFile.exists()) {
                val bytes = ByteArray(configFile.length().toInt())
                FileInputStream(configFile).use { fis ->
                    fis.read(bytes)
                }
                val json = JSONObject(String(bytes, StandardCharsets.UTF_8))
                if (json.has("name")) name = json.getString("name")
                if (json.has("packageName")) packageName = json.getString("packageName")
                if (json.has("minSdk")) minSdk = json.getInt("minSdk")
                if (json.has("targetSdk")) targetSdk = json.getInt("targetSdk")
                if (json.has("versionCode")) versionCode = json.getInt("versionCode")
                if (json.has("versionName")) versionName = json.getString("versionName")
                if (json.has("javaVersion")) javaVersion = json.getString("javaVersion")
                if (json.has("language")) language = json.getString("language")
                if (json.has("dexer")) dexer = json.getString("dexer")
                if (json.has("stringFog")) stringFog = json.getBoolean("stringFog")
                if (json.has("r8Shrink")) r8Shrink = json.getBoolean("r8Shrink")
                if (json.has("useAppCompat")) useAppCompat = json.getBoolean("useAppCompat")
                if (json.has("useMaterial")) useMaterial = json.getBoolean("useMaterial")
                if (json.has("iconPath")) iconPath = json.getString("iconPath")
                if (json.has("lastModified")) lastModified = json.getLong("lastModified")
            } else {
                // Auto-detect from project files if imported without project.json
                val manifest = manifestFile
                if (manifest.exists()) {
                    val mText = manifest.readText()
                    val pkgMatch = Regex("""package\s*=\s*"([^"]+)"""").find(mText)
                    if (pkgMatch != null) packageName = pkgMatch.groupValues[1]
                    val minMatch = Regex("""android:minSdkVersion\s*=\s*"(\d+)"""").find(mText)
                    if (minMatch != null) minSdk = minMatch.groupValues[1].toIntOrNull() ?: 26
                    val targetMatch = Regex("""android:targetSdkVersion\s*=\s*"(\d+)"""").find(mText)
                    if (targetMatch != null) targetSdk = targetMatch.groupValues[1].toIntOrNull() ?: 34
                    val vCodeMatch = Regex("""android:versionCode\s*=\s*"(\d+)"""").find(mText)
                    if (vCodeMatch != null) versionCode = vCodeMatch.groupValues[1].toIntOrNull() ?: 1
                    val vNameMatch = Regex("""android:versionName\s*=\s*"([^"]+)"""").find(mText)
                    if (vNameMatch != null) versionName = vNameMatch.groupValues[1]
                }
                val stringsXml = File(resDir, "values/strings.xml")
                if (stringsXml.exists()) {
                    val sText = stringsXml.readText()
                    val appMatch = Regex("""<string\s+name\s*=\s*"app_name"[^>]*>(.*?)</string>""").find(sText)
                    if (appMatch != null && appMatch.groupValues[1].isNotBlank()) {
                        name = appMatch.groupValues[1].trim()
                    }
                }
                val iconCandidates = listOf(
                    File(resDir, "drawable-xhdpi/app_icon.png"),
                    File(resDir, "drawable/app_icon.png"),
                    File(resDir, "mipmap-xhdpi/ic_launcher.png"),
                    File(resDir, "mipmap/ic_launcher.png"),
                    File(rootPath, "icon.png")
                )
                iconCandidates.firstOrNull { it.exists() }?.let { iconPath = it.absolutePath }
                saveConfig()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        @JvmStatic
        fun loadFromDirectory(dir: File?): Project? {
            if (dir == null || !dir.isDirectory) return null
            val safeName = dir.name.lowercase().replace(Regex("[^a-z0-9_]"), "")
            val p = Project(dir.name, "com.example.$safeName", dir.absolutePath)
            p.loadConfig()
            p.lastModified = dir.lastModified()
            return p
        }
    }
}
