package com.build.studio

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.blogspot.atifsoftwares.animatoolib.Animatoo
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class OllamaSettingsActivity : AppCompatActivity() {

    private lateinit var etApiKey: EditText
    private lateinit var etModelCustom: EditText
    private lateinit var etSystemPrompt: EditText
    private lateinit var spModels: Spinner
    private lateinit var tvStatus: TextView
    private val modelList = mutableListOf(
        "glm-4.6",
        "qwen2.5-coder:latest",
        "qwen2.5-coder:7b",
        "deepseek-coder",
        "codellama",
        "llama3"
    )
    private lateinit var modelAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.ollama_settings)

        val backBtn = findViewById<View>(R.id.back_btn) ?: findViewById<View>(R.id.btn_back)
        backBtn?.setOnClickListener { finish(); Animatoo.animateSlideRight(this) }

        etApiKey = findViewById(R.id.et_api_key)
        etModelCustom = findViewById(R.id.et_model_name) ?: findViewById(R.id.et_model_custom)
        etSystemPrompt = findViewById(R.id.et_system_prompt)
        spModels = findViewById(R.id.spinner_models) ?: findViewById(R.id.sp_models)
        tvStatus = findViewById(R.id.tv_conn_status) ?: findViewById(R.id.tv_connection_status)

        modelAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modelList)
        spModels.adapter = modelAdapter

        loadPreferences()

        val btnTest = findViewById<View>(R.id.btn_test_conn) ?: findViewById<View>(R.id.btn_test_connection)
        val btnSave = findViewById<View>(R.id.btn_save) ?: findViewById<View>(R.id.btn_save_settings)

        btnTest?.setOnClickListener { testConnection() }
        btnSave?.setOnClickListener { savePreferences() }
    }

    private fun loadPreferences() {
        val sp = getSharedPreferences("build_ai_prefs", Context.MODE_PRIVATE)
        etApiKey.setText(sp.getString("api_key", ""))
        val model = sp.getString("model", "glm-4.6") ?: "glm-4.6"
        etModelCustom.setText(model)
        etSystemPrompt.setText(sp.getString("system_prompt", "You are Build AI, an expert Android developer assistant."))

        val pos = modelList.indexOf(model)
        if (pos >= 0) spModels.setSelection(pos)

        spModels.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                etModelCustom.setText(modelList[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun testConnection() {
        tvStatus.text = "Testing connection..."
        val client = OkHttpClient()
        val apiKey = etApiKey.text.toString().trim()

        val req = Request.Builder()
            .url("https://open.bigmodel.cn/api/paas/v4/models")
            .apply {
                if (apiKey.isNotEmpty()) {
                    addHeader("Authorization", "Bearer $apiKey")
                }
            }
            .get()
            .build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    tvStatus.text = "Connection Failed: ${e.message}"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        tvStatus.text = "Connected Successfully! (HTTP ${response.code})"
                    } else {
                        tvStatus.text = "Response: HTTP ${response.code}"
                    }
                }
            }
        })
    }

    private fun savePreferences() {
        val sp = getSharedPreferences("build_ai_prefs", Context.MODE_PRIVATE)
        val model = etModelCustom.text.toString().trim().ifEmpty { "glm-4.6" }
        sp.edit()
            .putString("api_key", etApiKey.text.toString().trim())
            .putString("model", model)
            .putString("system_prompt", etSystemPrompt.text.toString())
            .apply()

        Toast.makeText(this, "AI Settings saved! 🚀", Toast.LENGTH_SHORT).show()
        finish(); Animatoo.animateSlideRight(this)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        Animatoo.animateSlideRight(this)
    }
}
