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
    private lateinit var spProvider: Spinner
    private lateinit var etModelCustom: EditText
    private lateinit var etSystemPrompt: EditText
    private lateinit var spModels: Spinner
    private lateinit var tvStatus: TextView
    private val modelList = mutableListOf<String>()
    private lateinit var modelAdapter: ArrayAdapter<String>
    private lateinit var providerAdapter: ArrayAdapter<String>
    private val providerList = AiConfigHelper.PROVIDERS
    private var isUserTypingModel = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.ollama_settings)

        val backBtn = findViewById<View>(R.id.back_btn) ?: findViewById<View>(R.id.btn_back)
        backBtn?.setOnClickListener { finish(); Animatoo.animateSlideRight(this) }

        etApiKey = findViewById(R.id.et_api_key)
        spProvider = findViewById(R.id.sp_provider)
        etModelCustom = findViewById(R.id.et_model_custom) ?: findViewById(R.id.et_model_name)
        etSystemPrompt = findViewById(R.id.et_system_prompt)
        spModels = findViewById(R.id.sp_models) ?: findViewById(R.id.spinner_models)
        tvStatus = findViewById(R.id.tv_connection_status) ?: findViewById(R.id.tv_conn_status)

        providerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, providerList)
        spProvider.adapter = providerAdapter

        modelAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modelList)
        spModels.adapter = modelAdapter

        loadPreferences()

        val btnTest = findViewById<View>(R.id.btn_test_connection) ?: findViewById<View>(R.id.btn_test_conn)
        val btnSave = findViewById<View>(R.id.btn_save_settings) ?: findViewById<View>(R.id.btn_save)

        btnTest?.setOnClickListener { testConnection() }
        btnSave?.setOnClickListener { savePreferences() }

        spProvider.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedProvider = providerList[position]
                val config = AiConfigHelper.getProviderConfigByName(selectedProvider, etApiKey.text.toString().trim())
                etApiKey.hint = "Enter ${config.providerName} Key (${config.keyPrefixHint})"

                modelList.clear()
                modelList.addAll(config.models)
                modelAdapter.notifyDataSetChanged()

                if (modelList.isNotEmpty()) {
                    spModels.setSelection(0)
                    etModelCustom.setText(AiConfigHelper.cleanModelId(modelList[0]))
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spModels.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position in modelList.indices && !isUserTypingModel) {
                    etModelCustom.setText(AiConfigHelper.cleanModelId(modelList[position]))
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadPreferences() {
        val sp = getSharedPreferences("build_ai_prefs", Context.MODE_PRIVATE)
        val savedKey = sp.getString("api_key", "") ?: ""
        val savedProvider = sp.getString("provider_name", "") ?: ""
        val savedModel = sp.getString("model_name", "")?.ifEmpty {
            sp.getString("model", "")
        } ?: ""
        val savedLabel = sp.getString("model_label", "") ?: ""

        etApiKey.setText(savedKey)

        var pIdx = providerList.indexOfFirst { it.contains(savedProvider, ignoreCase = true) }
        if (pIdx < 0 && savedModel.isNotEmpty()) {
            val detected = AiConfigHelper.resolveByModel(savedModel, savedKey)
            pIdx = providerList.indexOfFirst { it.contains(detected.providerName, ignoreCase = true) }
        }
        if (pIdx < 0) pIdx = 0
        spProvider.setSelection(pIdx)

        val config = AiConfigHelper.getProviderConfigByName(providerList[pIdx], savedKey)
        modelList.clear()
        modelList.addAll(config.models)

        if (savedLabel.isNotEmpty() && !modelList.contains(savedLabel)) {
            modelList.add(0, savedLabel)
        }
        modelAdapter.notifyDataSetChanged()

        val activeModel = savedModel.ifEmpty { config.defaultModel }
        etModelCustom.setText(AiConfigHelper.cleanModelId(activeModel))

        val pos = modelList.indexOfFirst { it.contains(activeModel, ignoreCase = true) }
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

    private fun testConnection() {
        tvStatus.text = "Testing connection..."
        tvStatus.setTextColor(Color.parseColor("#64748B"))

        val apiKey = etApiKey.text.toString().trim()
        val selectedProvider = providerList[spProvider.selectedItemPosition]
        val config = AiConfigHelper.getProviderConfigByName(selectedProvider, apiKey = apiKey)

        if (config.requiresKey && apiKey.isEmpty()) {
            tvStatus.text = "✗ ${config.providerName} requires an API key (${config.keyPrefixHint})"
            tvStatus.setTextColor(Color.parseColor("#FF5252"))
            Toast.makeText(this, "Please enter an API Key for ${config.providerName}", Toast.LENGTH_SHORT).show()
            return
        }

        val testUrl = if (config.providerName.contains("Google", ignoreCase = true) && apiKey.isNotEmpty()) {
            "${config.testUrl}?key=$apiKey"
        } else {
            config.testUrl
        }

        tvStatus.text = "Connecting to ${config.providerName} & fetching models..."
        tvStatus.setTextColor(Color.parseColor("#64748B"))

        val startTime = System.currentTimeMillis()
        val client = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()

        val reqBuilder = Request.Builder().url(testUrl).get()
        if (apiKey.isNotEmpty()) {
            reqBuilder.header("Authorization", "Bearer $apiKey")
            if (config.providerName.contains("Sarvam", ignoreCase = true)) {
                reqBuilder.header("api-subscription-key", apiKey)
            }
        }

        client.newCall(reqBuilder.build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    tvStatus.text = "✗ Connection failed: ${e.localizedMessage ?: "Unreachable"}"
                    tvStatus.setTextColor(Color.parseColor("#FF5252"))
                    Toast.makeText(this@OllamaSettingsActivity, "Connection error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val code = response.code
                val latency = System.currentTimeMillis() - startTime
                val bodyStr = response.body?.string() ?: ""

                runOnUiThread {
                    if (response.isSuccessful) {
                        val discovered = AiConfigHelper.parseModelsResponse(config.providerName, bodyStr)
                        if (discovered.isNotEmpty()) {
                            modelList.clear()
                            modelList.addAll(discovered)
                            modelAdapter.notifyDataSetChanged()
                            spModels.setSelection(0)
                            etModelCustom.setText(AiConfigHelper.cleanModelId(discovered[0]))
                        }

                        val freeCount = modelList.count { it.contains("FREE", ignoreCase = true) }
                        val paidCount = modelList.size - freeCount

                        tvStatus.text = "✓ ${config.providerName} Connected (${latency}ms, HTTP $code)! Found ${modelList.size} models ($freeCount Free, $paidCount Paid)"
                        tvStatus.setTextColor(Color.parseColor("#00C853"))
                        Toast.makeText(this@OllamaSettingsActivity, "${config.providerName} Connected! 🎯", Toast.LENGTH_SHORT).show()
                    } else {
                        val errDetail = when (code) {
                            400 -> "HTTP 400 (Invalid API Key for ${config.providerName})"
                            401 -> "HTTP 401 (Authentication Failed / Invalid Key for ${config.providerName})"
                            403 -> "HTTP 403 (Forbidden / Access Denied)"
                            404 -> "HTTP 404 (Endpoint Not Found)"
                            429 -> "HTTP 429 (Rate Limit / Quota Exceeded)"
                            else -> "HTTP $code: ${bodyStr.take(120)}"
                        }
                        tvStatus.text = "✗ ${config.providerName} Error: $errDetail"
                        tvStatus.setTextColor(Color.parseColor("#FF5252"))
                        Toast.makeText(this@OllamaSettingsActivity, "Verification failed: $errDetail", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    private fun savePreferences() {
        val sp = getSharedPreferences("build_ai_prefs", Context.MODE_PRIVATE)
        val apiKey = etApiKey.text.toString().trim()
        val customModel = etModelCustom.text.toString().trim()
        val prompt = etSystemPrompt.text.toString().trim()
        val selectedProvider = providerList[spProvider.selectedItemPosition]
        val config = AiConfigHelper.getProviderConfigByName(selectedProvider, apiKey = apiKey)

        val selectedModelFull = if (modelList.isNotEmpty() && spModels.selectedItemPosition in modelList.indices) {
            modelList[spModels.selectedItemPosition]
        } else {
            customModel.ifEmpty { config.defaultModel }
        }

        val cleanModel = AiConfigHelper.cleanModelId(customModel.ifEmpty { selectedModelFull })

        sp.edit()
            .putString("endpoint", config.baseUrl.substringBeforeLast("/chat/completions"))
            .putString("base_url", config.baseUrl)
            .putString("api_key", apiKey)
            .putString("provider_name", config.providerName)
            .putString("model", cleanModel)
            .putString("model_name", cleanModel)
            .putString("model_label", selectedModelFull)
            .putString("system_prompt", prompt)
            .apply()

        Toast.makeText(this, "AI Settings saved: ${config.providerName} ($cleanModel)! 🚀", Toast.LENGTH_SHORT).show()
        finish()
        Animatoo.animateSlideRight(this)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        Animatoo.animateSlideRight(this)
    }
}
