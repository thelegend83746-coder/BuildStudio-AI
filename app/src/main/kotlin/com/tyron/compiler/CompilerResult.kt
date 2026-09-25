package com.tyron.compiler

import java.io.File

data class CompilerResult(
    val isSuccess: Boolean,
    val apkFile: File?,
    val logs: String,
    val errorMessage: String? = null
)
