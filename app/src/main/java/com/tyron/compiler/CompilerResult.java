package com.tyron.compiler;

import java.io.File;

public class CompilerResult {
    private boolean success;
    private File outputApk;
    private String logs;
    private String errorMessage;

    public CompilerResult(boolean success, File outputApk, String logs, String errorMessage) {
        this.success = success;
        this.outputApk = outputApk;
        this.logs = logs;
        this.errorMessage = errorMessage;
    }

    public boolean isSuccess() { return success; }
    public File getOutputApk() { return outputApk; }
    public String getLogs() { return logs; }
    public String getErrorMessage() { return errorMessage; }
}
