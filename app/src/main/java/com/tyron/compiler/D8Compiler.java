package com.tyron.compiler;

import com.apk.builder.ApplicationLoader;
import com.apk.builder.logger.Logger;
import com.apk.builder.model.Project;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class D8Compiler extends Compiler {

    public D8Compiler(Project project) {
        super(project);
    }

    @Override
    public void run() throws Exception {
        boolean useDx = "Dx".equalsIgnoreCase(project.getDexer());
        String toolName = useDx ? "Dx" : "D8";

        notifyProgress("[" + toolName + "] Dexing .class bytecode and external libraries into classes.dex...", 3, 7);

        File androidJar = ApplicationLoader.getInstance().getAndroidJar(project.getTargetSdk());
        File classesDir = new File(project.getBuildDir(), "bin/classes");
        File binDir = project.getBinDir();
        binDir.mkdirs();

        List<File> classFiles = new ArrayList<>();
        collectClassFiles(classesDir, classFiles);

        if (classFiles.isEmpty()) {
            throw new Exception("No .class files found in " + classesDir.getAbsolutePath() + " for DEX compilation.");
        }

        File androidxJar = ApplicationLoader.getInstance().getAndroidxJar();
        if (androidxJar != null && androidxJar.exists()) {
            classFiles.add(androidxJar);
        }

        Logger.log("[" + toolName + "] Converting " + classFiles.size() + " bytecode files to DEX format");
        ClassLoader cl = ApplicationLoader.getInstance().getToolchainClassLoader();

        if (useDx) {
            // Dx mode
            File dexFile = new File(binDir, "classes.dex");
            List<String> args = new ArrayList<>();
            args.add("--dex");
            args.add("--verbose");
            args.add("--output=" + dexFile.getAbsolutePath());
            args.add(classesDir.getAbsolutePath());

            try {
                Class<?> dxClass = Class.forName("com.android.dx.command.Main", true, cl);
                Method mainMethod = dxClass.getMethod("main", String[].class);
                mainMethod.invoke(null, (Object) args.toArray(new String[0]));
            } catch (ClassNotFoundException e) {
                // Fallback to D8 if Dx not found
                runD8(cl, androidJar, binDir, classFiles);
            }
        } else {
            // D8 mode
            runD8(cl, androidJar, binDir, classFiles);
        }

        File dexFile = new File(binDir, "classes.dex");
        if (!dexFile.exists() || dexFile.length() == 0) {
            throw new Exception(toolName + " finished without producing a valid classes.dex file!");
        }

        Logger.log("[" + toolName + "] classes.dex successfully generated (" + dexFile.length() + " bytes)");
    }

    private void runD8(ClassLoader cl, File androidJar, File binDir, List<File> classFiles) throws Exception {
        List<String> args = new ArrayList<>();
        args.add("--min-api");
        args.add(String.valueOf(project.getMinSdk()));
        args.add("--lib");
        args.add(androidJar.getAbsolutePath());
        args.add("--output");
        args.add(binDir.getAbsolutePath());
        args.add("--release");

        for (File f : classFiles) {
            args.add(f.getAbsolutePath());
        }

        try {
            Class<?> d8Class = Class.forName("com.android.tools.r8.D8", true, cl);
            Method mainMethod = d8Class.getMethod("main", String[].class);
            mainMethod.invoke(null, (Object) args.toArray(new String[0]));
        } catch (ClassNotFoundException e) {
            throw new Exception("D8 DEX compiler not found on classpath: " + e.getMessage());
        } catch (Exception e) {
            throw new Exception("D8 execution error: " + e.getMessage(), e);
        }
    }

    private void collectClassFiles(File dir, List<File> list) {
        if (!dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectClassFiles(f, list);
            } else if (f.getName().endsWith(".class")) {
                list.add(f);
            }
        }
    }
}
