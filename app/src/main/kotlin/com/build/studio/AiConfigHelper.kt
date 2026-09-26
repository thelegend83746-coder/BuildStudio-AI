package com.build.studio

object AiConfigHelper {

    data class ProviderConfig(
        val providerName: String,
        val baseUrl: String,
        val defaultModel: String,
        val testUrl: String,
        val keyPrefixHint: String = "",
        val requiresKey: Boolean = true,
        val models: List<String> = emptyList()
    )

    /**
     * Resolves the exact provider configuration primarily based on the user's selected model.
     * When user selects Google Gemini -> tests/calls Gemini (DeepSeek key fails with HTTP 400).
     * When user selects DeepSeek -> tests/calls DeepSeek (Gemini key fails with HTTP 401).
     * When user selects Local Ollama -> tests local 127.0.0.1:11434.
     */
    fun resolveByModel(
        selectedModelOrLabel: String,
        apiKey: String = "",
        customBaseUrl: String = ""
    ): ProviderConfig {
        val s = selectedModelOrLabel.trim()
        val key = apiKey.trim()
        val trimmedUrl = customBaseUrl.trim()

        return when {
            // 1. Google Gemini
            s.contains("gemini", ignoreCase = true) -> {
                val model = when {
                    s.contains("2.0") -> "gemini-2.0-flash"
                    s.contains("pro") -> "gemini-1.5-pro"
                    else -> "gemini-1.5-flash"
                }
                ProviderConfig(
                    providerName = "Google Gemini",
                    baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
                    defaultModel = model,
                    testUrl = "https://generativelanguage.googleapis.com/v1beta/openai/models",
                    keyPrefixHint = "starts with AIzaSy...",
                    requiresKey = true,
                    models = listOf("gemini-1.5-flash", "gemini-2.0-flash", "gemini-1.5-pro")
                )
            }

            // 2. DeepSeek
            s.contains("deepseek", ignoreCase = true) -> {
                val model = when {
                    s.contains("coder", ignoreCase = true) -> "deepseek-coder"
                    s.contains("reasoner", ignoreCase = true) -> "deepseek-reasoner"
                    else -> "deepseek-chat"
                }
                ProviderConfig(
                    providerName = "DeepSeek",
                    baseUrl = "https://api.deepseek.com/v1/chat/completions",
                    defaultModel = model,
                    testUrl = "https://api.deepseek.com/v1/models",
                    keyPrefixHint = "starts with sk-...",
                    requiresKey = true,
                    models = listOf("deepseek-chat", "deepseek-coder", "deepseek-reasoner")
                )
            }

            // 3. GLM-4.6 / GLM-4.7 (Zhipu AI)
            s.contains("glm", ignoreCase = true) -> {
                val model = when {
                    s.contains("4.7") || s.contains("4-plus") -> "glm-4-plus"
                    s.contains("4.6") || s.contains("4-0520") -> "glm-4-0520"
                    else -> "glm-4-flash"
                }
                ProviderConfig(
                    providerName = "Zhipu AI (GLM)",
                    baseUrl = "https://open.bigmodel.cn/api/paas/v4/chat/completions",
                    defaultModel = model,
                    testUrl = "https://open.bigmodel.cn/api/paas/v4/models",
                    keyPrefixHint = "id.secret format",
                    requiresKey = true,
                    models = listOf("glm-4-flash", "glm-4-plus", "glm-4-0520")
                )
            }

            // 4. Qwen-Coder (Alibaba DashScope / compatible)
            s.contains("qwen", ignoreCase = true) && !s.contains("ollama", ignoreCase = true) -> {
                val model = if (s.contains("32b")) "qwen-2.5-coder-32b-instruct" else "qwen2.5-coder:latest"
                ProviderConfig(
                    providerName = "Qwen / DashScope",
                    baseUrl = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1/chat/completions",
                    defaultModel = model,
                    testUrl = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1/models",
                    keyPrefixHint = "starts with sk-...",
                    requiresKey = true,
                    models = listOf("qwen2.5-coder:latest", "qwen-2.5-coder-32b-instruct")
                )
            }

            // 5. Groq (Llama / Mixtral)
            s.contains("groq", ignoreCase = true) || s.contains("llama", ignoreCase = true) -> {
                ProviderConfig(
                    providerName = "Groq",
                    baseUrl = "https://api.groq.com/openai/v1/chat/completions",
                    defaultModel = "llama-3.3-70b-versatile",
                    testUrl = "https://api.groq.com/openai/v1/models",
                    keyPrefixHint = "starts with gsk_...",
                    requiresKey = true,
                    models = listOf("llama-3.3-70b-versatile", "mixtral-8x7b-32768", "llama-3.1-8b-instant")
                )
            }

            // 6. OpenAI
            s.contains("gpt", ignoreCase = true) || s.contains("openai", ignoreCase = true) -> {
                ProviderConfig(
                    providerName = "OpenAI",
                    baseUrl = "https://api.openai.com/v1/chat/completions",
                    defaultModel = "gpt-4o-mini",
                    testUrl = "https://api.openai.com/v1/models",
                    keyPrefixHint = "starts with sk-proj-... / sk-...",
                    requiresKey = true,
                    models = listOf("gpt-4o-mini", "gpt-4o", "gpt-3.5-turbo")
                )
            }

            // 7. Local Ollama (on device or Termux)
            s.contains("ollama", ignoreCase = true) -> {
                ProviderConfig(
                    providerName = "Local Ollama",
                    baseUrl = "http://127.0.0.1:11434/v1/chat/completions",
                    defaultModel = "qwen2.5-coder:latest",
                    testUrl = "http://127.0.0.1:11434/api/tags",
                    keyPrefixHint = "No key needed for local daemon",
                    requiresKey = false,
                    models = listOf("qwen2.5-coder:latest", "deepseek-coder:6.7b", "llama3.2:latest")
                )
            }

            // 8. Explicit custom URL configured
            trimmedUrl.startsWith("http") && !trimmedUrl.contains("127.0.0.1") && !trimmedUrl.contains("localhost") -> {
                val base = if (trimmedUrl.endsWith("/chat/completions")) trimmedUrl else "${trimmedUrl.trimEnd('/')}/v1/chat/completions"
                val test = if (trimmedUrl.endsWith("/")) "${trimmedUrl}v1/models" else "$trimmedUrl/v1/models"
                ProviderConfig(
                    providerName = "Custom Endpoint",
                    baseUrl = base,
                    defaultModel = if (s.isNotEmpty()) s else "gpt-4o-mini",
                    testUrl = test,
                    keyPrefixHint = "Custom API Key",
                    requiresKey = true,
                    models = if (s.isNotEmpty()) listOf(s) else listOf("gpt-4o-mini")
                )
            }

            // Fallback: If model is empty or unrecognized, detect from key format
            else -> {
                detectProviderByKey(key, s)
            }
        }
    }

    private fun detectProviderByKey(key: String, fallbackModel: String): ProviderConfig {
        return when {
            key.startsWith("AIza") -> {
                ProviderConfig(
                    providerName = "Google Gemini",
                    baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
                    defaultModel = if (fallbackModel.isNotEmpty()) fallbackModel else "gemini-1.5-flash",
                    testUrl = "https://generativelanguage.googleapis.com/v1beta/openai/models",
                    keyPrefixHint = "starts with AIzaSy...",
                    requiresKey = true,
                    models = listOf("gemini-1.5-flash", "gemini-2.0-flash", "gemini-1.5-pro")
                )
            }
            key.startsWith("gsk_") -> {
                ProviderConfig(
                    providerName = "Groq",
                    baseUrl = "https://api.groq.com/openai/v1/chat/completions",
                    defaultModel = "llama-3.3-70b-versatile",
                    testUrl = "https://api.groq.com/openai/v1/models",
                    keyPrefixHint = "starts with gsk_...",
                    requiresKey = true,
                    models = listOf("llama-3.3-70b-versatile", "mixtral-8x7b-32768", "llama-3.1-8b-instant")
                )
            }
            key.contains(".") && !key.startsWith("http") -> {
                ProviderConfig(
                    providerName = "Zhipu AI (GLM)",
                    baseUrl = "https://open.bigmodel.cn/api/paas/v4/chat/completions",
                    defaultModel = "glm-4-flash",
                    testUrl = "https://open.bigmodel.cn/api/paas/v4/models",
                    keyPrefixHint = "id.secret format",
                    requiresKey = true,
                    models = listOf("glm-4-flash", "glm-4-plus", "glm-4-0520")
                )
            }
            key.startsWith("sk-") -> {
                ProviderConfig(
                    providerName = "DeepSeek",
                    baseUrl = "https://api.deepseek.com/v1/chat/completions",
                    defaultModel = if (fallbackModel.isNotEmpty()) fallbackModel else "deepseek-chat",
                    testUrl = "https://api.deepseek.com/v1/models",
                    keyPrefixHint = "starts with sk-...",
                    requiresKey = true,
                    models = listOf("deepseek-chat", "deepseek-coder", "deepseek-reasoner")
                )
            }
            key.isNotEmpty() -> {
                ProviderConfig(
                    providerName = "OpenAI Compatible",
                    baseUrl = "https://api.openai.com/v1/chat/completions",
                    defaultModel = if (fallbackModel.isNotEmpty()) fallbackModel else "gpt-4o-mini",
                    testUrl = "https://api.openai.com/v1/models",
                    keyPrefixHint = "API Key",
                    requiresKey = true,
                    models = listOf("gpt-4o-mini", "gpt-4o", "gpt-3.5-turbo")
                )
            }
            else -> {
                ProviderConfig(
                    providerName = "Local Ollama",
                    baseUrl = "http://127.0.0.1:11434/v1/chat/completions",
                    defaultModel = "qwen2.5-coder:latest",
                    testUrl = "http://127.0.0.1:11434/api/tags",
                    keyPrefixHint = "No key needed for local daemon",
                    requiresKey = false,
                    models = listOf("qwen2.5-coder:latest", "deepseek-coder:6.7b", "llama3.2:latest")
                )
            }
        }
    }

    // Retain legacy method for backward compatibility
    fun detectProvider(apiKey: String, customBaseUrl: String = "", customModel: String = ""): ProviderConfig {
        return resolveByModel(customModel, apiKey, customBaseUrl)
    }
}
