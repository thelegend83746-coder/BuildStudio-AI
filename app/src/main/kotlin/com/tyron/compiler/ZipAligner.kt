package com.tyron.compiler

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipAligner {

    @JvmStatic
    fun align(inputZip: File, outputZip: File) {
        outputZip.parentFile?.mkdirs()
        ZipInputStream(FileInputStream(inputZip)).use { zis ->
            ZipOutputStream(FileOutputStream(outputZip)).use { zos ->
                var entry: ZipEntry?
                while (zis.nextEntry.also { entry = it } != null) {
                    val e = entry!!
                    val newEntry = ZipEntry(e.name).apply {
                        time = e.time
                        comment = e.comment
                        extra = e.extra
                    }
                    if (e.method == ZipEntry.STORED) {
                        newEntry.method = ZipEntry.STORED
                        newEntry.size = e.size
                        newEntry.compressedSize = e.compressedSize
                        newEntry.crc = e.crc
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
        }
    }
}
