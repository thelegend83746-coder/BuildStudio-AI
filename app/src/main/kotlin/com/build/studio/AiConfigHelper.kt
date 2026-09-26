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

        return when {
            // Google Gemini API key (starts with AIzaSy)
            trimmedKey.startsWith("AIza") -> {
                val model = if (trimmedModel.contains("gemini")) trimmedModel else "gemini-1.5-flash"
                ProviderConfig(
                    providerName = "Google Gemini",
                    baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
                    defaultModel = model,
                    models = listOf("gemini-1.5-flash", "gemini-2.0-flash", "gemini-1.5-pro"),
                    testUrl = "https://generativelanguage.googleapis.com/v1beta/openai/models"
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
            trimmedKey.startsWith("sk-proj-") || (trimmedKey.startsWith("sk-") && (trimmedUrl.contains("openai.com") || trimmedModel.startsWith("gpt-") || trimmedModel.startsWith("o1") || trimmedModel.startsWith("o3"))) -> {
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
            // DeepSeek API Key (sk- followed by 32 hex chars, default for sk-)
            trimmedKey.startsWith("sk-") -> {
                val model = if (trimmedModel.startsWith("deepseek")) trimmedModel else "deepseek-chat"
                ProviderConfig(
                    providerName = "DeepSeek",
                    baseUrl = "https://api.deepseek.com/v1/chat/completions",
                    defaultModel = model,
                    models = listOf("deepseek-chat", "deepseek-coder", "deepseek-reasoner"),
                    testUrl = "https://api.deepseek.com/v1/models"
                )
            }
            // Custom cloud URL saved previously
            trimmedUrl.startsWith("http") && !trimmedUrl.contains("127.0.0.1") && !trimmedUrl.contains("localhost") && !trimmedUrl.contains("10.0.2.2") -> {
                ProviderConfig(
                    providerName = "Cloud AI",
                    baseUrl = if (trimmedUrl.endsWith("/chat/completions")) trimmedUrl else "${trimmedUrl.trimEnd('/')}/v1/chat/completions",
                    defaultModel = trimmedModel.ifEmpty { "deepseek-chat" },
                    models = listOf("deepseek-chat", "gpt-4o-mini", "qwen2.5-coder:latest"),
                    testUrl = "${trimmedUrl.trimEnd('/')}/v1/models"
                )
            }
            // Local Ollama (running in Termux or on phone)
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
