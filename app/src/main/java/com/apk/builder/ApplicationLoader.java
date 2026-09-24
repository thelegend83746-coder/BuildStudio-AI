package com.apk.builder;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import java.io.File;
import java.io.InputStream;

public class ApplicationLoader extends Application {

    private static ApplicationLoader instance;
    private static ClassLoader toolchainClassLoader;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        initCompilerEnvironment();
    }

    public static ApplicationLoader getInstance() {
        return instance;
    }

    public static Context getContext() {
        return instance != null ? instance.getApplicationContext() : null;
    }

    public static void initEnvironment() {
        if (instance != null) {
            instance.initCompilerEnvironment();
        }
    }

    private void initCompilerEnvironment() {
        new Thread(() -> {
            try {
                File filesDir = getFilesDir();

                // 1. Android platform jar
                File androidJar = new File(filesDir, "android.jar");
                if (!androidJar.exists() || androidJar.length() == 0) {
                    try (InputStream is = getAssets().open("android.jar.zip")) {
                        FileUtil.unzip(is, filesDir);
                    } catch (Exception e) {
                        try (InputStream is = getAssets().open("platforms/android-34/android.jar")) {
                            FileUtil.copyAsset(is, androidJar);
                        } catch (Exception ignored) {}
                    }
                }

                // 2. Signing keys
                File keysDir = new File(filesDir, "keys");
                if (!keysDir.exists()) keysDir.mkdirs();
                File pk8 = new File(keysDir, "testkey.pk8");
                File pem = new File(keysDir, "testkey.x509.pem");
                if (!pk8.exists() || pk8.length() == 0) {
                    try (InputStream is = getAssets().open("keys/testkey.pk8")) {
                        FileUtil.copyAsset(is, pk8);
                    } catch (Exception ignored) {}
                }
                if (!pem.exists() || pem.length() == 0) {
                    try (InputStream is = getAssets().open("keys/testkey.x509.pem")) {
                        FileUtil.copyAsset(is, pem);
                    } catch (Exception ignored) {}
                }

                // 3. Extract cp-android-v6.jar for offline fallback toolchain
                File cpDexJar = new File(filesDir, "cp-android-v6.jar");
                if (!cpDexJar.exists() || cpDexJar.length() == 0) {
                    try (InputStream is = getAssets().open("cp-android-v6.jar")) {
                        FileUtil.copyAsset(is, cpDexJar);
                    } catch (Exception ignored) {}
                }

                // 4. Verify AAPT2 binary
                File aapt2 = getAAPT2Binary();
                if (aapt2.exists()) {
                    aapt2.setExecutable(true, false);
                }

                // Pre-warm toolchain ClassLoader
                getToolchainClassLoader();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public synchronized ClassLoader getToolchainClassLoader() {
        if (toolchainClassLoader != null) {
            return toolchainClassLoader;
        }

        // Check if compiler classes are directly on the ART classpath
        try {
            Class.forName("org.eclipse.jdt.internal.compiler.batch.Main");
            Class.forName("com.android.tools.r8.D8");
            toolchainClassLoader = getClassLoader();
            return toolchainClassLoader;
        } catch (Throwable ignored) {}

        // Fallback: load pre-dexed toolchain archive cp-android-v6.jar
        try {
            File cpDexJar = new File(getFilesDir(), "cp-android-v6.jar");
            if (!cpDexJar.exists() || cpDexJar.length() == 0) {
                try (InputStream is = getAssets().open("cp-android-v6.jar")) {
                    FileUtil.copyAsset(is, cpDexJar);
                }
            }
            File optDir = getCodeCacheDir();
            toolchainClassLoader = new dalvik.system.DexClassLoader(
                    cpDexJar.getAbsolutePath(),
                    optDir.getAbsolutePath(),
                    null,
                    getClassLoader()
            );
            return toolchainClassLoader;
        } catch (Throwable t) {
            t.printStackTrace();
            toolchainClassLoader = getClassLoader();
            return toolchainClassLoader;
        }
    }

    public File getAAPT2Binary() {
        // Priority 1: nativeLibraryDir (standard installed apk lib)
        File nativeLib = new File(getApplicationInfo().nativeLibraryDir, "libaapt2.so");
        if (nativeLib.exists() && nativeLib.canExecute()) {
            return nativeLib;
        }

        // Priority 2: already extracted to filesDir
        File internalBinary = new File(getFilesDir(), "libaapt2.so");
        if (internalBinary.exists() && internalBinary.canExecute() && internalBinary.length() > 1000000) {
            return internalBinary;
        }

        // Priority 3: Extract from assets/toolchain/aapt2/ based on CPU ABI
        String[] supportedAbis = Build.SUPPORTED_ABIS;
        for (String abi : supportedAbis) {
            String assetPath = "toolchain/aapt2/" + abi;
            try (InputStream is = getAssets().open(assetPath)) {
                FileUtil.copyAsset(is, internalBinary);
                internalBinary.setExecutable(true, false);
                return internalBinary;
            } catch (Exception ignored) {}
        }

        // Priority 4: Try generic asset libaapt2.so
        try (InputStream is = getAssets().open("libaapt2.so")) {
            FileUtil.copyAsset(is, internalBinary);
            internalBinary.setExecutable(true, false);
            return internalBinary;
        } catch (Exception ignored) {}

        if (nativeLib.exists()) {
            nativeLib.setExecutable(true, false);
            return nativeLib;
        }
        return internalBinary;
    }

    public File getAndroidJar() {
        return getAndroidJar(34);
    }

    public File getAndroidJar(int apiLevel) {
        int resolvedApi = Math.max(30, Math.min(34, apiLevel));
        File platformJar = new File(getFilesDir(), "platforms/android-" + resolvedApi + "/android.jar");
        if (platformJar.exists() && platformJar.length() > 10000000) {
            return platformJar;
        }

        try (InputStream is = getAssets().open("platforms/android-" + resolvedApi + "/android.jar")) {
            boolean ok = FileUtil.copyAsset(is, platformJar);
            if (ok && platformJar.exists() && platformJar.length() > 10000000) {
                return platformJar;
            }
        } catch (Exception ignored) {}

        File rootJar = new File(getFilesDir(), "android.jar");
        if (rootJar.exists() && rootJar.length() > 10000000) return rootJar;

        try (InputStream is = getAssets().open("android.jar.zip")) {
            FileUtil.unzip(is, getFilesDir());
            if (rootJar.exists() && rootJar.length() > 10000000) return rootJar;
        } catch (Exception ignored) {}

        try (InputStream is = getAssets().open("platforms/android-34/android.jar")) {
            FileUtil.copyAsset(is, rootJar);
            if (rootJar.exists() && rootJar.length() > 10000000) return rootJar;
        } catch (Exception ignored) {}

        return rootJar;
    }

    public File getKeyPk8() {
        File pk8 = new File(getFilesDir(), "keys/testkey.pk8");
        if (!pk8.exists() || pk8.length() == 0) {
            try (InputStream is = getAssets().open("keys/testkey.pk8")) {
                FileUtil.copyAsset(is, pk8);
            } catch (Exception ignored) {}
        }
        return pk8;
    }

    public File getKeyPem() {
        File pem = new File(getFilesDir(), "keys/testkey.x509.pem");
        if (!pem.exists() || pem.length() == 0) {
            try (InputStream is = getAssets().open("keys/testkey.x509.pem")) {
                FileUtil.copyAsset(is, pem);
            } catch (Exception ignored) {}
        }
        return pem;
    }

    public File getAndroidxJar() {
        File jar = new File(getFilesDir(), "libs/androidx-stubs.jar");
        if (jar.exists() && jar.length() > 0) return jar;
        try (InputStream is = getAssets().open("libs/androidx-stubs.jar")) {
            FileUtil.copyAsset(is, jar);
            return jar;
        } catch (Exception ignored) {}
        return null;
    }
}
