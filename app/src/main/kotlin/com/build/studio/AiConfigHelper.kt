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

    val PROVIDERS = listOf(
        "Google AI Studio (Gemini)",
        "Ollama (Local / Offline)",
        "Qwen / DashScope (Alibaba)",
        "Sarvam AI (Indian LLM)",
        "DeepSeek",
        "Groq (Fast Cloud)",
        "OpenAI"
    )

    fun getProviderConfigByName(providerName: String, apiKey: String = ""): ProviderConfig {
        val p = providerName.trim()
        val key = apiKey.trim()

        return when {
            p.contains("Gemini", ignoreCase = true) || p.contains("Google", ignoreCase = true) -> {
                ProviderConfig(
                    providerName = "Google AI Studio",
                    baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
                    defaultModel = "gemini-1.5-flash",
                    testUrl = "https://generativelanguage.googleapis.com/v1beta/openai/models",
                    keyPrefixHint = "starts with AIzaSy...",
                    requiresKey = true,
                    models = listOf(
                        "gemini-1.5-flash [FREE TIER]",
                        "gemini-2.0-flash [FREE TIER]",
                        "gemini-1.5-flash-8b [FREE TIER]",
                        "gemini-1.5-pro [PAID / LIMITED]"
                    )
                )
            }

            p.contains("Ollama", ignoreCase = true) -> {
                ProviderConfig(
                    providerName = "Local Ollama",
                    baseUrl = "http://127.0.0.1:11434/v1/chat/completions",
                    defaultModel = "qwen2.5-coder:latest",
                    testUrl = "http://127.0.0.1:11434/api/tags",
                    keyPrefixHint = "No API Key required (127.0.0.1:11434)",
                    requiresKey = false,
                    models = listOf(
                        "qwen2.5-coder:latest [FREE / LOCAL]",
                        "deepseek-coder:6.7b [FREE / LOCAL]",
                        "llama3.2:latest [FREE / LOCAL]"
                    )
                )
            }

            p.contains("Qwen", ignoreCase = true) || p.contains("DashScope", ignoreCase = true) -> {
                ProviderConfig(
                    providerName = "Qwen / DashScope",
                    baseUrl = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1/chat/completions",
                    defaultModel = "qwen2.5-coder:7b",
                    testUrl = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1/models",
                    keyPrefixHint = "starts with sk-...",
                    requiresKey = true,
                    models = listOf(
                        "qwen2.5-coder:7b [FREE TRIAL]",
                        "qwen2.5-coder:latest [FREE TRIAL]",
                        "qwen-2.5-coder-32b-instruct [PAID]",
                        "qwen-plus [PAID]",
                        "qwen-turbo [PAID]"
                    )
                )
            }

            p.contains("Sarvam", ignoreCase = true) -> {
                ProviderConfig(
                    providerName = "Sarvam AI",
                    baseUrl = "https://api.sarvam.ai/v1/chat/completions",
                    defaultModel = "sarvam-2b",
                    testUrl = "https://api.sarvam.ai/v1/models",
                    keyPrefixHint = "Enter Sarvam Subscription Key",
                    requiresKey = true,
                    models = listOf(
                        "sarvam-2b [PAID / DEV CREDITS]",
                        "sarvam-m [PAID / DEV CREDITS]",
                        "sarvam-translate [PAID]"
                    )
                )
            }

            p.contains("DeepSeek", ignoreCase = true) -> {
                ProviderConfig(
                    providerName = "DeepSeek",
                    baseUrl = "https://api.deepseek.com/v1/chat/completions",
                    defaultModel = "deepseek-chat",
                    testUrl = "https://api.deepseek.com/v1/models",
                    keyPrefixHint = "starts with sk-...",
                    requiresKey = true,
                    models = listOf(
                        "deepseek-chat [PAID - LOW COST]",
                        "deepseek-coder [PAID - LOW COST]",
                        "deepseek-reasoner [PAID]"
                    )
                )
            }

            p.contains("Groq", ignoreCase = true) -> {
                ProviderConfig(
                    providerName = "Groq",
                    baseUrl = "https://api.groq.com/openai/v1/chat/completions",
                    defaultModel = "llama-3.3-70b-versatile",
                    testUrl = "https://api.groq.com/openai/v1/models",
                    keyPrefixHint = "starts with gsk_...",
                    requiresKey = true,
                    models = listOf(
                        "llama-3.3-70b-versatile [FREE TIER]",
                        "llama-3.1-8b-instant [FREE TIER]",
                        "mixtral-8x7b-32768 [FREE TIER]"
                    )
                )
            }

            p.contains("OpenAI", ignoreCase = true) -> {
                ProviderConfig(
                    providerName = "OpenAI",
                    baseUrl = "https://api.openai.com/v1/chat/completions",
                    defaultModel = "gpt-4o-mini",
                    testUrl = "https://api.openai.com/v1/models",
                    keyPrefixHint = "starts with sk-proj-... / sk-...",
                    requiresKey = true,
                    models = listOf(
                        "gpt-4o-mini [PAID]",
                        "gpt-4o [PAID]",
                        "gpt-3.5-turbo [PAID]"
                    )
                )
            }

            else -> {
                // Fallback by key format or model name
                resolveByModel(p, key)
            }
        }
    }

    /**
     * Resolves the exact provider configuration primarily based on the user's selected model.
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
            // Sarvam AI
            s.contains("sarvam", ignoreCase = true) -> {
                getProviderConfigByName("Sarvam AI", key)
            }

            // Google AI Studio / Gemini
            s.contains("gemini", ignoreCase = true) || s.contains("google", ignoreCase = true) -> {
                val base = getProviderConfigByName("Google AI Studio", key)
                val cleanId = cleanModelId(s)
                val model = if (cleanId.isNotEmpty()) cleanId else base.defaultModel
                base.copy(defaultModel = model)
            }

            // DeepSeek
            s.contains("deepseek", ignoreCase = true) -> {
                val base = getProviderConfigByName("DeepSeek", key)
                val cleanId = cleanModelId(s)
                val model = if (cleanId.isNotEmpty()) cleanId else base.defaultModel
                base.copy(defaultModel = model)
            }

            // Qwen-Coder (Alibaba DashScope / compatible)
            s.contains("qwen", ignoreCase = true) && !s.contains("ollama", ignoreCase = true) && !s.contains("127.0.0.1", ignoreCase = true) -> {
                val base = getProviderConfigByName("Qwen / DashScope", key)
                val cleanId = cleanModelId(s)
                val model = if (cleanId.isNotEmpty()) cleanId else base.defaultModel
                base.copy(defaultModel = model)
            }

            // Groq (Llama / Mixtral)
            s.contains("groq", ignoreCase = true) || s.contains("llama-3", ignoreCase = true) -> {
                val base = getProviderConfigByName("Groq", key)
                val cleanId = cleanModelId(s)
                val model = if (cleanId.isNotEmpty()) cleanId else base.defaultModel
                base.copy(defaultModel = model)
            }

            // OpenAI
            s.contains("gpt", ignoreCase = true) || s.contains("openai", ignoreCase = true) -> {
                val base = getProviderConfigByName("OpenAI", key)
                val cleanId = cleanModelId(s)
                val model = if (cleanId.isNotEmpty()) cleanId else base.defaultModel
                base.copy(defaultModel = model)
            }

            // Local Ollama (on device or Termux)
            s.contains("ollama", ignoreCase = true) -> {
                val base = getProviderConfigByName("Ollama", key)
                val cleanId = cleanModelId(s)
                val model = if (cleanId.isNotEmpty()) cleanId else base.defaultModel
                base.copy(defaultModel = model)
            }

            // Explicit custom URL configured
            trimmedUrl.startsWith("http") && !trimmedUrl.contains("127.0.0.1") && !trimmedUrl.contains("localhost") -> {
                val base = if (trimmedUrl.endsWith("/chat/completions")) trimmedUrl else "${trimmedUrl.trimEnd('/')}/v1/chat/completions"
                val test = if (trimmedUrl.endsWith("/")) "${trimmedUrl}v1/models" else "$trimmedUrl/v1/models"
                ProviderConfig(
                    providerName = "Custom Endpoint",
                    baseUrl = base,
                    defaultModel = if (s.isNotEmpty()) cleanModelId(s) else "gpt-4o-mini",
                    testUrl = test,
                    keyPrefixHint = "Custom API Key",
                    requiresKey = true,
                    models = if (s.isNotEmpty()) listOf(s) else listOf("gpt-4o-mini [CUSTOM]")
                )
            }

            // Fallback: detect from key format
            else -> {
                detectProviderByKey(key, s)
            }
        }
    }

    private fun detectProviderByKey(key: String, fallbackModel: String): ProviderConfig {
        return when {
            key.startsWith("AIza") -> getProviderConfigByName("Google AI Studio", key)
            key.startsWith("gsk_") -> getProviderConfigByName("Groq", key)
            key.startsWith("sk-") -> getProviderConfigByName("DeepSeek", key)
            key.isNotEmpty() -> getProviderConfigByName("OpenAI", key)
            else -> getProviderConfigByName("Ollama", key)
        }
    }

    /**
     * Parses the response from `/models` or `/api/tags` and decorates every model with a [FREE] or [PAID] badge.
     */
    fun parseModelsResponse(providerName: String, responseJson: String): List<String> {
        val result = mutableListOf<String>()
        try {
            val json = org.json.JSONObject(responseJson)

            // 1. Ollama format: { "models": [ { "name": "qwen2.5-coder:latest" } ] }
            if (json.has("models")) {
                val arr = json.getJSONArray("models")
                for (i in 0 until arr.length()) {
                    val m = arr.getJSONObject(i)
                    val name = m.optString("name", "")
                    if (name.isNotEmpty()) {
                        result.add("$name [FREE / LOCAL]")
                    }
                }
            }

            // 2. OpenAI / Gemini / DashScope / Groq / Sarvam format: { "data": [ { "id": "..." } ] }
            if (json.has("data")) {
                val arr = json.getJSONArray("data")
                for (i in 0 until arr.length()) {
                    val d = arr.getJSONObject(i)
                    val id = d.optString("id", "").ifEmpty { d.optString("name", "") }
                    if (id.isNotEmpty() && !id.contains("embedding", ignoreCase = true) && !id.contains("whisper", ignoreCase = true)) {
                        val isFree = when {
                            providerName.contains("Ollama", ignoreCase = true) -> true
                            providerName.contains("Groq", ignoreCase = true) -> true
                            id.contains("flash", ignoreCase = true) || id.contains("8b", ignoreCase = true) || id.contains("free", ignoreCase = true) -> true
                            else -> false
                        }
                        val tag = when {
                            providerName.contains("Ollama", ignoreCase = true) -> "[FREE / LOCAL]"
                            providerName.contains("Groq", ignoreCase = true) -> "[FREE TIER]"
                            isFree -> "[FREE TIER]"
                            providerName.contains("DeepSeek", ignoreCase = true) -> "[PAID - LOW COST]"
                            providerName.contains("Sarvam", ignoreCase = true) -> "[PAID / DEV CREDITS]"
                            else -> "[PAID]"
                        }
                        result.add("$id $tag")
                    }
                }
            }
        } catch (e: Exception) {
        }

        // If parsed list is empty, return default verified models for that provider
        if (result.isEmpty()) {
            val cfg = getProviderConfigByName(providerName)
            result.addAll(cfg.models)
        }
        return result
    }

    // Strips Free and Paid tags to return the pure model ID for API payloads.
    fun cleanModelId(modelOrDisplay: String): String {
        return modelOrDisplay
            .replace(Regex("""\[.*?\]"""), "")
            .replace(Regex("""\(.*?\)"""), "")
            .trim()
    }

    // Retain legacy method for backward compatibility
    fun detectProvider(apiKey: String, customBaseUrl: String = "", customModel: String = ""): ProviderConfig {
        return resolveByModel(customModel, apiKey, customBaseUrl)
    }
}
