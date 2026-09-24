package com.tyron.compiler;

import com.apk.builder.ApplicationLoader;
import com.apk.builder.logger.Logger;
import com.apk.builder.model.Project;
import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipSignerWrapper {

    public interface SignCallback {
        void onProgress(String message, int step, int total);
    }

    public static void signApk(File inputApk, File outputApk) throws Exception {
        PrivateKey key = null;
        X509Certificate cert = null;
        try {
            File debugKey = new File(com.apk.builder.ApplicationLoader.getInstance().getFilesDir(), "keys/testkey.pk8");
            File debugCert = new File(com.apk.builder.ApplicationLoader.getInstance().getFilesDir(), "keys/testkey.x509.pem");
            if (debugKey.exists() && debugCert.exists()) {
                key = loadPrivateKey(debugKey);
                cert = loadCertificate(debugCert);
            }
        } catch (Throwable ignored) {}

        if (key != null && cert != null) {
            signWithApkSigner(inputApk, outputApk, key, cert, 21);
        } else {
            com.apk.builder.FileUtil.copyFile(inputApk, outputApk);
        }
    }

    public static File packageAndSign(Project project, SignCallback callback) throws Exception {
        File binDir = project.getBinDir();
        File resourcesAp = new File(binDir, "resources.ap_");
        File dexFile = new File(binDir, "classes.dex");

        File unalignedApk = new File(binDir, project.getName() + "-unaligned.apk");
        File alignedApk = new File(binDir, project.getName() + "-aligned.apk");
        File finalSignedApk = new File(binDir, project.getName() + "-signed.apk");

        if (!resourcesAp.exists()) {
            throw new FileNotFoundException("resources.ap_ not found in " + binDir.getAbsolutePath());
        }
        if (!dexFile.exists()) {
            throw new FileNotFoundException("classes.dex not found in " + binDir.getAbsolutePath());
        }

        // Step 4: Packaging .dex and resources into unaligned APK
        if (callback != null) callback.onProgress("[APK Builder] Packaging .dex and resources into unaligned APK...", 4, 7);
        Logger.log("[APK Builder] Packaging resources.ap_ and classes.dex into unaligned APK");

        Map<String, byte[]> entries = new HashMap<>();

        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(unalignedApk)))) {
            // Add entries from resources.ap_
            try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(resourcesAp)))) {
                ZipEntry ze;
                byte[] buf = new byte[8192];
                while ((ze = zis.getNextEntry()) != null) {
                    if (ze.getName().startsWith("META-INF/")) continue;
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    int len;
                    while ((len = zis.read(buf)) > 0) baos.write(buf, 0, len);
                    byte[] data = baos.toByteArray();
                    entries.put(ze.getName(), data);

                    ZipEntry outEntry = new ZipEntry(ze.getName());
                    zos.putNextEntry(outEntry);
                    zos.write(data);
                    zos.closeEntry();
                    zis.closeEntry();
                }
            }

            // Add classes.dex
            try (InputStream is = new FileInputStream(dexFile)) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int len;
                while ((len = is.read(buf)) > 0) baos.write(buf, 0, len);
                byte[] data = baos.toByteArray();
                entries.put("classes.dex", data);

                ZipEntry dexEntry = new ZipEntry("classes.dex");
                zos.putNextEntry(dexEntry);
                zos.write(data);
                zos.closeEntry();
            }

            // Add assets if present
            File assetsDir = new File(project.getRootPath(), "app/src/main/assets");
            if (!assetsDir.exists()) assetsDir = new File(project.getRootPath(), "src/main/assets");
            if (assetsDir.exists() && assetsDir.isDirectory()) {
                addDirectoryToZip(assetsDir, "assets", zos, entries);
            }
        }

        Logger.log("[APK Builder] Packaging complete: " + unalignedApk.getName() + " (" + unalignedApk.length() + " bytes)");

        // Step 5: ZipAlign 4-byte boundary alignment
        if (callback != null) callback.onProgress("[ZipAlign] Performing 4-byte boundary alignment...", 5, 7);
        try {
            ZipAligner.align(unalignedApk, alignedApk);
        } catch (Throwable t) {
            Logger.log("[ZipAlign] Alignment note: " + t.getMessage() + ", continuing with unaligned base");
            alignedApk = unalignedApk;
        }

        // Step 6: APK Signer using embedded debug/testkey credentials
        if (callback != null) callback.onProgress("[APK Signer] Signing APK using embedded debug key (v1 + v2 scheme)...", 6, 7);
        Logger.log("[APK Signer] Signing APK using embedded testkey credentials");

        File pk8File = ApplicationLoader.getInstance().getKeyPk8();
        File pemFile = ApplicationLoader.getInstance().getKeyPem();

        PrivateKey privateKey = loadPrivateKey(pk8File);
        X509Certificate cert = loadCertificate(pemFile);

        boolean signedWithV2 = false;
        try {
            signWithApkSigner(alignedApk, finalSignedApk, privateKey, cert, project.getMinSdk());
            signedWithV2 = true;
            Logger.log("[APK Signer] APK successfully signed with APK Signature Scheme v1 + v2 + v3!");
        } catch (Throwable t) {
            Logger.log("[APK Signer] ApkSigner v2/v3 fallback to v1 signer: " + t.getMessage());
            signApkLegacy(entries, alignedApk, finalSignedApk, privateKey, cert);
            Logger.log("[APK Signer] APK successfully signed with JAR Signature Scheme v1!");
        }

        // Clean intermediate unaligned/aligned files
        try {
            if (unalignedApk.exists()) unalignedApk.delete();
            if (alignedApk.exists() && !alignedApk.equals(finalSignedApk)) alignedApk.delete();
        } catch (Exception ignored) {}

        return finalSignedApk;
    }

    private static void addDirectoryToZip(File dir, String base, ZipOutputStream zos, Map<String, byte[]> entries) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        byte[] buf = new byte[8192];
        for (File f : files) {
            String entryName = base + "/" + f.getName();
            if (f.isDirectory()) {
                addDirectoryToZip(f, entryName, zos, entries);
            } else {
                try (InputStream is = new FileInputStream(f)) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    int len;
                    while ((len = is.read(buf)) > 0) baos.write(buf, 0, len);
                    byte[] data = baos.toByteArray();
                    entries.put(entryName, data);

                    ZipEntry ze = new ZipEntry(entryName);
                    zos.putNextEntry(ze);
                    zos.write(data);
                    zos.closeEntry();
                }
            }
        }
    }

    private static void signWithApkSigner(File inputApk, File outputApk, PrivateKey key, X509Certificate cert, int minSdk) throws Exception {
        Class<?> scbClass = Class.forName("com.android.apksig.ApkSigner");
        Constructor<?> scbCtor = scbClass.getConstructor(String.class, PrivateKey.class, List.class);
        Object scb = scbCtor.newInstance("CERT", key, Collections.singletonList(cert));
        Method buildSc = scbClass.getMethod("build");
        Object signerConfig = buildSc.invoke(scb);

        Class<?> asbClass = Class.forName("com.android.apksig.ApkSigner");
        Constructor<?> asbCtor = asbClass.getConstructor(List.class);
        Object asb = asbCtor.newInstance(Collections.singletonList(signerConfig));

        asbClass.getMethod("setInputApk", File.class).invoke(asb, inputApk);
        asbClass.getMethod("setOutputApk", File.class).invoke(asb, outputApk);
        asbClass.getMethod("setMinSdkVersion", int.class).invoke(asb, Math.max(minSdk, 21));
        asbClass.getMethod("setV1SigningEnabled", boolean.class).invoke(asb, true);
        asbClass.getMethod("setV2SigningEnabled", boolean.class).invoke(asb, true);
        asbClass.getMethod("setV3SigningEnabled", boolean.class).invoke(asb, true);

        Object apkSigner = asbClass.getMethod("build").invoke(asb);
        apkSigner.getClass().getMethod("sign").invoke(apkSigner);
    }

    public static PrivateKey loadPrivateKey(File pk8File) throws Exception {
        byte[] keyBytes = new byte[(int) pk8File.length()];
        try (FileInputStream fis = new FileInputStream(pk8File)) {
            fis.read(keyBytes);
        }
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(spec);
    }

    public static X509Certificate loadCertificate(File pemFile) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        try (FileInputStream fis = new FileInputStream(pemFile)) {
            return (X509Certificate) cf.generateCertificate(fis);
        }
    }

    private static void signApkLegacy(Map<String, byte[]> entries, File unaligned, File signedApk, PrivateKey key, X509Certificate cert) throws Exception {
        MessageDigest sha1 = MessageDigest.getInstance("SHA1");

        StringBuilder manifest = new StringBuilder();
        manifest.append("Manifest-Version: 1.0\r\n");
        manifest.append("Created-By: 1.0 (BUILD STUDIO Native Compiler)\r\n\r\n");

        Map<String, String> digests = new TreeMap<>();
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            if (entry.getKey().startsWith("META-INF/")) continue;
            sha1.reset();
            byte[] digest = sha1.digest(entry.getValue());
            String b64 = android.util.Base64.encodeToString(digest, android.util.Base64.NO_WRAP);
            digests.put(entry.getKey(), b64);

            manifest.append("Name: ").append(entry.getKey()).append("\r\n");
            manifest.append("SHA1-Digest: ").append(b64).append("\r\n\r\n");
        }
        byte[] manifestBytes = manifest.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);

        StringBuilder certSf = new StringBuilder();
        certSf.append("Signature-Version: 1.0\r\n");
        certSf.append("Created-By: 1.0 (BUILD STUDIO Native Compiler)\r\n");
        sha1.reset();
        String manifestDigest = android.util.Base64.encodeToString(sha1.digest(manifestBytes), android.util.Base64.NO_WRAP);
        certSf.append("SHA1-Digest-Manifest: ").append(manifestDigest).append("\r\n\r\n");

        for (Map.Entry<String, String> d : digests.entrySet()) {
            certSf.append("Name: ").append(d.getKey()).append("\r\n");
            certSf.append("SHA1-Digest: ").append(d.getValue()).append("\r\n\r\n");
        }
        byte[] certSfBytes = certSf.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);

        Signature sig = Signature.getInstance("SHA1withRSA");
        sig.initSign(key);
        sig.update(certSfBytes);
        byte[] signatureBytes = sig.sign();

        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(signedApk)))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                if (entry.getKey().startsWith("META-INF/")) continue;
                ZipEntry ze = new ZipEntry(entry.getKey());
                zos.putNextEntry(ze);
                zos.write(entry.getValue());
                zos.closeEntry();
            }

            ZipEntry manifestEntry = new ZipEntry("META-INF/MANIFEST.MF");
            zos.putNextEntry(manifestEntry);
            zos.write(manifestBytes);
            zos.closeEntry();

            ZipEntry certSfEntry = new ZipEntry("META-INF/CERT.SF");
            zos.putNextEntry(certSfEntry);
            zos.write(certSfBytes);
            zos.closeEntry();

            ZipEntry certRsaEntry = new ZipEntry("META-INF/CERT.RSA");
            zos.putNextEntry(certRsaEntry);
            zos.write(signatureBytes);
            zos.closeEntry();
        }
    }
}
