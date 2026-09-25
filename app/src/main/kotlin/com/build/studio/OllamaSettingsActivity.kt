package com.build.studio

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.blogspot.atifsoftwares.animatoolib.Animatoo
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class OllamaSettingsActivity : AppCompatActivity() {

    private lateinit var etApiKey: EditText
    private lateinit var etModelCustom: EditText
    private lateinit var etSystemPrompt: EditText
    private lateinit var spModels: Spinner
    private lateinit var tvStatus: TextView
    private val modelList = mutableListOf(
        "qwen2.5-coder:latest",
        "qwen2.5-coder:7b",
        "deepseek-coder",
        "deepseek-chat",
        "codellama",
        "llama3",
        "glm-4.6"
    )
    private lateinit var modelAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.ollama_settings)

        val backBtn = findViewById<View>(R.id.back_btn) ?: findViewById<View>(R.id.btn_back)
        backBtn?.setOnClickListener { finish(); Animatoo.animateSlideRight(this) }

        etApiKey = findViewById(R.id.et_api_key)
        etModelCustom = findViewById(R.id.et_model_custom) ?: findViewById(R.id.et_model_name)
        etSystemPrompt = findViewById(R.id.et_system_prompt)
        spModels = findViewById(R.id.sp_models) ?: findViewById(R.id.spinner_models)
        tvStatus = findViewById(R.id.tv_connection_status) ?: findViewById(R.id.tv_conn_status)

        modelAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modelList)
        spModels.adapter = modelAdapter

        loadPreferences()

        val btnTest = findViewById<View>(R.id.btn_test_connection) ?: findViewById<View>(R.id.btn_test_conn)
        val btnSave = findViewById<View>(R.id.btn_save_settings) ?: findViewById<View>(R.id.btn_save)

        btnTest?.setOnClickListener { testConnection() }
        btnSave?.setOnClickListener { savePreferences() }
    }

    private fun loadPreferences() {
        val sp = getSharedPreferences("build_ai_prefs", Context.MODE_PRIVATE)
        etApiKey.setText(sp.getString("api_key", ""))

        val savedModel = sp.getString("model_name", "")?.ifEmpty {
            sp.getString("model", "qwen2.5-coder:latest")
        } ?: "qwen2.5-coder:latest"
        etModelCustom.setText(savedModel)

        etSystemPrompt.setText(
            sp.getString(
                "system_prompt",
                "You are Build AI, an expert Android and Kotlin developer assistant. Help the user build, debug and compile Android apps."
            )
        )

        val pos = modelList.indexOf(savedModel)
        if (pos >= 0) {
            spModels.setSelection(pos)
        } else if (savedModel.isNotEmpty()) {
            modelList.add(0, savedModel)
            modelAdapter.notifyDataSetChanged()
            spModels.setSelection(0)
        }

        spModels.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position in modelList.indices) {
                    etModelCustom.setText(modelList[position])
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    data class EndpointCandidate(
        val name: String,
        val testUrl: String,
        val endpoint: String,
        val baseUrl: String
    )

    private fun testConnection() {
        tvStatus.text = "Testing connection..."
        tvStatus.setTextColor(Color.parseColor("#64748B"))

        val apiKey = etApiKey.text.toString().trim()

        val candidates = mutableListOf<EndpointCandidate>()
        if (apiKey.isNotEmpty()) {
            // If API key is provided, test DeepSeek, OpenAI, Groq, and local Ollama
            candidates.add(
                EndpointCandidate(
                    name = "DeepSeek",
                    testUrl = "https://api.deepseek.com/v1/models",
                    endpoint = "https://api.deepseek.com",
                    baseUrl = "https://api.deepseek.com/v1/chat/completions"
                )
            )
            candidates.add(
                EndpointCandidate(
                    name = "OpenAI",
                    testUrl = "https://api.openai.com/v1/models",
                    endpoint = "https://api.openai.com",
                    baseUrl = "https://api.openai.com/v1/chat/completions"
                )
            )
            candidates.add(
                EndpointCandidate(
                    name = "Local Ollama",
                    testUrl = "http://127.0.0.1:11434/api/tags",
                    endpoint = "http://127.0.0.1:11434",
                    baseUrl = "http://127.0.0.1:11434/v1/chat/completions"
                )
            )
            candidates.add(
                EndpointCandidate(
                    name = "Groq",
                    testUrl = "https://api.groq.com/openai/v1/models",
                    endpoint = "https://api.groq.com/openai",
                    baseUrl = "https://api.groq.com/openai/v1/chat/completions"
                )
            )
        } else {
            // Local Ollama endpoints
            candidates.add(
                EndpointCandidate(
                    name = "Local Ollama (127.0.0.1)",
                    testUrl = "http://127.0.0.1:11434/api/tags",
                    endpoint = "http://127.0.0.1:11434",
                    baseUrl = "http://127.0.0.1:11434/v1/chat/completions"
                )
            )
            candidates.add(
                EndpointCandidate(
                    name = "Local Ollama (localhost)",
                    testUrl = "http://localhost:11434/api/tags",
                    endpoint = "http://localhost:11434",
                    baseUrl = "http://localhost:11434/v1/chat/completions"
                )
            )
            candidates.add(
                EndpointCandidate(
                    name = "Emulator Ollama (10.0.2.2)",
                    testUrl = "http://10.0.2.2:11434/api/tags",
                    endpoint = "http://10.0.2.2:11434",
                    baseUrl = "http://10.0.2.2:11434/v1/chat/completions"
                )
            )
        }

        testNextCandidate(candidates, 0, apiKey, null)
    }

    private fun testNextCandidate(
        candidates: List<EndpointCandidate>,
        index: Int,
        apiKey: String,
        lastError: String?
    ) {
        if (index >= candidates.size) {
            runOnUiThread {
                tvStatus.text = lastError ?: "Failed: Could not connect to any endpoint"
                tvStatus.setTextColor(Color.parseColor("#FF5252"))
            }
            return
        }

        val candidate = candidates[index]
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

        val reqBuilder = Request.Builder().url(candidate.testUrl).get()
        if (apiKey.isNotEmpty()) {
            reqBuilder.addHeader("Authorization", "Bearer $apiKey")
        }

        client.newCall(reqBuilder.build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Try next candidate
                testNextCandidate(candidates, index + 1, apiKey, "Failed: ${e.localizedMessage ?: "Connection Refused"}")
            }

            override fun onResponse(call: Call, response: Response) {
                val code = response.code
                val bodyStr = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    runOnUiThread {
                        tvStatus.text = "✓ Connected to ${candidate.name}! (HTTP $code)"
                        tvStatus.setTextColor(Color.parseColor("#00C853"))

                        // Auto-save the working endpoint and baseUrl in preferences
                        val sp = getSharedPreferences("build_ai_prefs", Context.MODE_PRIVATE)
                        sp.edit()
                            .putString("endpoint", candidate.endpoint)
                            .putString("base_url", candidate.baseUrl)
                            .putString("api_key", apiKey)
                            .apply()

                        // Parse discovered models
                        try {
                            val json = JSONObject(bodyStr)
                            val discovered = mutableListOf<String>()
                            if (json.has("models")) {
                                val arr = json.getJSONArray("models")
                                for (i in 0 until arr.length()) {
                                    val item = arr.getJSONObject(i)
                                    val name = if (item.has("name")) item.getString("name") else item.optString("model")
                                    if (name.isNotEmpty()) discovered.add(name)
                                }
                            } else if (json.has("data")) {
                                val arr = json.getJSONArray("data")
                                for (i in 0 until arr.length()) {
                                    val item = arr.getJSONObject(i)
                                    val id = item.optString("id")
                                    if (id.isNotEmpty()) discovered.add(id)
                                }
                            }
                            if (discovered.isNotEmpty()) {
                                for (m in discovered.reversed()) {
                                    if (!modelList.contains(m)) modelList.add(0, m)
                                }
                                modelAdapter.notifyDataSetChanged()
                                etModelCustom.setText(discovered[0])
                                spModels.setSelection(0)
                                Toast.makeText(this@OllamaSettingsActivity, "Connected to ${candidate.name}! Loaded ${discovered.size} models 🎯", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@OllamaSettingsActivity, "Connected to ${candidate.name}!", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this@OllamaSettingsActivity, "Connected to ${candidate.name}!", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else if (code == 401) {
                    runOnUiThread {
                        tvStatus.text = "HTTP 401: Invalid API Key for ${candidate.name}"
                        tvStatus.setTextColor(Color.parseColor("#FF5252"))
                    }
                } else {
                    // Try next candidate
                    testNextCandidate(candidates, index + 1, apiKey, "${candidate.name} returned HTTP $code")
                }
            }
        })
    }

    private fun savePreferences() {
        val sp = getSharedPreferences("build_ai_prefs", Context.MODE_PRIVATE)
        val apiKey = etApiKey.text.toString().trim()
        val model = etModelCustom.text.toString().trim().ifEmpty { "qwen2.5-coder:latest" }
        val prompt = etSystemPrompt.text.toString().trim()

        var endpoint = sp.getString("endpoint", "") ?: ""
        var baseUrl = sp.getString("base_url", "") ?: ""

        if (endpoint.isEmpty() || baseUrl.isEmpty()) {
            if (apiKey.startsWith("sk-")) {
                endpoint = "https://api.deepseek.com"
                baseUrl = "https://api.deepseek.com/v1/chat/completions"
            } else {
                endpoint = "http://127.0.0.1:11434"
                baseUrl = "http://127.0.0.1:11434/v1/chat/completions"
            }
        }

        sp.edit()
            .putString("endpoint", endpoint)
            .putString("base_url", baseUrl)
            .putString("api_key", apiKey)
            .putString("model", model)
            .putString("model_name", model)
            .putString("system_prompt", prompt)
            .apply()

        Toast.makeText(this, "AI Settings saved! 🚀", Toast.LENGTH_SHORT).show()
        finish()
        Animatoo.animateSlideRight(this)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        Animatoo.animateSlideRight(this)
    }
}
