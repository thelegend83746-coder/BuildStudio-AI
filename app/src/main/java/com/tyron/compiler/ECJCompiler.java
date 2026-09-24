package com.tyron.compiler;

import com.apk.builder.ApplicationLoader;
import com.apk.builder.logger.Logger;
import com.apk.builder.model.Project;
import java.io.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class ECJCompiler extends Compiler {

    public ECJCompiler(Project project) {
        super(project);
    }

    @Override
    public void run() throws Exception {
        notifyProgress("[ECJ] Compiling Java source files (.java) against android.jar into bytecode (.class)...", 2, 7);

        File androidJar = ApplicationLoader.getInstance().getAndroidJar(project.getTargetSdk());
        File genDir = new File(project.getBuildDir(), "gen");
        File srcDir = project.getSrcDir();
        File classesDir = new File(project.getBuildDir(), "bin/classes");
        classesDir.mkdirs();

        List<File> javaFiles = new ArrayList<>();
        collectJavaFiles(srcDir, javaFiles);
        collectJavaFiles(genDir, javaFiles);

        if (javaFiles.isEmpty()) {
            throw new Exception("No Java source files found to compile in " + srcDir.getAbsolutePath());
        }

        Logger.log("[ECJ] Compiling " + javaFiles.size() + " source files against " + androidJar.getName());

        List<String> args = new ArrayList<>();
        String javaVer = project.getJavaVersion();
        if (javaVer == null || javaVer.isEmpty()) javaVer = "1.8";
        if (!javaVer.startsWith("-")) {
            if (javaVer.equals("Java 6") || javaVer.equals("1.6")) args.add("-1.6");
            else if (javaVer.equals("Java 7") || javaVer.equals("1.7")) args.add("-1.7");
            else args.add("-1.8");
        } else {
            args.add(javaVer);
        }

        args.add("-bootclasspath");
        args.add(androidJar.getAbsolutePath());
        args.add("-cp");
        args.add(androidJar.getAbsolutePath());
        args.add("-d");
        args.add(classesDir.getAbsolutePath());
        args.add("-proc:none");
        args.add("-nowarn");
        args.add("-encoding");
        args.add("UTF-8");

        for (File f : javaFiles) {
            args.add(f.getAbsolutePath());
        }

        StringWriter outWriter = new StringWriter();
        StringWriter errWriter = new StringWriter();
        PrintWriter outPw = new PrintWriter(outWriter);
        PrintWriter errPw = new PrintWriter(errWriter);

        boolean compileSuccess = false;
        ClassLoader cl = ApplicationLoader.getInstance().getToolchainClassLoader();

        try {
            Class<?> mainClass = Class.forName("org.eclipse.jdt.internal.compiler.batch.Main", true, cl);
            Method compileMethod = mainClass.getMethod("compile", String[].class, PrintWriter.class, PrintWriter.class, Object.class);
            Object result = compileMethod.invoke(null, args.toArray(new String[0]), outPw, errPw, null);
            if (result instanceof Boolean) {
                compileSuccess = (Boolean) result;
            }
        } catch (ClassNotFoundException e) {
            // Try BatchCompiler alternate entry point
            try {
                Class<?> batchClass = Class.forName("org.eclipse.jdt.core.compiler.batch.BatchCompiler", true, cl);
                Method compileMethod = batchClass.getMethod("compile", String[].class, PrintWriter.class, PrintWriter.class, Object.class);
                Object result = compileMethod.invoke(null, args.toArray(new String[0]), outPw, errPw, null);
                if (result instanceof Boolean) {
                    compileSuccess = (Boolean) result;
                }
            } catch (Exception ex) {
                throw new Exception("Eclipse Batch Compiler (ECJ) runtime not found: " + ex.getMessage());
            }
        } catch (Exception e) {
            throw new Exception("ECJ invocation error: " + e.getMessage(), e);
        }

        outPw.flush();
        errPw.flush();

        if (!compileSuccess) {
            String errStr = errWriter.toString().trim();
            if (errStr.isEmpty()) errStr = outWriter.toString().trim();
            Logger.log("[ECJ] Compilation error:\n" + errStr);
            throw new Exception("ECJ Compilation Errors:\n" + errStr);
        }

        Logger.log("[ECJ] Compilation successful. Bytecode generated at: " + classesDir.getAbsolutePath());
    }

    private void collectJavaFiles(File dir, List<File> list) {
        if (!dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectJavaFiles(f, list);
            } else if (f.getName().endsWith(".java")) {
                list.add(f);
            }
        }
    }
}
