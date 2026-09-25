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

    private lateinit var etEndpoint: EditText
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

        etEndpoint = findViewById(R.id.et_endpoint)
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
        val savedEndpoint = sp.getString("endpoint", "")?.ifEmpty {
            sp.getString("base_url", "http://localhost:11434")
        } ?: "http://localhost:11434"
        etEndpoint.setText(savedEndpoint)

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

    private fun testConnection() {
        tvStatus.text = "Testing connection..."
        tvStatus.setTextColor(Color.parseColor("#64748B"))

        var endpoint = etEndpoint.text.toString().trim()
        val apiKey = etApiKey.text.toString().trim()

        if (endpoint.isEmpty()) {
            endpoint = if (apiKey.isEmpty()) "http://localhost:11434" else "https://api.deepseek.com/v1"
            etEndpoint.setText(endpoint)
        }

        val clean = endpoint.trimEnd('/')
        val isOllama = clean.contains("11434") || clean.contains("ollama")

        // Build target test URL
        val testUrl = when {
            isOllama -> {
                if (clean.endsWith("/api/tags") || clean.endsWith("/api/version") || clean.endsWith("/v1/models")) {
                    clean
                } else if (clean.endsWith("/api")) {
                    "$clean/tags"
                } else {
                    "$clean/api/tags"
                }
            }
            clean.endsWith("/models") -> clean
            clean.endsWith("/v1") -> "$clean/models"
            clean.contains("/v1/") -> "$clean/models"
            else -> "$clean/v1/models"
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()

        val reqBuilder = Request.Builder().url(testUrl).get()
        if (apiKey.isNotEmpty()) {
            reqBuilder.addHeader("Authorization", "Bearer $apiKey")
        }

        client.newCall(reqBuilder.build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    tvStatus.text = "Failed: ${e.localizedMessage ?: "Connection Refused"}"
                    tvStatus.setTextColor(Color.parseColor("#FF5252"))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val code = response.code
                val bodyStr = response.body?.string() ?: ""
                runOnUiThread {
                    if (response.isSuccessful) {
                        tvStatus.text = "✓ Connected Successfully! (HTTP $code)"
                        tvStatus.setTextColor(Color.parseColor("#00C853"))

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
                                Toast.makeText(this@OllamaSettingsActivity, "Loaded ${discovered.size} models! 🎯", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            // Valid response code is enough
                        }
                    } else if (code == 401) {
                        tvStatus.text = "HTTP 401: Invalid API Key or Unauthorized"
                        tvStatus.setTextColor(Color.parseColor("#FF5252"))
                    } else if (code == 404) {
                        tvStatus.text = "HTTP 404: Endpoint Not Found"
                        tvStatus.setTextColor(Color.parseColor("#FF5252"))
                    } else {
                        tvStatus.text = "Response: HTTP $code"
                        tvStatus.setTextColor(Color.parseColor("#FF5252"))
                    }
                }
            }
        })
    }

    private fun savePreferences() {
        val sp = getSharedPreferences("build_ai_prefs", Context.MODE_PRIVATE)
        val endpoint = etEndpoint.text.toString().trim()
        val apiKey = etApiKey.text.toString().trim()
        val model = etModelCustom.text.toString().trim().ifEmpty { "qwen2.5-coder:latest" }
        val prompt = etSystemPrompt.text.toString().trim()

        val clean = endpoint.trimEnd('/')
        val isOllama = clean.contains("11434") || clean.contains("ollama")
        val baseUrl = when {
            clean.endsWith("/chat/completions") -> clean
            clean.endsWith("/v1") -> "$clean/chat/completions"
            isOllama -> "$clean/v1/chat/completions"
            clean.isEmpty() -> "http://localhost:11434/v1/chat/completions"
            else -> "$clean/v1/chat/completions"
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
        finish(); Animatoo.animateSlideRight(this)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        Animatoo.animateSlideRight(this)
    }
}
