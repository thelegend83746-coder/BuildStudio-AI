package com.build.studio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.apk.builder.FileUtil
import com.blogspot.atifsoftwares.animatoolib.Animatoo
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class BuildAiActivity : AppCompatActivity() {

    enum class ActionType {
        WRITE_FILE,
        REPLACE_CODE,
        CREATE_DIR,
        RENAME,
        MOVE,
        DELETE
    }

    data class FileAction(
        val type: ActionType,
        val filePath: String,
        val code: String = "",
        val target: String = "",
        val replacement: String = "",
        val destPath: String = "",
        var applied: Boolean = false
    )

    data class ChatMessage(
        val type: Int, // 1 = User, 2 = Assistant
        var text: String,
        var plan: String? = null,
        var confidence: String? = null,
        val actions: MutableList<FileAction> = mutableListOf(),
        var isStreaming: Boolean = false
    )

    private lateinit var rvChat: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var layoutThinking: View
    private lateinit var tvThinkingStatus: TextView
    private lateinit var tvActiveModel: TextView
    private lateinit var chatAdapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()

    private var projectPath: String? = null
    private var activeFilePath: String? = null
    private var activeCall: Call? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val systemPrompt = """You are Build AI, an expert Android systems engineer and on-device IDE coding assistant inside the Build Studio app. You can create files, folders, write Java and XML code, fix compilation errors, and generate targeted smart patches.

CRITICAL RULES:
1. NEVER REWRITE ENTIRE FILES UNNECESSARILY: Output targeted patches using <replace_code> for specific XML attributes or Java method blocks to prevent breaking existing code.
2. MANDATORY EXECUTION PLAN & CONFIDENCE SCORE: For every coding task, you MUST output a <plan>...</plan> block explaining your diagnosis and planned edits, followed by <confidence>98%</confidence> (stating your estimated confidence score between 90% and 99%).
3. COMPILER ERROR AUTO-FIXER: When provided with a compiler error or stack trace, diagnose the root cause (e.g. AndroidX compatibility, missing view bindings, duplicate IDs, missing imports), patch ONLY the broken lines using <replace_code>, and preserve the rest of the file.

TAG FORMATS:
<plan>detailed analysis and targeted action steps</plan>
<confidence>98%</confidence>
<replace_code path="relative/path/to/file">
<target>exact existing lines to replace</target>
<replacement>exact replacement code</replacement>
</replace_code>
<write_file path="relative/path/to/file">full code (only for new files or when full rewrite is necessary)</write_file>
<create_dir path="relative/path/to/dir"/>
<rename path="relative/old/path" new_path="relative/new/path"/>
<move path="relative/old/path" dest_path="relative/new/path"/>
<delete path="relative/path/to/delete"/>
"""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.build_ai)

        projectPath = intent.getStringExtra("project_path")
        activeFilePath = intent.getStringExtra("active_file")

        rvChat = findViewById(R.id.chat_recycler) ?: findViewById(R.id.rv_chat)
        etMessage = findViewById(R.id.et_message)
        layoutThinking = findViewById(R.id.layout_status) ?: findViewById(R.id.layout_thinking)
        tvThinkingStatus = findViewById(R.id.tv_status_text) ?: findViewById(R.id.tv_thinking_status)
        tvActiveModel = findViewById(R.id.tv_model_info) ?: findViewById(R.id.tv_active_model)

        updateActiveModelDisplay()

        (findViewById<View>(R.id.back_btn) ?: findViewById<View>(R.id.btn_back))?.setOnClickListener {
            finish()
            Animatoo.animateSlideRight(this)
        }

        findViewById<View>(R.id.clear_chat_btn)?.setOnClickListener {
            confirmClearChat()
        }

        findViewById<View>(R.id.btn_stop)?.setOnClickListener {
            stopGeneration()
        }

        findViewById<View>(R.id.btn_ai_settings)?.setOnClickListener {
            SettingsBottomSheet.show(this)
        }

        chatAdapter = ChatAdapter()
        rvChat.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        rvChat.adapter = chatAdapter

        val sendButton = findViewById<View>(R.id.send_btn) ?: findViewById<View>(R.id.btn_send)
        sendButton?.setOnClickListener {
            val txt = etMessage.text.toString().trim()
            if (txt.isNotEmpty()) {
                sendMessage(txt)
            }
        }

        // Load persistent chat history from SQLite
        val p = projectPath ?: ""
        if (p.isNotEmpty()) {
            val savedMsgs = AiChatDbHelper.getInstance(this).loadMessages(p)
            if (savedMsgs.isNotEmpty()) {
                messages.addAll(savedMsgs)
                chatAdapter.notifyDataSetChanged()
                rvChat.scrollToPosition(messages.size - 1)
            }
        }

        if (messages.isEmpty()) {
            messages.add(
                ChatMessage(
                    type = 2,
                    text = "Hello! I am Build AI. I can generate code, fix compiler errors, and create Android features for your project. How can I help you today?"
                )
            )
            chatAdapter.notifyItemInserted(0)
        }

        // Auto-fixer intent payload support
        val prefillPrompt = intent.getStringExtra("prompt") ?: intent.getStringExtra("error")
        if (!prefillPrompt.isNullOrBlank()) {
            etMessage.setText(prefillPrompt)
            etMessage.postDelayed({
                sendMessage(prefillPrompt)
            }, 300)
        }
    }

    override fun onResume() {
        super.onResume()
        updateActiveModelDisplay()
    }

    private fun updateActiveModelDisplay() {
        val prefs = getSharedPreferences("build_ai_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("api_key", "") ?: ""
        val savedModel = prefs.getString("model_label", "")?.ifEmpty {
            prefs.getString("model_name", "")?.ifEmpty {
                prefs.getString("model", "")
            }
        } ?: ""

        val config = AiConfigHelper.resolveByModel(savedModel, apiKey)
        tvActiveModel?.text = "Build AI (${config.providerName} • ${config.defaultModel})"
    }

    private fun confirmClearChat() {
        AlertDialog.Builder(this)
            .setTitle("Clear Chat")
            .setMessage("Do you want to clear the conversation history?")
            .setPositiveButton("Clear") { _, _ ->
                messages.clear()
                val p = projectPath ?: ""
                if (p.isNotEmpty()) {
                    AiChatDbHelper.getInstance(this).clearMessages(p)
                }
                chatAdapter.notifyDataSetChanged()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun stopGeneration() {
        activeCall?.cancel()
        activeCall = null
        layoutThinking.visibility = View.GONE
        Toast.makeText(this, "Generation stopped", Toast.LENGTH_SHORT).show()
    }

    private fun sendMessage(userText: String) {
        messages.add(ChatMessage(type = 1, text = userText))
        val p = projectPath ?: ""
        if (p.isNotEmpty()) {
            AiChatDbHelper.getInstance(this).saveMessage(p, 1, userText)
        }
        chatAdapter.notifyItemInserted(messages.size - 1)
        rvChat.scrollToPosition(messages.size - 1)
        etMessage.setText("")

        val assistantMsg = ChatMessage(type = 2, text = "", isStreaming = true)
        messages.add(assistantMsg)
        val assistantIdx = messages.size - 1
        chatAdapter.notifyItemInserted(assistantIdx)
        rvChat.scrollToPosition(assistantIdx)

        layoutThinking.visibility = View.VISIBLE
        tvThinkingStatus?.text = "Build AI is thinking..."

        val contextPayload = buildAntiGravityContext(userText)

        val prefs = getSharedPreferences("build_ai_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("api_key", "") ?: ""
        val savedModel = prefs.getString("model_label", "")?.ifEmpty {
            prefs.getString("model_name", "")?.ifEmpty {
                prefs.getString("model", "")
            }
        } ?: ""

        // Resolve exact working baseUrl and compatible model based on user selection
        val config = AiConfigHelper.resolveByModel(savedModel, apiKey)
        var baseUrl = config.baseUrl
        val model = config.defaultModel

        if (config.providerName == "Google Gemini" && apiKey.isNotEmpty() && !baseUrl.contains("key=")) {
            baseUrl = if (baseUrl.contains("?")) "$baseUrl&key=$apiKey" else "$baseUrl?key=$apiKey"
        }

        val userInstructions = prefs.getString("system_prompt", "")?.trim()
        val finalSystemPrompt = if (!userInstructions.isNullOrEmpty()) {
            "$userInstructions\n\n$systemPrompt"
        } else {
            systemPrompt
        }

        val jsonBody = JSONObject().apply {
            put("model", model)
            val jsonMsgs = JSONArray()
            jsonMsgs.put(JSONObject().apply {
                put("role", "system")
                put("content", "$finalSystemPrompt\n\n$contextPayload")
            })

            // Conversation history: only prior non-empty messages
            // messages.size - 2 is current user message, messages.size - 1 is current streaming assistant
            val priorEnd = maxOf(0, messages.size - 2)
            val priorStart = maxOf(0, priorEnd - 6)
            for (i in priorStart until priorEnd) {
                val m = messages[i]
                if (m.text.isNotBlank()) {
                    jsonMsgs.put(JSONObject().apply {
                        put("role", if (m.type == 1) "user" else "assistant")
                        put("content", m.text)
                    })
                }
            }

            // Current user message added once
            jsonMsgs.put(JSONObject().apply {
                put("role", "user")
                put("content", userText)
            })

            put("messages", jsonMsgs)
            put("temperature", 0.3)
        }

        val reqBuilder = Request.Builder()
            .url(baseUrl)

        if (apiKey.isNotEmpty()) {
            reqBuilder.header("Authorization", "Bearer $apiKey")
        }

        val request = reqBuilder
            .post(jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .build()

        activeCall = client.newCall(request)
        activeCall?.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    layoutThinking.visibility = View.GONE
                    assistantMsg.isStreaming = false
                    val errDesc = if (apiKey.isEmpty()) {
                        "Cannot connect to local Ollama (127.0.0.1:11434). Please start Termux/Ollama or enter a DeepSeek / Gemini API key in Settings -> Build AI Settings."
                    } else {
                        "Connection failed: ${e.localizedMessage ?: "Network unreachable"}. Please check your connection or API key."
                    }
                    assistantMsg.text = errDesc
                    chatAdapter.notifyItemChanged(assistantIdx)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val respCode = response.code
                val respBody = response.body?.string() ?: ""
                val isSuccess = response.isSuccessful

                runOnUiThread {
                    layoutThinking.visibility = View.GONE
                    assistantMsg.isStreaming = false

                    if (!isSuccess) {
                        var errDetail = ""
                        try {
                            val errJson = JSONObject(respBody)
                            if (errJson.has("error")) {
                                val errObj = errJson.optJSONObject("error")
                                errDetail = errObj?.optString("message") ?: errJson.optString("error")
                            } else if (errJson.has("message")) {
                                errDetail = errJson.optString("message")
                            }
                        } catch (e: Exception) {
                            errDetail = respBody.take(250)
                        }

                        val errorDisplay = when (respCode) {
                            401 -> "Authentication failed (HTTP 401): Invalid API key for ${config.providerName}. Please check your key in Settings -> Build AI Settings.\n$errDetail"
                            400 -> "Request error (HTTP 400): $errDetail"
                            404 -> "Not found (HTTP 404): Endpoint or model '$model' not found.\n$errDetail"
                            429 -> "Rate limit exceeded (HTTP 429): $errDetail"
                            else -> "Server error (HTTP $respCode): $errDetail"
                        }

                        assistantMsg.text = errorDisplay
                        chatAdapter.notifyItemChanged(assistantIdx)
                        return@runOnUiThread
                    }

                    try {
                        val json = JSONObject(respBody)
                        var replyText = ""

                        // 1. OpenAI / DeepSeek format: choices[0].message.content
                        val choices = json.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val msgObj = choices.optJSONObject(0)?.optJSONObject("message")
                            replyText = msgObj?.optString("content") ?: choices.optJSONObject(0)?.optString("text") ?: ""
                        }

                        // 2. Ollama /api/chat format: message.content
                        if (replyText.isEmpty()) {
                            replyText = json.optJSONObject("message")?.optString("content") ?: ""
                        }

                        // 3. Ollama /api/generate format: response
                        if (replyText.isEmpty()) {
                            replyText = json.optString("response")
                        }

                        // 4. Gemini format: candidates[0].content.parts[0].text
                        if (replyText.isEmpty()) {
                            val candidates = json.optJSONArray("candidates")
                            if (candidates != null && candidates.length() > 0) {
                                val parts = candidates.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                                if (parts != null && parts.length() > 0) {
                                    replyText = parts.optJSONObject(0)?.optString("text") ?: ""
                                }
                            }
                        }

                        if (replyText.isEmpty()) {
                            replyText = respBody
                        }

                        parseResponseIntoMessage(replyText, assistantMsg)
                        val p = projectPath ?: ""
                        if (p.isNotEmpty()) {
                            AiChatDbHelper.getInstance(this@BuildAiActivity).saveMessage(
                                p,
                                2,
                                assistantMsg.text,
                                assistantMsg.plan,
                                assistantMsg.confidence,
                                assistantMsg.actions
                            )
                        }
                        chatAdapter.notifyItemChanged(assistantIdx)
                        rvChat.scrollToPosition(assistantIdx)
                    } catch (e: Exception) {
                        parseResponseIntoMessage(respBody, assistantMsg)
                        val p = projectPath ?: ""
                        if (p.isNotEmpty()) {
                            AiChatDbHelper.getInstance(this@BuildAiActivity).saveMessage(
                                p,
                                2,
                                assistantMsg.text,
                                assistantMsg.plan,
                                assistantMsg.confidence,
                                assistantMsg.actions
                            )
                        }
                        chatAdapter.notifyItemChanged(assistantIdx)
                    }
                }
            }
        })
    }

    private fun buildAntiGravityContext(query: String): String {
        val sb = StringBuilder()
        sb.append("--- IDE ENVIRONMENT INFO ---\n")
        sb.append("Build Studio App: Offline IDE for mobile Android development.\n")
        sb.append("Note: The project uses LOCAL library imports instead of Gradle remote dependencies.\n")
        sb.append("Libraries are placed under 'app/Build/libs/<libName>/classes.jar'.\n\n")

        val root = projectPath
        if (root != null) {
            val rootDir = File(root)
            sb.append("--- PROJECT INFO ---\n")
            sb.append("Project Name: ${rootDir.name}\n")
            sb.append("Root path: $root\n\n")

            sb.append("--- KEY FILE PATHS ---\n")
            sb.append("AndroidManifest.xml  -> relative path: \"app/src/main/AndroidManifest.xml\"\n")
            sb.append("Java sources         -> relative path: \"app/src/main/java/<package>/<ClassName>.java\"\n")
            sb.append("Layout XML files     -> relative path: \"app/src/main/res/layout/<layout_name>.xml\"\n")
            sb.append("Values XML           -> relative path: \"app/src/main/res/values/strings.xml\"\n")
            sb.append("Drawable resources   -> relative path: \"app/src/main/res/drawable/<name>.xml\"\n\n")

            val manifest = File(rootDir, "app/src/main/AndroidManifest.xml")
            if (manifest.exists()) {
                sb.append("--- CRITICAL FILE: AndroidManifest.xml ---\n")
                sb.append(manifest.readText().take(2500)).append("\n\n")
            }

            val active = activeFilePath
            if (active != null && File(active).exists()) {
                val rel = File(active).relativeTo(rootDir).path
                sb.append("--- CURRENTLY OPEN FILE: $rel ---\n")
                sb.append(File(active).readText().take(3000)).append("\n\n")
            }

            sb.append("--- DIRECTORY STRUCTURE ---\n")
            getProjectStructure(rootDir, sb, 0)
        }
        return sb.toString()
    }

    private fun getProjectStructure(dir: File, sb: StringBuilder, depth: Int) {
        if (depth > 3) return
        val files = dir.listFiles() ?: return
        for (f in files.take(25)) {
            if (f.name == "build" || f.name == ".git") continue
            sb.append("  ".repeat(depth)).append(f.name).append(if (f.isDirectory) "/" else "").append("\n")
            if (f.isDirectory) {
                getProjectStructure(f, sb, depth + 1)
            }
        }
    }

    private fun parseResponseIntoMessage(raw: String, msg: ChatMessage) {
        var cleanText = raw

        // 1. Parse <plan>...</plan> and optional confidence attribute
        val planPattern = Pattern.compile("<plan(?:\\s+confidence=[\"']([^\"']+)[\"'])?>(.*?)</plan>", Pattern.DOTALL)
        val planMatcher = planPattern.matcher(cleanText)
        if (planMatcher.find()) {
            val confAttr = planMatcher.group(1)
            msg.plan = planMatcher.group(2)?.trim()
            if (!confAttr.isNullOrBlank()) {
                msg.confidence = if (confAttr.contains("%")) "Confidence: $confAttr" else "Confidence: $confAttr%"
            }
            cleanText = planMatcher.replaceAll("").trim()
        }

        // Parse explicit <confidence>...</confidence> if present
        val confPattern = Pattern.compile("<confidence>(.*?)</confidence>", Pattern.DOTALL)
        val confMatcher = confPattern.matcher(cleanText)
        if (confMatcher.find()) {
            val c = confMatcher.group(1)?.trim() ?: ""
            msg.confidence = if (c.startsWith("Confidence", ignoreCase = true)) c else "Confidence: $c"
            cleanText = confMatcher.replaceAll("").trim()
        }

        if (msg.confidence.isNullOrBlank() && !msg.plan.isNullOrBlank()) {
            msg.confidence = "Confidence: 98%"
        }

        // 2. Parse <write_file path="...">...</write_file>
        val writePattern = Pattern.compile("<write_file\\s+path=[\"']([^\"']+)[\"']>(.*?)</write_file>", Pattern.DOTALL)
        val writeMatcher = writePattern.matcher(cleanText)
        while (writeMatcher.find()) {
            val p = writeMatcher.group(1)?.trim() ?: ""
            val code = writeMatcher.group(2) ?: ""
            msg.actions.add(FileAction(ActionType.WRITE_FILE, filePath = p, code = code))
        }
        cleanText = writeMatcher.replaceAll("").trim()

        // 3. Parse <replace_code path="..."> <target>...</target> <replacement>...</replacement> </replace_code>
        val replacePattern = Pattern.compile(
            "<replace_code\\s+path=[\"']([^\"']+)[\"']>\\s*<target>(.*?)</target>\\s*<replacement>(.*?)</replacement>\\s*</replace_code>",
            Pattern.DOTALL
        )
        val replaceMatcher = replacePattern.matcher(cleanText)
        while (replaceMatcher.find()) {
            val p = replaceMatcher.group(1)?.trim() ?: ""
            val target = replaceMatcher.group(2) ?: ""
            val repl = replaceMatcher.group(3) ?: ""
            msg.actions.add(FileAction(ActionType.REPLACE_CODE, filePath = p, target = target, replacement = repl))
        }
        cleanText = replaceMatcher.replaceAll("").trim()

        // 4. Parse <create_dir path="..."/> or <create_dir path="...">...</create_dir>
        val dirPattern = Pattern.compile("<create_dir\\s+path=[\"']([^\"']+)[\"']\\s*(?:/>|>.*?</create_dir>)", Pattern.DOTALL)
        val dirMatcher = dirPattern.matcher(cleanText)
        while (dirMatcher.find()) {
            val p = dirMatcher.group(1)?.trim() ?: ""
            msg.actions.add(FileAction(ActionType.CREATE_DIR, filePath = p))
        }
        cleanText = dirMatcher.replaceAll("").trim()

        // 5. Parse <rename path="..." new_path="..."/>
        val renamePattern = Pattern.compile("<rename\\s+path=[\"']([^\"']+)[\"']\\s+(?:new_path|to)=[\"']([^\"']+)[\"']\\s*(?:/>|>.*?</rename>)", Pattern.DOTALL)
        val renameMatcher = renamePattern.matcher(cleanText)
        while (renameMatcher.find()) {
            val p = renameMatcher.group(1)?.trim() ?: ""
            val np = renameMatcher.group(2)?.trim() ?: ""
            msg.actions.add(FileAction(ActionType.RENAME, filePath = p, destPath = np))
        }
        cleanText = renameMatcher.replaceAll("").trim()

        // 6. Parse <move path="..." dest_path="..."/>
        val movePattern = Pattern.compile("<move\\s+path=[\"']([^\"']+)[\"']\\s+(?:dest_path|to)=[\"']([^\"']+)[\"']\\s*(?:/>|>.*?</move>)", Pattern.DOTALL)
        val moveMatcher = movePattern.matcher(cleanText)
        while (moveMatcher.find()) {
            val p = moveMatcher.group(1)?.trim() ?: ""
            val dp = moveMatcher.group(2)?.trim() ?: ""
            msg.actions.add(FileAction(ActionType.MOVE, filePath = p, destPath = dp))
        }
        cleanText = moveMatcher.replaceAll("").trim()

        // 7. Parse <delete path="..."/>
        val deletePattern = Pattern.compile("<delete\\s+path=[\"']([^\"']+)[\"']\\s*(?:/>|>.*?</delete>)", Pattern.DOTALL)
        val deleteMatcher = deletePattern.matcher(cleanText)
        while (deleteMatcher.find()) {
            val p = deleteMatcher.group(1)?.trim() ?: ""
            msg.actions.add(FileAction(ActionType.DELETE, filePath = p))
        }
        cleanText = deleteMatcher.replaceAll("").trim()

        // Fallback: Markdown code block parser with file path header if no actions found
        if (msg.actions.isEmpty()) {
            val codeBlockPattern = Pattern.compile("```(?:xml|java|kt|gradle|json)?\\s*(?://|<!--|#)\\s*(?:File:\\s*)?([a-zA-Z0-9_/.-]+\\.[a-zA-Z0-9]+)\\s*(?:-->)?\n([\\s\\S]*?)```")
            val cbMatcher = codeBlockPattern.matcher(cleanText)
            while (cbMatcher.find()) {
                val p = cbMatcher.group(1)?.trim() ?: ""
                val code = cbMatcher.group(2) ?: ""
                msg.actions.add(FileAction(ActionType.WRITE_FILE, filePath = p, code = code))
            }
        }

        msg.text = cleanText
    }

    inner class ChatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(position: Int): Int = messages[position].type

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == 1) {
                UserViewHolder(inflater.inflate(R.layout.chat_item_user, parent, false))
            } else {
                AssistantViewHolder(inflater.inflate(R.layout.chat_item_assistant, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val msg = messages[position]
            if (holder is UserViewHolder) {
                holder.tvText.text = msg.text
            } else if (holder is AssistantViewHolder) {
                holder.tvText.text = msg.text

                // Plan Card with Confidence Score
                if (!msg.plan.isNullOrBlank()) {
                    holder.containerPlan.visibility = View.VISIBLE
                    holder.containerPlan.removeAllViews()
                    val planCard = LayoutInflater.from(this@BuildAiActivity).inflate(R.layout.chat_plan_card, holder.containerPlan, false)
                    val tvPlanText = planCard.findViewById<TextView>(R.id.tv_plan_text) ?: planCard.findViewById<TextView>(R.id.tv_plan_body)
                    val tvConfidence = planCard.findViewById<TextView>(R.id.tv_confidence_score)
                    tvPlanText?.text = msg.plan
                    tvConfidence?.text = msg.confidence ?: "Confidence: 98%"
                    holder.containerPlan.addView(planCard)
                } else {
                    holder.containerPlan.visibility = View.GONE
                }

                // Action Cards
                holder.containerActions.removeAllViews()
                if (msg.actions.isNotEmpty()) {
                    holder.containerActions.visibility = View.VISIBLE
                    for (act in msg.actions) {
                        val card = LayoutInflater.from(this@BuildAiActivity).inflate(R.layout.chat_action_card, holder.containerActions, false)
                        val tvType = card.findViewById<TextView>(R.id.tv_action_type)
                        val tvPath = card.findViewById<TextView>(R.id.tv_file_path) ?: card.findViewById<TextView>(R.id.tv_action_path)
                        val tvDesc = card.findViewById<TextView>(R.id.tv_action_desc)
                        val tvCodePreview = card.findViewById<TextView>(R.id.tv_code_preview)
                        val scrollPreview = card.findViewById<View>(R.id.scroll_code_preview)
                        val layoutButtons = card.findViewById<View>(R.id.layout_buttons)
                        val btnApprove = card.findViewById<Button>(R.id.btn_approve) ?: card.findViewById<Button>(R.id.btn_apply_action)
                        val btnReject = card.findViewById<Button>(R.id.btn_reject)
                        val tvStatus = card.findViewById<TextView>(R.id.tv_status)

                        tvPath?.text = act.filePath

                        when (act.type) {
                            ActionType.WRITE_FILE -> {
                                tvType?.text = "CREATE / WRITE FILE"
                                tvDesc?.text = "Write code to ${act.filePath}"
                                tvCodePreview?.text = act.code.trim()
                                scrollPreview?.visibility = if (act.code.isNotBlank()) View.VISIBLE else View.GONE
                            }
                            ActionType.REPLACE_CODE -> {
                                tvType?.text = "UPDATE CODE"
                                tvDesc?.text = "Update code in ${act.filePath}"
                                tvCodePreview?.text = "<<<<<<< TARGET\n${act.target.trim()}\n=======\n${act.replacement.trim()}\n>>>>>>> REPLACEMENT"
                                scrollPreview?.visibility = View.VISIBLE
                            }
                            ActionType.CREATE_DIR -> {
                                tvType?.text = "CREATE FOLDER"
                                tvDesc?.text = "Create folder: ${act.filePath}"
                                tvCodePreview?.text = "mkdir -p ${act.filePath}"
                                scrollPreview?.visibility = View.VISIBLE
                            }
                            ActionType.RENAME -> {
                                tvType?.text = "RENAME"
                                tvDesc?.text = "Rename to: ${act.destPath}"
                                tvCodePreview?.text = "${act.filePath}  ->  ${act.destPath}"
                                scrollPreview?.visibility = View.VISIBLE
                            }
                            ActionType.MOVE -> {
                                tvType?.text = "MOVE"
                                tvDesc?.text = "Move to: ${act.destPath}"
                                tvCodePreview?.text = "${act.filePath}  ->  ${act.destPath}"
                                scrollPreview?.visibility = View.VISIBLE
                            }
                            ActionType.DELETE -> {
                                tvType?.text = "DELETE"
                                tvDesc?.text = "Delete: ${act.filePath}"
                                tvCodePreview?.text = "rm -rf ${act.filePath}"
                                scrollPreview?.visibility = View.VISIBLE
                            }
                        }

                        if (act.applied) {
                            layoutButtons?.visibility = View.GONE
                            tvStatus?.visibility = View.VISIBLE
                        } else {
                            layoutButtons?.visibility = View.VISIBLE
                            tvStatus?.visibility = View.GONE

                            btnApprove?.setOnClickListener {
                                executeAction(act)
                                act.applied = true
                                layoutButtons?.visibility = View.GONE
                                tvStatus?.visibility = View.VISIBLE
                            }

                            btnReject?.setOnClickListener {
                                holder.containerActions.removeView(card)
                                Toast.makeText(this@BuildAiActivity, "Action rejected", Toast.LENGTH_SHORT).show()
                            }
                        }
                        holder.containerActions.addView(card)
                    }
                } else {
                    holder.containerActions.visibility = View.GONE
                }
            }
        }

        override fun getItemCount(): Int = messages.size

        inner class UserViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvText: TextView = v.findViewById(R.id.tv_message) ?: v.findViewById(R.id.tv_user_text)
        }

        inner class AssistantViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvText: TextView = v.findViewById(R.id.tv_message) ?: v.findViewById(R.id.tv_assistant_text)
            val containerPlan: LinearLayout = v.findViewById(R.id.container_plan) ?: v.findViewById(R.id.container_actions)
            val containerActions: LinearLayout = v.findViewById(R.id.container_actions)
        }
    }

    private fun executeAction(act: FileAction) {
        val root = projectPath
        if (root.isNullOrEmpty()) {
            Toast.makeText(this, "Error: No project open to apply action", Toast.LENGTH_SHORT).show()
            return
        }
        val targetFile = File(root, act.filePath)

        try {
            when (act.type) {
                ActionType.WRITE_FILE -> {
                    targetFile.parentFile?.mkdirs()
                    backupFile(targetFile)
                    FileUtil.writeFile(targetFile.absolutePath, act.code)
                    Toast.makeText(this, "Applied action: ${act.filePath}", Toast.LENGTH_SHORT).show()
                }
                ActionType.REPLACE_CODE -> {
                    if (targetFile.exists()) {
                        backupFile(targetFile)
                        val current = FileUtil.readFile(targetFile.absolutePath)
                        if (current.contains(act.target)) {
                            val updated = current.replace(act.target, act.replacement)
                            FileUtil.writeFile(targetFile.absolutePath, updated)
                            Toast.makeText(this, "Applied action: ${act.filePath}", Toast.LENGTH_SHORT).show()
                        } else {
                            val targetTrimmed = act.target.trim()
                            if (current.contains(targetTrimmed)) {
                                val updated = current.replace(targetTrimmed, act.replacement.trim())
                                FileUtil.writeFile(targetFile.absolutePath, updated)
                                Toast.makeText(this, "Applied action: ${act.filePath}", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "Target code not found in ${targetFile.name}", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        Toast.makeText(this, "File not found: ${act.filePath}", Toast.LENGTH_LONG).show()
                    }
                }
                ActionType.CREATE_DIR -> {
                    targetFile.mkdirs()
                    Toast.makeText(this, "Applied action: ${act.filePath}", Toast.LENGTH_SHORT).show()
                }
                ActionType.RENAME -> {
                    val newFile = File(root, act.destPath)
                    newFile.parentFile?.mkdirs()
                    targetFile.renameTo(newFile)
                    Toast.makeText(this, "Applied action: ${act.filePath}", Toast.LENGTH_SHORT).show()
                }
                ActionType.MOVE -> {
                    val destFile = File(root, act.destPath)
                    destFile.parentFile?.mkdirs()
                    targetFile.renameTo(destFile)
                    Toast.makeText(this, "Applied action: ${act.filePath}", Toast.LENGTH_SHORT).show()
                }
                ActionType.DELETE -> {
                    if (targetFile.exists()) {
                        backupFile(targetFile)
                        deleteRecursive(targetFile)
                        Toast.makeText(this, "Applied action: ${act.filePath}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "File not found: ${act.filePath}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Action error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun backupFile(f: File) {
        if (!f.exists()) return
        val root = projectPath ?: return
        val backupDir = File(root, ".build_ai_backups")
        backupDir.mkdirs()
        val bkp = File(backupDir, "${f.name}.${System.currentTimeMillis()}.bak")
        FileUtil.copyFile(f, bkp)
    }

    private fun deleteRecursive(fileOrDir: File) {
        if (fileOrDir.isDirectory) {
            fileOrDir.listFiles()?.forEach { deleteRecursive(it) }
        }
        fileOrDir.delete()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        Animatoo.animateSlideRight(this)
    }
}
