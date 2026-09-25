package com.apk.builder

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

object BinaryExecutor {

    data class Result(
        val exitCode: Int,
        val output: String
    ) {
        val isSuccess: Boolean
            get() = exitCode == 0
    }

    @JvmStatic
    fun execute(command: List<String>, workingDir: File?): Result {
        val output = StringBuilder()
        return try {
            val pb = ProcessBuilder(command)
            if (workingDir != null) {
                if (!workingDir.exists()) {
                    workingDir.mkdirs()
                }
                if (workingDir.exists() && workingDir.isDirectory) {
                    pb.directory(workingDir)
                }
            }
            pb.redirectErrorStream(true)
            val process = pb.start()

            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }
            }

            val exitCode = process.waitFor()
            Result(exitCode, output.toString())
        } catch (e: Exception) {
            Result(-1, "Process execution error: ${e.message}")
        }
    }
}
