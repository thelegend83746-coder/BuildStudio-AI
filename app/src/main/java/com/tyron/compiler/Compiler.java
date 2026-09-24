package com.tyron.compiler;

import com.apk.builder.logger.Logger;
import com.apk.builder.model.Project;
import java.io.File;

public abstract class Compiler {

    public interface OnProgressUpdateListener {
        void onProgress(String message, int step, int totalSteps);
    }

    protected final Project project;
    protected OnProgressUpdateListener progressListener;

    public Compiler(Project project) {
        this.project = project;
    }

    public void setProgressListener(OnProgressUpdateListener listener) {
        this.progressListener = listener;
    }

    protected void notifyProgress(String message, int step, int totalSteps) {
        Logger.log(message);
        if (progressListener != null) {
            progressListener.onProgress(message, step, totalSteps);
        }
    }

    public abstract void run() throws Exception;
}
