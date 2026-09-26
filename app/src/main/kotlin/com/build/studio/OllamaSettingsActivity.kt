package com.build.studio

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
    private val modelList = mutableListOf<String>()
    private lateinit var modelAdapter: ArrayAdapter<String>
    private var isUserTypingModel = false

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

        // Live provider detection when typing API Key
        etApiKey.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val key = s?.toString()?.trim() ?: ""
                val currentModel = etModelCustom.text.toString().trim()
                val config = AiConfigHelper.detectProvider(key, customModel = currentModel)

                updateModelList(config.models, config.defaultModel)
            }
        })

        spModels.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position in modelList.indices && !isUserTypingModel) {
                    etModelCustom.setText(modelList[position])
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updateModelList(newModels: List<String>, defaultSelect: String) {
        modelList.clear()
        modelList.addAll(newModels)
        modelAdapter.notifyDataSetChanged()

        val pos = modelList.indexOf(defaultSelect)
        if (pos >= 0) {
            spModels.setSelection(pos)
        } else if (modelList.isNotEmpty()) {
            spModels.setSelection(0)
        }
        etModelCustom.setText(defaultSelect)
    }

    private fun loadPreferences() {
        val sp = getSharedPreferences("build_ai_prefs", Context.MODE_PRIVATE)
        val savedKey = sp.getString("api_key", "") ?: ""
        val savedModel = sp.getString("model_name", "")?.ifEmpty {
            sp.getString("model", "")
        } ?: ""

        val config = AiConfigHelper.detectProvider(savedKey, customModel = savedModel)

        etApiKey.setText(savedKey)
        modelList.clear()
        modelList.addAll(config.models)
        if (savedModel.isNotEmpty() && !modelList.contains(savedModel)) {
            modelList.add(0, savedModel)
        }
        modelAdapter.notifyDataSetChanged()

        val activeModel = savedModel.ifEmpty { config.defaultModel }
        etModelCustom.setText(activeModel)

        val pos = modelList.indexOf(activeModel)
        if (pos >= 0) {
            spModels.setSelection(pos)
        }

        etSystemPrompt.setText(
            sp.getString(
                "system_prompt",
                "You are Build AI, an expert Android and Kotlin developer assistant. Help the user build, debug and compile Android apps."
            )
        )
    }

    data class Candidate(
        val name: String,
        val testUrl: String,
        val endpoint: String,
        val baseUrl: String,
        val defaultModel: String
    )

    private fun testConnection() {
        tvStatus.text = "Testing connection..."
        tvStatus.setTextColor(Color.parseColor("#64748B"))

        val apiKey = etApiKey.text.toString().trim()
        val currentModel = etModelCustom.text.toString().trim()
        val detected = AiConfigHelper.detectProvider(apiKey, customModel = currentModel)

        val candidates = mutableListOf<Candidate>()

        if (apiKey.isNotEmpty()) {
            // Put detected candidate first
            candidates.add(
                Candidate(
                    name = detected.providerName,
                    testUrl = detected.testUrl,
                    endpoint = detected.baseUrl.substringBeforeLast("/chat/completions"),
                    baseUrl = detected.baseUrl,
                    defaultModel = detected.defaultModel
                )
            )

            // Fallback candidates
            if (apiKey.startsWith("sk-")) {
                // Could be DeepSeek or OpenAI
                if (detected.providerName == "DeepSeek") {
                    candidates.add(
                        Candidate(
                            name = "OpenAI",
                            testUrl = "https://api.openai.com/v1/models",
                            endpoint = "https://api.openai.com",
                            baseUrl = "https://api.openai.com/v1/chat/completions",
                            defaultModel = "gpt-4o-mini"
                        )
                    )
                } else {
                    candidates.add(
                        Candidate(
                            name = "DeepSeek",
                            testUrl = "https://api.deepseek.com/v1/models",
                            endpoint = "https://api.deepseek.com",
                            baseUrl = "https://api.deepseek.com/v1/chat/completions",
                            defaultModel = "deepseek-chat"
                        )
                    )
                }
            } else if (apiKey.startsWith("AIza")) {
                candidates.add(
                    Candidate(
                        name = "Google Gemini",
                        testUrl = "https://generativelanguage.googleapis.com/v1beta/openai/models",
                        endpoint = "https://generativelanguage.googleapis.com",
                        baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
                        defaultModel = "gemini-1.5-flash"
                    )
                )
            }
            // Also add Local Ollama with key (if running secured local instance)
            candidates.add(
                Candidate(
                    name = "Local Ollama",
                    testUrl = "http://127.0.0.1:11434/api/tags",
                    endpoint = "http://127.0.0.1:11434",
                    baseUrl = "http://127.0.0.1:11434/v1/chat/completions",
                    defaultModel = "qwen2.5-coder:latest"
                )
            )
        } else {
            // Local Ollama endpoints
            candidates.add(
                Candidate(
                    name = "Local Ollama (127.0.0.1)",
                    testUrl = "http://127.0.0.1:11434/api/tags",
                    endpoint = "http://127.0.0.1:11434",
                    baseUrl = "http://127.0.0.1:11434/v1/chat/completions",
                    defaultModel = "qwen2.5-coder:latest"
                )
            )
            candidates.add(
                Candidate(
                    name = "Local Ollama (localhost)",
                    testUrl = "http://localhost:11434/api/tags",
                    endpoint = "http://localhost:11434",
                    baseUrl = "http://localhost:11434/v1/chat/completions",
                    defaultModel = "qwen2.5-coder:latest"
                )
            )
            candidates.add(
                Candidate(
                    name = "Termux Bridge Server",
                    testUrl = "http://127.0.0.1:8080/ping",
                    endpoint = "http://127.0.0.1:8080",
                    baseUrl = "http://127.0.0.1:8080/run",
                    defaultModel = "qwen2.5-coder:latest"
                )
            )
        }

        testNextCandidate(candidates, 0, apiKey, null)
    }

    private fun testNextCandidate(
        candidates: List<Candidate>,
        index: Int,
        apiKey: String,
        lastError: String?
    ) {
        if (index >= candidates.size) {
            runOnUiThread {
                val err = if (apiKey.isEmpty()) {
                    "Failed: Local Ollama / Termux is not running on 127.0.0.1:11434.\nEnter an API key for DeepSeek or Gemini to use Cloud AI."
                } else {
                    lastError ?: "Failed: Could not connect to API with the provided key."
                }
                tvStatus.text = err
                tvStatus.setTextColor(Color.parseColor("#FF5252"))
            }
            return
        }

        val candidate = candidates[index]
        val client = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()

        val reqBuilder = Request.Builder().url(candidate.testUrl).get()
        if (apiKey.isNotEmpty()) {
            reqBuilder.header("Authorization", "Bearer $apiKey")
        }

        client.newCall(reqBuilder.build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                testNextCandidate(candidates, index + 1, apiKey, "Failed: ${e.localizedMessage ?: "Connection Refused"}")
            }

            override fun onResponse(call: Call, response: Response) {
                val code = response.code
                val bodyStr = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    runOnUiThread {
                        tvStatus.text = "✓ Connected to ${candidate.name}! (HTTP $code)"
                        tvStatus.setTextColor(Color.parseColor("#00C853"))

                        // Discovered models list
                        val discovered = mutableListOf<String>()
                        try {
                            val json = JSONObject(bodyStr)
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
                        } catch (e: Exception) {}

                        val effectiveModel = if (discovered.isNotEmpty()) {
                            discovered[0]
                        } else {
                            candidate.defaultModel
                        }

                        // Auto-save verified connection in preferences
                        val sp = getSharedPreferences("build_ai_prefs", Context.MODE_PRIVATE)
                        sp.edit()
                            .putString("endpoint", candidate.endpoint)
                            .putString("base_url", candidate.baseUrl)
                            .putString("api_key", apiKey)
                            .putString("model", effectiveModel)
                            .putString("model_name", effectiveModel)
                            .apply()

                        if (discovered.isNotEmpty()) {
                            for (m in discovered.reversed()) {
                                if (!modelList.contains(m)) modelList.add(0, m)
                            }
                            modelAdapter.notifyDataSetChanged()
                            etModelCustom.setText(effectiveModel)
                            val p = modelList.indexOf(effectiveModel)
                            if (p >= 0) spModels.setSelection(p)
                            Toast.makeText(this@OllamaSettingsActivity, "Connected to ${candidate.name}! Loaded ${discovered.size} models 🎯", Toast.LENGTH_SHORT).show()
                        } else {
                            etModelCustom.setText(effectiveModel)
                            Toast.makeText(this@OllamaSettingsActivity, "Connected to ${candidate.name}! 🚀", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else if (code == 401) {
                    if (index + 1 < candidates.size) {
                        // Key might belong to another candidate (e.g. OpenAI vs DeepSeek)
                        testNextCandidate(candidates, index + 1, apiKey, "HTTP 401: Invalid API Key for ${candidate.name}")
                    } else {
                        runOnUiThread {
                            tvStatus.text = "HTTP 401: Invalid API Key. Please check the key."
                            tvStatus.setTextColor(Color.parseColor("#FF5252"))
                        }
                    }
                } else {
                    testNextCandidate(candidates, index + 1, apiKey, "${candidate.name} returned HTTP $code")
                }
            }
        })
    }

    private fun savePreferences() {
        val sp = getSharedPreferences("build_ai_prefs", Context.MODE_PRIVATE)
        val apiKey = etApiKey.text.toString().trim()
        val customModel = etModelCustom.text.toString().trim()
        val prompt = etSystemPrompt.text.toString().trim()

        val config = AiConfigHelper.detectProvider(apiKey, customModel = customModel)
        val finalModel = customModel.ifEmpty { config.defaultModel }

        sp.edit()
            .putString("endpoint", config.baseUrl.substringBeforeLast("/chat/completions"))
            .putString("base_url", config.baseUrl)
            .putString("api_key", apiKey)
            .putString("model", finalModel)
            .putString("model_name", finalModel)
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
