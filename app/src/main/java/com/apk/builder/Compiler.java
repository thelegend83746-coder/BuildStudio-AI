package com.apk.builder;

import android.content.Context;
import com.apk.builder.model.Project;
import com.tyron.compiler.CompilerAsyncTask;
import com.tyron.compiler.CompilerResult;
import java.io.File;

public class Compiler {

    public interface CompilerCallback {
        void onProgress(String message, int step, int totalSteps);
        void onCompleted(CompilerResult result);
    }

    private final Context context;
    private final Project project;
    private CompilerCallback callback;

    public Compiler(Context context, Project project) {
        this.context = context;
        this.project = project;
    }

    public void setCallback(CompilerCallback callback) {
        this.callback = callback;
    }

    public void run() {
        CompilerAsyncTask task = new CompilerAsyncTask(
                context,
                project,
                new CompilerAsyncTask.CompilerCallback() {
                    @Override
                    public void onProgress(String message, int step, int totalSteps) {
                        if (callback != null) {
                            callback.onProgress(message, step, totalSteps);
                        }
                    }

                    @Override
                    public void onComplete(CompilerResult result) {
                        if (callback != null) {
                            callback.onCompleted(result);
                        }
                    }

                    @Override
                    public void onCompleted(CompilerResult result) {
                        onComplete(result);
                    }
                }
        );
        task.execute();
    }
}
