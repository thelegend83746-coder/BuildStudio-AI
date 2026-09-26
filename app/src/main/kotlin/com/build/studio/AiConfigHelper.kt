package com.build.studio

object AiConfigHelper {

    data class ProviderConfig(
        val providerName: String,
        val baseUrl: String,
        val defaultModel: String,
        val models: List<String>,
        val testUrl: String
    )

    fun detectProvider(
        apiKey: String,
        customBaseUrl: String = "",
        customModel: String = ""
    ): ProviderConfig {
        val trimmedKey = apiKey.trim()
        val trimmedUrl = customBaseUrl.trim()
        val trimmedModel = customModel.trim()

        // 1. If explicit Custom Endpoint / Ollama Cloud URL is provided, always honor it
        if (trimmedUrl.startsWith("http") && !trimmedUrl.contains("127.0.0.1") && !trimmedUrl.contains("localhost") && !trimmedUrl.contains("10.0.2.2")) {
            val base = if (trimmedUrl.endsWith("/chat/completions")) trimmedUrl else "${trimmedUrl.trimEnd('/')}/v1/chat/completions"
            val test = if (trimmedUrl.endsWith("/")) "${trimmedUrl}v1/models" else "$trimmedUrl/v1/models"
            val defaultM = when {
                trimmedModel.isNotEmpty() -> trimmedModel
                trimmedUrl.contains("ollama") -> "qwen2.5-coder:latest"
                else -> "gpt-4o-mini"
            }
            return ProviderConfig(
                providerName = if (trimmedUrl.contains("ollama")) "Ollama Cloud" else "Custom Endpoint",
                baseUrl = base,
                defaultModel = defaultM,
                models = listOf(defaultM, "qwen2.5-coder:latest", "glm-4.7", "gemini-1.5-flash", "gpt-4o-mini"),
                testUrl = test
            )
        }

        // 2. Provider detection based on API Key and Model
        return when {
            // Google Gemini API key (starts with AIzaSy) or gemini model specified
            trimmedKey.startsWith("AIza") || (trimmedKey.isNotEmpty() && trimmedModel.contains("gemini", ignoreCase = true)) -> {
                val model = if (trimmedModel.contains("gemini", ignoreCase = true)) trimmedModel else "gemini-1.5-flash"
                ProviderConfig(
                    providerName = "Google Gemini",
                    baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
                    defaultModel = model,
                    models = listOf("gemini-1.5-flash", "gemini-2.0-flash", "gemini-1.5-pro"),
                    testUrl = "https://generativelanguage.googleapis.com/v1beta/openai/models"
                )
            }

            // GLM-4.6 / GLM-4.7 / Zhipu API key (e.g. contains '.' or model starts with glm)
            trimmedModel.contains("glm", ignoreCase = true) || (trimmedKey.contains(".") && !trimmedKey.startsWith("http")) -> {
                val model = when {
                    trimmedModel.contains("4.7") || trimmedModel.contains("4-plus") -> "glm-4-plus"
                    trimmedModel.contains("4.6") || trimmedModel.contains("4-0520") -> "glm-4-0520"
                    trimmedModel.isNotEmpty() -> trimmedModel
                    else -> "glm-4-flash"
                }
                ProviderConfig(
                    providerName = "Zhipu AI (GLM)",
                    baseUrl = "https://open.bigmodel.cn/api/paas/v4/chat/completions",
                    defaultModel = model,
                    models = listOf("glm-4-plus", "glm-4-0520", "glm-4-flash", "glm-4"),
                    testUrl = "https://open.bigmodel.cn/api/paas/v4/models"
                )
            }

            // Groq API key (starts with gsk_)
            trimmedKey.startsWith("gsk_") -> {
                val model = if (trimmedModel.contains("llama") || trimmedModel.contains("qwen") || trimmedModel.contains("mixtral")) {
                    trimmedModel
                } else {
                    "llama-3.3-70b-versatile"
                }
                ProviderConfig(
                    providerName = "Groq",
                    baseUrl = "https://api.groq.com/openai/v1/chat/completions",
                    defaultModel = model,
                    models = listOf("llama-3.3-70b-versatile", "qwen-2.5-coder-32b", "llama-3.1-8b-instant"),
                    testUrl = "https://api.groq.com/openai/v1/models"
                )
            }

            // OpenRouter API key (starts with sk-or-v1-)
            trimmedKey.startsWith("sk-or-") -> {
                val model = if (trimmedModel.contains("/")) trimmedModel else "deepseek/deepseek-chat"
                ProviderConfig(
                    providerName = "OpenRouter",
                    baseUrl = "https://openrouter.ai/api/v1/chat/completions",
                    defaultModel = model,
                    models = listOf("deepseek/deepseek-chat", "google/gemini-2.0-flash-exp:free", "qwen/qwen-2.5-coder-32b-instruct"),
                    testUrl = "https://openrouter.ai/api/v1/models"
                )
            }

            // OpenAI Project Key or OpenAI with explicit model/url
            trimmedKey.startsWith("sk-proj-") || (trimmedKey.startsWith("sk-") && (trimmedModel.startsWith("gpt-") || trimmedModel.startsWith("o1") || trimmedModel.startsWith("o3"))) -> {
                val model = if (trimmedModel.startsWith("gpt-") || trimmedModel.startsWith("o1") || trimmedModel.startsWith("o3")) {
                    trimmedModel
                } else {
                    "gpt-4o-mini"
                }
                ProviderConfig(
                    providerName = "OpenAI",
                    baseUrl = "https://api.openai.com/v1/chat/completions",
                    defaultModel = model,
                    models = listOf("gpt-4o-mini", "gpt-4o", "gpt-3.5-turbo"),
                    testUrl = "https://api.openai.com/v1/models"
                )
            }

            // DeepSeek API Key (sk- followed by 32 hex chars, or explicit deepseek model)
            trimmedKey.startsWith("sk-") && (trimmedModel.contains("deepseek", ignoreCase = true) || !trimmedModel.contains("qwen", ignoreCase = true)) -> {
                val model = if (trimmedModel.startsWith("deepseek")) trimmedModel else "deepseek-chat"
                ProviderConfig(
                    providerName = "DeepSeek",
                    baseUrl = "https://api.deepseek.com/v1/chat/completions",
                    defaultModel = model,
                    models = listOf("deepseek-chat", "deepseek-coder", "deepseek-reasoner"),
                    testUrl = "https://api.deepseek.com/v1/models"
                )
            }

            // Qwen-Coder or DashScope API Key
            trimmedModel.contains("qwen", ignoreCase = true) && trimmedKey.isNotEmpty() -> {
                ProviderConfig(
                    providerName = "Qwen / DashScope",
                    baseUrl = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1/chat/completions",
                    defaultModel = if (trimmedModel.isNotEmpty()) trimmedModel else "qwen2.5-coder:latest",
                    models = listOf("qwen-2.5-coder-32b-instruct", "qwen2.5-coder:latest", "qwen-turbo"),
                    testUrl = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1/models"
                )
            }

            // Generic cloud API key with unknown prefix (assume OpenAI-compatible or Cloud AI)
            trimmedKey.isNotEmpty() -> {
                val model = trimmedModel.ifEmpty { "gpt-4o-mini" }
                ProviderConfig(
                    providerName = "Cloud AI (OpenAI Compatible)",
                    baseUrl = "https://api.openai.com/v1/chat/completions",
                    defaultModel = model,
                    models = listOf("gpt-4o-mini", "gpt-4o", "qwen-2.5-coder-32b"),
                    testUrl = "https://api.openai.com/v1/models"
                )
            }

            // Local Ollama (running in Termux or phone when no key is set)
            else -> {
                val model = if (trimmedModel.isNotEmpty() && !trimmedModel.startsWith("deepseek") && !trimmedModel.startsWith("gpt-") && !trimmedModel.startsWith("gemini")) {
                    trimmedModel
                } else {
                    "qwen2.5-coder:latest"
                }
                ProviderConfig(
                    providerName = "Local Ollama",
                    baseUrl = "http://127.0.0.1:11434/v1/chat/completions",
                    defaultModel = model,
                    models = listOf("qwen2.5-coder:latest", "qwen2.5-coder:7b", "deepseek-coder", "codellama", "llama3"),
                    testUrl = "http://127.0.0.1:11434/api/tags"
                )
            }
        }
    }
}
