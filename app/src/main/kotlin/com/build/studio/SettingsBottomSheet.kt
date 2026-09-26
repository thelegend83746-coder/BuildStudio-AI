package com.build.studio

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayout
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class SettingsBottomSheet : BottomSheetDialogFragment() {

    private val supportedAiModels = listOf(
        "Google Gemini (gemini-1.5-flash)",
        "Google Gemini (gemini-2.0-flash)",
        "DeepSeek (deepseek-chat)",
        "DeepSeek Coder (deepseek-coder)",
        "Qwen-Coder (qwen-2.5-coder-32b)",
        "GLM-4.6 (Zhipu AI)",
        "GLM-4.7 (Zhipu AI)",
        "OpenAI (gpt-4o-mini)",
        "Groq (llama-3.3-70b)",
        "Local Ollama (qwen2.5-coder:latest)"
    )

    private val targetSdks = listOf("34", "33", "31", "30", "28")
    private val minSdks = listOf("21", "24", "26", "28", "30")
    private val tabSizes = listOf("4 spaces", "2 spaces")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_settings_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tabLayout = view.findViewById<TabLayout>(R.id.tab_layout_settings)
        val tabAi = view.findViewById<View>(R.id.tab_ai_settings)
        val tabApp = view.findViewById<View>(R.id.tab_app_settings)
        val tabEditor = view.findViewById<View>(R.id.tab_editor_settings)
        val tabBuild = view.findViewById<View>(R.id.tab_build_settings)
        val tabAbout = view.findViewById<View>(R.id.tab_about_settings)
        val btnClose = view.findViewById<View>(R.id.btn_close_sheet)

        btnClose?.setOnClickListener { dismiss() }

        // Setup Tabs
        tabLayout.addTab(tabLayout.newTab().setText("Build Studio AI"))
        tabLayout.addTab(tabLayout.newTab().setText("Application"))
        tabLayout.addTab(tabLayout.newTab().setText("Editor"))
        tabLayout.addTab(tabLayout.newTab().setText("Build & Run"))
        tabLayout.addTab(tabLayout.newTab().setText("About Us"))

        val tabViews = listOf(tabAi, tabApp, tabEditor, tabBuild, tabAbout)

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val pos = tab.position
                tabViews.forEachIndexed { index, v ->
                    v.visibility = if (index == pos) View.VISIBLE else View.GONE
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        // Setup Tab 1: AI Settings
        setupAiSettings(view)

        // Setup Tab 2: Application Settings
        setupApplicationSettings(view)

        // Setup Tab 3: Editor Settings
        setupEditorSettings(view)

        // Setup Tab 4: Build & Run Settings
        setupBuildRunSettings(view)

        // Setup Tab 5: About Us
        setupAboutUs(view)
    }

    private fun setupAiSettings(root: View) {
        val tilApiKey = root.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.til_sheet_api_key)
        val etApiKey = root.findViewById<EditText>(R.id.et_sheet_api_key)
        val etInstructions = root.findViewById<EditText>(R.id.et_sheet_instructions)
        val spProvider = root.findViewById<Spinner>(R.id.sp_sheet_provider)
        val spModels = root.findViewById<Spinner>(R.id.sp_sheet_models)
        val tvStatus = root.findViewById<TextView>(R.id.tv_sheet_conn_status)
        val btnTest = root.findViewById<Button>(R.id.btn_sheet_test_conn)
        val btnSave = root.findViewById<Button>(R.id.btn_sheet_save_ai)

        val providerList = AiConfigHelper.PROVIDERS
        val providerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, providerList)
        spProvider.adapter = providerAdapter

        val activeModelsList = mutableListOf<String>()
        val modelsAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, activeModelsList)
        spModels.adapter = modelsAdapter

        val sp = requireContext().getSharedPreferences("build_ai_prefs", Context.MODE_PRIVATE)

        // Load existing AI prefs
        val savedKey = sp.getString("api_key", "") ?: ""
        val savedProvider = sp.getString("provider_name", "") ?: ""
        val savedModel = sp.getString("model_name", "")?.ifEmpty { sp.getString("model", "") } ?: ""
        val savedLabel = sp.getString("model_label", "") ?: ""
        val savedInstructions = sp.getString("system_prompt", "")?.ifEmpty { sp.getString("system_instructions", "") } ?: ""

        etApiKey.setText(savedKey)
        etInstructions?.setText(savedInstructions)

        fun updateModelsForProvider(providerName: String, preserveModel: String = "") {
            val config = AiConfigHelper.getProviderConfigByName(providerName, etApiKey.text.toString().trim())
            tilApiKey?.hint = "Enter ${config.providerName} Key (${config.keyPrefixHint})"
            etApiKey.hint = null

            activeModelsList.clear()
            activeModelsList.addAll(config.models)
            modelsAdapter.notifyDataSetChanged()

            if (preserveModel.isNotEmpty()) {
                val idx = activeModelsList.indexOfFirst { it.contains(preserveModel, ignoreCase = true) }
                if (idx >= 0) spModels.setSelection(idx)
            } else if (activeModelsList.isNotEmpty()) {
                spModels.setSelection(0)
            }

            tvStatus.text = "Active: ${config.providerName} (${config.defaultModel})"
            tvStatus.setTextColor(Color.parseColor("#64748B"))
        }

        var providerIdx = providerList.indexOfFirst { it.contains(savedProvider, ignoreCase = true) }
        if (providerIdx < 0 && savedModel.isNotEmpty()) {
            val detected = AiConfigHelper.resolveByModel(savedModel, savedKey)
            providerIdx = providerList.indexOfFirst { it.contains(detected.providerName, ignoreCase = true) }
        }
        if (providerIdx < 0) providerIdx = 0
        spProvider.setSelection(providerIdx)
        updateModelsForProvider(providerList[providerIdx], savedLabel.ifEmpty { savedModel })

        spProvider.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateModelsForProvider(providerList[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnTest.setOnClickListener {
            val key = etApiKey.text.toString().trim()
            val selectedProvider = providerList[spProvider.selectedItemPosition]
            val config = AiConfigHelper.getProviderConfigByName(selectedProvider, apiKey = key)

            if (config.requiresKey && key.isEmpty()) {
                tvStatus.text = "✗ ${config.providerName} requires an API key (${config.keyPrefixHint})"
                tvStatus.setTextColor(Color.parseColor("#EF4444"))
                Toast.makeText(context, "Please enter an API Key for ${config.providerName}", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val testUrl = if (config.providerName.contains("Google", ignoreCase = true) && key.isNotEmpty()) {
                "${config.testUrl}?key=$key"
            } else {
                config.testUrl
            }

            tvStatus.text = "Connecting to ${config.providerName} & fetching models..."
            tvStatus.setTextColor(Color.parseColor("#64748B"))

            val startTime = System.currentTimeMillis()
            Thread {
                val client = OkHttpClient.Builder()
                    .connectTimeout(12, TimeUnit.SECONDS)
                    .readTimeout(12, TimeUnit.SECONDS)
                    .build()

                try {
                    val reqBuilder = Request.Builder().url(testUrl).get()
                    if (key.isNotEmpty()) {
                        reqBuilder.header("Authorization", "Bearer $key")
                        if (config.providerName.contains("Sarvam", ignoreCase = true)) {
                            reqBuilder.header("api-subscription-key", key)
                        }
                    }

                    val resp = client.newCall(reqBuilder.build()).execute()
                    val ok = resp.isSuccessful
                    val code = resp.code
                    val latency = System.currentTimeMillis() - startTime
                    val respBody = resp.body?.string() ?: ""

                    activity?.runOnUiThread {
                        if (ok) {
                            val discovered = AiConfigHelper.parseModelsResponse(config.providerName, respBody)
                            if (discovered.isNotEmpty()) {
                                activeModelsList.clear()
                                activeModelsList.addAll(discovered)
                                modelsAdapter.notifyDataSetChanged()
                                spModels.setSelection(0)
                            }

                            val freeCount = activeModelsList.count { it.contains("FREE", ignoreCase = true) }
                            val paidCount = activeModelsList.size - freeCount

                            tvStatus.text = "✓ ${config.providerName} Connected (${latency}ms, HTTP $code)! Found ${activeModelsList.size} models ($freeCount Free, $paidCount Paid)"
                            tvStatus.setTextColor(Color.parseColor("#10B981"))
                            Toast.makeText(context, "${config.providerName} Verified! 🚀", Toast.LENGTH_SHORT).show()
                        } else {
                            val detail = when (code) {
                                400 -> "HTTP 400 (Invalid API Key for ${config.providerName})"
                                401 -> "HTTP 401 (Authentication Failed / Invalid Key for ${config.providerName})"
                                403 -> "HTTP 403 (Forbidden / Access Denied)"
                                404 -> "HTTP 404 (Endpoint Not Found)"
                                429 -> "HTTP 429 (Rate Limit / Quota Exceeded)"
                                else -> "HTTP $code: ${respBody.take(120)}"
                            }
                            tvStatus.text = "✗ ${config.providerName} Error: $detail"
                            tvStatus.setTextColor(Color.parseColor("#EF4444"))
                            Toast.makeText(context, "Verification failed: $detail", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    activity?.runOnUiThread {
                        tvStatus.text = "✗ Connection failed to ${config.providerName}: ${e.localizedMessage ?: "Unreachable"}"
                        tvStatus.setTextColor(Color.parseColor("#EF4444"))
                        Toast.makeText(context, "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        }

        btnSave.setOnClickListener {
            val key = etApiKey.text.toString().trim()
            val selectedProvider = providerList[spProvider.selectedItemPosition]
            val config = AiConfigHelper.getProviderConfigByName(selectedProvider, apiKey = key)

            val selectedModelFull = if (activeModelsList.isNotEmpty() && spModels.selectedItemPosition in activeModelsList.indices) {
                activeModelsList[spModels.selectedItemPosition]
            } else {
                config.defaultModel
            }

            val cleanModel = AiConfigHelper.cleanModelId(selectedModelFull)
            val customInstructions = etInstructions?.text?.toString()?.trim() ?: ""

            sp.edit()
                .putString("api_key", key)
                .putString("endpoint", config.baseUrl.substringBeforeLast("/chat/completions"))
                .putString("base_url", config.baseUrl)
                .putString("model", cleanModel)
                .putString("model_name", cleanModel)
                .putString("model_label", selectedModelFull)
                .putString("provider_name", config.providerName)
                .putString("system_prompt", customInstructions)
                .putString("system_instructions", customInstructions)
                .apply()

            Toast.makeText(context, "Saved: ${config.providerName} ($cleanModel)! 🚀", Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }

    private fun setupApplicationSettings(root: View) {
        val sp = requireContext().getSharedPreferences("build_studio_settings", Context.MODE_PRIVATE)
        val tvCacheSize = root.findViewById<TextView>(R.id.tv_cache_size)
        val btnClearCache = root.findViewById<Button>(R.id.btn_clear_cache)
        val switchResetIndex = root.findViewById<Switch>(R.id.switch_reset_index)
        val switchTheme = root.findViewById<Switch>(R.id.switch_theme_mode)
        val tvThemeLabel = root.findViewById<TextView>(R.id.tv_theme_label)

        // Cache size calculation
        fun updateCacheText() {
            val cacheSize = getDirSize(requireContext().cacheDir)
            tvCacheSize.text = "Current Cache: ${String.format("%.2f MB", cacheSize.toDouble() / (1024 * 1024))}"
        }
        updateCacheText()

        btnClearCache.setOnClickListener {
            try {
                requireContext().cacheDir.deleteRecursively()
                requireContext().cacheDir.mkdirs()
                updateCacheText()
                Toast.makeText(context, "App Cache cleared successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error clearing cache: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        switchResetIndex.isChecked = sp.getBoolean("reset_project_index", false)
        switchResetIndex.setOnCheckedChangeListener { _, isChecked ->
            sp.edit().putBoolean("reset_project_index", isChecked).apply()
            Toast.makeText(context, if (isChecked) "Project index will reset on next launch" else "Project index cache preserved", Toast.LENGTH_SHORT).show()
        }

        val isDark = sp.getBoolean("dark_theme", true)
        switchTheme.isChecked = isDark
        tvThemeLabel.text = if (isDark) "Dark Theme" else "Light Theme"
        switchTheme.setOnCheckedChangeListener { _, checked ->
            sp.edit().putBoolean("dark_theme", checked).apply()
            tvThemeLabel.text = if (checked) "Dark Theme" else "Light Theme"
            AppCompatDelegate.setDefaultNightMode(if (checked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    private fun setupEditorSettings(root: View) {
        val sp = requireContext().getSharedPreferences("build_studio_settings", Context.MODE_PRIVATE)
        val tvFontSize = root.findViewById<TextView>(R.id.tv_sheet_font_size)
        val seekbarFontSize = root.findViewById<SeekBar>(R.id.seekbar_sheet_font_size)
        val spTabSize = root.findViewById<Spinner>(R.id.sp_sheet_tab_size)
        val switchWordWrap = root.findViewById<Switch>(R.id.switch_sheet_word_wrap)
        val switchLineNumbers = root.findViewById<Switch>(R.id.switch_sheet_line_numbers)

        // Font size (10sp to 24sp)
        val savedFontSize = sp.getInt("editor_font_size", 14).coerceIn(10, 24)
        seekbarFontSize.progress = savedFontSize - 10
        tvFontSize.text = "${savedFontSize}sp"

        seekbarFontSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val size = 10 + progress
                tvFontSize.text = "${size}sp"
                sp.edit().putInt("editor_font_size", size).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Tab Size
        val tabAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, tabSizes)
        spTabSize.adapter = tabAdapter
        val savedTabSize = sp.getInt("editor_tab_size", 4)
        spTabSize.setSelection(if (savedTabSize == 2) 1 else 0)
        spTabSize.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val chosen = if (position == 1) 2 else 4
                sp.edit().putInt("editor_tab_size", chosen).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Word Wrap
        switchWordWrap.isChecked = sp.getBoolean("editor_word_wrap", false)
        switchWordWrap.setOnCheckedChangeListener { _, isChecked ->
            sp.edit().putBoolean("editor_word_wrap", isChecked).apply()
        }

        // Line Numbers
        switchLineNumbers.isChecked = sp.getBoolean("editor_line_numbers", true)
        switchLineNumbers.setOnCheckedChangeListener { _, isChecked ->
            sp.edit().putBoolean("editor_line_numbers", isChecked).apply()
        }
    }

    private fun setupBuildRunSettings(root: View) {
        val sp = requireContext().getSharedPreferences("build_studio_settings", Context.MODE_PRIVATE)
        val spTargetSdk = root.findViewById<Spinner>(R.id.sp_sheet_target_sdk)
        val spMinSdk = root.findViewById<Spinner>(R.id.sp_sheet_min_sdk)

        val targetAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, targetSdks)
        spTargetSdk.adapter = targetAdapter
        val savedTarget = sp.getInt("target_sdk", 34).toString()
        val targetPos = targetSdks.indexOf(savedTarget)
        spTargetSdk.setSelection(if (targetPos >= 0) targetPos else 0)

        spTargetSdk.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val sdk = targetSdks[position].toIntOrNull() ?: 34
                sp.edit().putInt("target_sdk", sdk).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val minAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, minSdks)
        spMinSdk.adapter = minAdapter
        val savedMin = sp.getInt("min_sdk", 21).toString()
        val minPos = minSdks.indexOf(savedMin)
        spMinSdk.setSelection(if (minPos >= 0) minPos else 0)

        spMinSdk.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val sdk = minSdks[position].toIntOrNull() ?: 21
                sp.edit().putInt("min_sdk", sdk).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupAboutUs(root: View) {
        root.findViewById<View>(R.id.tv_doc_link)?.setOnClickListener {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/build-studio/documentation"))
                startActivity(browserIntent)
            } catch (_: Exception) {}
        }
    }

    private fun getDirSize(dir: File): Long {
        var size = 0L
        if (dir.exists()) {
            dir.walkTopDown().forEach { if (it.isFile) size += it.length() }
        }
        return size
    }

    companion object {
        const val TAG = "SettingsBottomSheet"

        fun show(activity: Activity) {
            val sheet = SettingsBottomSheet()
            if (activity is androidx.fragment.app.FragmentActivity) {
                sheet.show(activity.supportFragmentManager, TAG)
            }
        }
    }
}
