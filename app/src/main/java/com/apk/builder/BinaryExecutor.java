package com.apk.builder;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class BinaryExecutor {

    public static class Result {
        public int exitCode;
        public String output;

        public Result(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }

        public boolean isSuccess() {
            return exitCode == 0;
        }
    }

    public static Result execute(List<String> command, File workingDir) {
        StringBuilder output = new StringBuilder();
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            if (workingDir != null) {
                if (!workingDir.exists()) {
                    workingDir.mkdirs();
                }
                if (workingDir.exists() && workingDir.isDirectory()) {
                    pb.directory(workingDir);
                }
            }
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            return new Result(exitCode, output.toString());
        } catch (Exception e) {
            return new Result(-1, "Process execution error: " + e.getMessage());
        }
    }
}
