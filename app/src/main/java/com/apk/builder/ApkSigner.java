package com.apk.builder;

import com.apk.builder.logger.Logger;
import com.tyron.compiler.ZipAligner;
import com.tyron.compiler.ZipSignerWrapper;
import java.io.File;

public class ApkSigner {

    public static File signAndAlign(File unalignedApk, File alignedApk, File signedApk) throws Exception {
        Logger.log("[ZipAlign] Performing 4-byte boundary alignment...");
        ZipAligner.align(unalignedApk, alignedApk);

        Logger.log("[APK Signer] Signing APK using embedded debug key (v1 + v2 + v3 scheme)...");
        ZipSignerWrapper.signApk(alignedApk, signedApk);

        Logger.log("[APK Signer] Successfully signed APK: " + signedApk.getAbsolutePath());
        return signedApk;
    }
}
