package com.apk.builder

import java.io.*
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object FileUtil {

    @JvmStatic
    fun readFile(path: String): String {
        val file = File(path)
        if (!file.exists()) return ""
        val sb = StringBuilder()
        try {
            BufferedReader(InputStreamReader(FileInputStream(file), StandardCharsets.UTF_8)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line).append("\n")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return sb.toString()
    }

    @JvmStatic
    fun writeFile(path: String, content: String): Boolean {
        return try {
            val file = File(path)
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { fos ->
                fos.write(content.toByteArray(StandardCharsets.UTF_8))
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @JvmStatic
    fun copyFile(source: File, dest: File) {
        dest.parentFile?.mkdirs()
        FileInputStream(source).use { fis ->
            FileOutputStream(dest).use { fos ->
                val buf = ByteArray(8192)
                var len: Int
                while (fis.read(buf).also { len = it } > 0) {
                    fos.write(buf, 0, len)
                }
            }
        }
    }

    @JvmStatic
    fun copyFile(sourcePath: String, destPath: String) {
        copyFile(File(sourcePath), File(destPath))
    }

    @JvmStatic
    fun copyAsset(inputStream: InputStream, dest: File) {
        dest.parentFile?.mkdirs()
        inputStream.use { input ->
            FileOutputStream(dest).use { output ->
                val buf = ByteArray(8192)
                var len: Int
                while (input.read(buf).also { len = it } > 0) {
                    output.write(buf, 0, len)
                }
            }
        }
    }

    @JvmStatic
    fun deleteDir(dir: File?): Boolean {
        if (dir != null && dir.isDirectory) {
            val children = dir.list()
            if (children != null) {
                for (child in children) {
                    val success = deleteDir(File(dir, child))
                    if (!success) return false
                }
            }
            return dir.delete()
        } else if (dir != null && dir.isFile) {
            return dir.delete()
        }
        return false
    }

    @JvmStatic
    fun unzip(zipStream: InputStream, targetDir: File) {
        targetDir.mkdirs()
        ZipInputStream(BufferedInputStream(zipStream)).use { zis ->
            var entry: ZipEntry?
            while (zis.nextEntry.also { entry = it } != null) {
                val e = entry!!
                val file = File(targetDir, e.name)
                if (e.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos ->
                        val buf = ByteArray(8192)
                        var len: Int
                        while (zis.read(buf).also { len = it } > 0) {
                            fos.write(buf, 0, len)
                        }
                    }
                }
                zis.closeEntry()
            }
        }
    }
}
