package com.apk.builder

import android.content.Context
import java.io.File

class LibraryManager(private val context: Context) {

    fun getLibraries(useAppCompat: Boolean, useMaterial: Boolean): List<File> {
        val libs = mutableListOf<File>()
        val filesDir = context.filesDir

        if (useAppCompat) {
            val appcompatJar = File(filesDir, "libs/appcompat.jar")
            if (appcompatJar.exists()) libs.add(appcompatJar)
        }
        if (useMaterial) {
            val materialJar = File(filesDir, "libs/material.jar")
            if (materialJar.exists()) libs.add(materialJar)
        }

        val defaultCp = File(filesDir, "cp-android-v6.jar")
        if (defaultCp.exists()) libs.add(defaultCp)

        return libs
    }

    fun getLibraryClasspaths(useAppCompat: Boolean, useMaterial: Boolean): List<String> {
        return getLibraries(useAppCompat, useMaterial).map { it.absolutePath }
    }
}
