package com.tyron.compiler;

import com.apk.builder.logger.Logger;
import java.io.*;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ZipAligner {

    private static final int DEFAULT_ALIGNMENT = 4;
    private static final int PAGE_ALIGNMENT = 4096;

    public static void align(File inputZip, File outputZip) throws IOException {
        Logger.log("[ZipAlign] Performing 4-byte boundary alignment...");

        if (outputZip.exists()) {
            outputZip.delete();
        }

        try (ZipFile zipFile = new ZipFile(inputZip);
             FileOutputStream fos = new FileOutputStream(outputZip);
             BufferedOutputStream bos = new BufferedOutputStream(fos);
             ZipOutputStream zos = new ZipOutputStream(bos)) {

            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            byte[] buffer = new byte[8192];

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                ZipEntry newEntry = new ZipEntry(entry.getName());
                newEntry.setMethod(entry.getMethod());
                newEntry.setTime(entry.getTime());

                if (entry.getMethod() == ZipEntry.STORED) {
                    newEntry.setSize(entry.getSize());
                    newEntry.setCompressedSize(entry.getCompressedSize());
                    newEntry.setCrc(entry.getCrc());

                    int alignment = entry.getName().endsWith(".so") ? PAGE_ALIGNMENT : DEFAULT_ALIGNMENT;
                    // Calculate header padding for alignment
                    byte[] extra = entry.getExtra();
                    int extraLen = (extra != null) ? extra.length : 0;
                    int headerLen = 30 + entry.getName().getBytes("UTF-8").length + extraLen;
                    int padding = (alignment - (headerLen % alignment)) % alignment;

                    if (padding > 0) {
                        byte[] paddedExtra = new byte[extraLen + padding];
                        if (extra != null) {
                            System.arraycopy(extra, 0, paddedExtra, 0, extraLen);
                        }
                        newEntry.setExtra(paddedExtra);
                    } else if (extra != null) {
                        newEntry.setExtra(extra);
                    }
                } else if (entry.getExtra() != null) {
                    newEntry.setExtra(entry.getExtra());
                }

                zos.putNextEntry(newEntry);
                try (InputStream is = zipFile.getInputStream(entry)) {
                    int len;
                    while ((len = is.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                }
                zos.closeEntry();
            }
            zos.flush();
        }
        Logger.log("[ZipAlign] 4-byte boundary alignment completed successfully.");
    }
}
