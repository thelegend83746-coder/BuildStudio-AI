package com.apk.builder;

import android.content.Context;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class LibraryManager {

    private final Context context;

    public LibraryManager(Context context) {
        this.context = context;
    }

    public List<File> getLibraries(boolean useAppCompat, boolean useMaterial) {
        List<File> libs = new ArrayList<>();
        File filesDir = context.getFilesDir();

        if (useAppCompat) {
            File appcompatJar = new File(filesDir, "libs/appcompat.jar");
            if (appcompatJar.exists()) libs.add(appcompatJar);
        }
        if (useMaterial) {
            File materialJar = new File(filesDir, "libs/material.jar");
            if (materialJar.exists()) libs.add(materialJar);
        }

        File defaultCp = new File(filesDir, "cp-android-v6.jar");
        if (defaultCp.exists()) libs.add(defaultCp);

        return libs;
    }

    public List<String> getLibraryClasspaths(boolean useAppCompat, boolean useMaterial) {
        List<String> paths = new ArrayList<>();
        for (File f : getLibraries(useAppCompat, useMaterial)) {
            paths.add(f.getAbsolutePath());
        }
        return paths;
    }
}
