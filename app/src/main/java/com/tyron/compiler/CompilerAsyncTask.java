package com.tyron.compiler;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.apk.builder.logger.Logger;
import com.apk.builder.model.Project;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CompilerAsyncTask {

    public interface CompilerCallback {
        void onProgress(String message, int step, int total);
        void onComplete(CompilerResult result);
    }

    private final Context context;
    private final Project project;
    private final CompilerCallback callback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public CompilerAsyncTask(Context context, Project project, CompilerCallback callback) {
        this.context = context;
        this.project = project;
        this.callback = callback;
    }

    public void execute() {
        Logger.clear();
        executor.execute(() -> {
            long startTime = System.currentTimeMillis();
            try {
                Logger.log("=== INITIATING BUILD PIPELINE FOR: " + project.getName() + " ===");

                // Step 1: AAPT2 Resource Compilation & Linking
                AAPT2Compiler aapt2 = new AAPT2Compiler(project);
                aapt2.setProgressListener((msg, s, t) -> postProgress(msg, 1, 7));
                aapt2.run();

                // Step 2: ECJ Java Source Compilation
                ECJCompiler ecj = new ECJCompiler(project);
                ecj.setProgressListener((msg, s, t) -> postProgress(msg, 2, 7));
                ecj.run();

                // Step 3: D8 Dex Conversion
                D8Compiler d8 = new D8Compiler(project);
                d8.setProgressListener((msg, s, t) -> postProgress(msg, 3, 7));
                d8.run();

                // Step 4, 5, 6: Packaging + ZipAlign + Signing
                File finalApk = ZipSignerWrapper.packageAndSign(project, (msg, step, total) -> {
                    postProgress(msg, step, total);
                });

                // Step 7: Build Success Trigger with duration stats
                long durationMs = System.currentTimeMillis() - startTime;
                String successMsg = "[APK Builder] Build success in " + durationMs + "ms";
                postProgress(successMsg, 7, 7);
                Logger.log(successMsg);
                Logger.log("Output APK: " + finalApk.getAbsolutePath() + " (" + finalApk.length() + " bytes)");

                CompilerResult result = new CompilerResult(true, finalApk, Logger.getLogs(), null);
                mainHandler.post(() -> callback.onComplete(result));
            } catch (Exception e) {
                long durationMs = System.currentTimeMillis() - startTime;
                String errMsg = e.getMessage() != null ? e.getMessage() : e.toString();
                Logger.log("[APK Builder] BUILD FAILED after " + durationMs + "ms: " + errMsg);
                CompilerResult result = new CompilerResult(false, null, Logger.getLogs(), errMsg);
                mainHandler.post(() -> callback.onComplete(result));
            }
        });
    }

    private void postProgress(String msg, int s, int t) {
        mainHandler.post(() -> callback.onProgress(msg, s, t));
    }
}
