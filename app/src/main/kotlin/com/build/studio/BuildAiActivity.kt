package com.build.studio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

    private val systemPrompt = """You are Build AI, an expert Android developer and coding assistant inside the Build Studio app. You can create files, folders, write Java and XML code, fix compilation errors, and answer questions.

RULE 0 - MANDATORY PLAN BEFORE ACTING: Before any <write_file>, <replace_code>, <delete>, <rename>, <move>, or <create_dir> tag, you MUST write a <plan>...</plan> block. Inside it, in plain text, state: (1) the exact root cause, quoting the specific error line/number you are responding to, not a guess; (2) which file(s) you actually need to look at or change to fix it, and why those specific ones; (3) whether any existing file in the project is now unused or redundant because of this change; (4) the smallest action type you will use. Do not put code inside <plan>. Skip <plan> only for pure conversation with no file action.

RULE 1 - NO GUESSING: If you are not confident about the root cause or the fix, say so and ask for the specific file or information you need instead of guessing.

RULE 2 - FULL ANALYSIS BEFORE FIXING: Base your diagnosis on the complete file(s) provided, not a single line in isolation.

RULE 3 - SMALLEST EDIT FIRST: Prefer the smallest possible change. Use <replace_code> for a single line, block, or function instead of rewriting the whole file with <write_file>.

RULE 4 - DELETE BEFORE WRITE, ALWAYS: If a fix requires deleting a file and recreating it, you MUST emit the <delete> tag for that exact path BEFORE the <write_file> tag for the same path.

RULE 5 - DESTRUCTIVE ACTIONS NEED A REASON: Whenever you use <delete>, <rename>, or <move>, state in plain text in the <plan> block why it is necessary.

RULE 6 - STATE YOUR CONFIDENCE: State a confidence level (e.g. "Confidence: ~90%") inside the <plan> block.

RULE 7 - PLAIN TEXT FORMATTING: In your normal chat replies, do not use markdown symbols like **, __, backticks, or bullet dashes. Use simple numbered lines (1. 2. 3.).

TAG FORMATS:
<plan>analysis, affected files, confidence %, action type</plan>
<write_file path="relative/path/to/file">code</write_file>
<replace_code path="relative/path/to/file">
<target>exact lines to match</target>
<replacement>replacement code</replacement>
</replace_code>
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

        tvActiveModel?.text = "Build AI (DeepSeek / Gemini)"

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
            startActivity(Intent(this, OllamaSettingsActivity::class.java))
            Animatoo.animateSlideLeft(this)
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

        messages.add(
            ChatMessage(
                type = 2,
                text = "Hello! I am Build AI. I can generate code, fix compiler errors, and create Android features for your project. How can I help you today?"
            )
        )
        chatAdapter.notifyItemInserted(0)
    }

    private fun confirmClearChat() {
        AlertDialog.Builder(this)
            .setTitle("Clear Chat")
            .setMessage("Do you want to clear the conversation history?")
            .setPositiveButton("Clear") { _, _ ->
                messages.clear()
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
        val baseUrl = prefs.getString("base_url", "http://localhost:11434/v1/chat/completions")
            ?.ifEmpty { "http://localhost:11434/v1/chat/completions" } ?: "http://localhost:11434/v1/chat/completions"
        val model = prefs.getString("model_name", "")?.ifEmpty {
            prefs.getString("model", "qwen2.5-coder:latest")
        } ?: "qwen2.5-coder:latest"

        val jsonBody = JSONObject().apply {
            put("model", model)
            val jsonMsgs = JSONArray()
            jsonMsgs.put(JSONObject().apply {
                put("role", "system")
                put("content", "$systemPrompt\n\n$contextPayload")
            })
            // Pass last 4 messages for conversation continuity
            val startIdx = maxOf(0, messages.size - 6)
            for (i in startIdx until messages.size - 1) {
                val m = messages[i]
                jsonMsgs.put(JSONObject().apply {
                    put("role", if (m.type == 1) "user" else "assistant")
                    put("content", m.text)
                })
            }
            jsonMsgs.put(JSONObject().apply {
                put("role", "user")
                put("content", userText)
            })
            put("messages", jsonMsgs)
            put("temperature", 0.2)
        }

        val reqBuilder = Request.Builder()
            .url(baseUrl)
            .addHeader("Content-Type", "application/json")

        if (apiKey.isNotEmpty()) {
            reqBuilder.addHeader("Authorization", "Bearer $apiKey")
        }

        val request = reqBuilder
            .post(jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        activeCall = client.newCall(request)
        activeCall?.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    layoutThinking.visibility = View.GONE
                    assistantMsg.isStreaming = false
                    assistantMsg.text = "Error communicating with AI: ${e.message}"
                    chatAdapter.notifyItemChanged(assistantIdx)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val respBody = response.body?.string() ?: ""
                runOnUiThread {
                    layoutThinking.visibility = View.GONE
                    assistantMsg.isStreaming = false
                    try {
                        val json = JSONObject(respBody)
                        val choices = json.optJSONArray("choices")
                        val content = choices?.optJSONObject(0)?.optJSONObject("message")?.optString("content") ?: respBody
                        parseResponseIntoMessage(content, assistantMsg)
                        chatAdapter.notifyItemChanged(assistantIdx)
                        rvChat.scrollToPosition(assistantIdx)
                    } catch (e: Exception) {
                        parseResponseIntoMessage(respBody, assistantMsg)
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

        // 1. Parse <plan>...</plan>
        val planPattern = Pattern.compile("<plan>(.*?)</plan>", Pattern.DOTALL)
        val planMatcher = planPattern.matcher(cleanText)
        if (planMatcher.find()) {
            msg.plan = planMatcher.group(1)?.trim()
            cleanText = planMatcher.replaceAll("").trim()
        }

        // 2. Parse <write_file path="...">...</write_file>
        val writePattern = Pattern.compile("<write_file\\s+path=\"([^\"]+)\">(.*?)</write_file>", Pattern.DOTALL)
        val writeMatcher = writePattern.matcher(cleanText)
        while (writeMatcher.find()) {
            val p = writeMatcher.group(1)?.trim() ?: ""
            val code = writeMatcher.group(2) ?: ""
            msg.actions.add(FileAction(ActionType.WRITE_FILE, filePath = p, code = code))
        }
        cleanText = writeMatcher.replaceAll("").trim()

        // 3. Parse <replace_code path="..."> <target>...</target> <replacement>...</replacement> </replace_code>
        val replacePattern = Pattern.compile(
            "<replace_code\\s+path=\"([^\"]+)\">\\s*<target>(.*?)</target>\\s*<replacement>(.*?)</replacement>\\s*</replace_code>",
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

        // 4. Parse <create_dir path="..."/>
        val dirPattern = Pattern.compile("<create_dir\\s+path=\"([^\"]+)\"\\s*/>")
        val dirMatcher = dirPattern.matcher(cleanText)
        while (dirMatcher.find()) {
            val p = dirMatcher.group(1)?.trim() ?: ""
            msg.actions.add(FileAction(ActionType.CREATE_DIR, filePath = p))
        }
        cleanText = dirMatcher.replaceAll("").trim()

        // 5. Parse <rename path="..." new_path="..."/>
        val renamePattern = Pattern.compile("<rename\\s+path=\"([^\"]+)\"\\s+new_path=\"([^\"]+)\"\\s*/>")
        val renameMatcher = renamePattern.matcher(cleanText)
        while (renameMatcher.find()) {
            val p = renameMatcher.group(1)?.trim() ?: ""
            val np = renameMatcher.group(2)?.trim() ?: ""
            msg.actions.add(FileAction(ActionType.RENAME, filePath = p, destPath = np))
        }
        cleanText = renameMatcher.replaceAll("").trim()

        // 6. Parse <move path="..." dest_path="..."/>
        val movePattern = Pattern.compile("<move\\s+path=\"([^\"]+)\"\\s+dest_path=\"([^\"]+)\"\\s*/>")
        val moveMatcher = movePattern.matcher(cleanText)
        while (moveMatcher.find()) {
            val p = moveMatcher.group(1)?.trim() ?: ""
            val dp = moveMatcher.group(2)?.trim() ?: ""
            msg.actions.add(FileAction(ActionType.MOVE, filePath = p, destPath = dp))
        }
        cleanText = moveMatcher.replaceAll("").trim()

        // 7. Parse <delete path="..."/>
        val deletePattern = Pattern.compile("<delete\\s+path=\"([^\"]+)\"\\s*/>")
        val deleteMatcher = deletePattern.matcher(cleanText)
        while (deleteMatcher.find()) {
            val p = deleteMatcher.group(1)?.trim() ?: ""
            msg.actions.add(FileAction(ActionType.DELETE, filePath = p))
        }
        cleanText = deleteMatcher.replaceAll("").trim()

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

                // Plan Card
                if (!msg.plan.isNullOrBlank()) {
                    holder.containerPlan.visibility = View.VISIBLE
                    holder.containerPlan.removeAllViews()
                    val planCard = LayoutInflater.from(this@BuildAiActivity).inflate(R.layout.chat_plan_card, holder.containerPlan, false)
                    val tvPlanText = planCard.findViewById<TextView>(R.id.tv_plan_text) ?: planCard.findViewById<TextView>(R.id.tv_plan_body)
                    tvPlanText?.text = msg.plan
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
                        val tvPath = card.findViewById<TextView>(R.id.tv_action_path) ?: card.findViewById<TextView>(R.id.tv_file_path)
                        val layoutButtons = card.findViewById<View>(R.id.layout_buttons)
                        val btnApprove = card.findViewById<Button>(R.id.btn_approve) ?: card.findViewById<Button>(R.id.btn_apply_action)
                        val btnReject = card.findViewById<Button>(R.id.btn_reject)
                        val tvStatus = card.findViewById<TextView>(R.id.tv_status)

                        tvType.text = act.type.name.replace("_", " ")
                        tvPath.text = act.filePath

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
                                card.visibility = View.GONE
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
        val root = projectPath ?: return
        val targetFile = File(root, act.filePath)

        try {
            when (act.type) {
                ActionType.WRITE_FILE -> {
                    targetFile.parentFile?.mkdirs()
                    backupFile(targetFile)
                    FileUtil.writeFile(targetFile.absolutePath, act.code)
                    Toast.makeText(this, "Created/Updated ${targetFile.name}", Toast.LENGTH_SHORT).show()
                }
                ActionType.REPLACE_CODE -> {
                    if (targetFile.exists()) {
                        backupFile(targetFile)
                        val current = FileUtil.readFile(targetFile.absolutePath)
                        if (current.contains(act.target)) {
                            val updated = current.replace(act.target, act.replacement)
                            FileUtil.writeFile(targetFile.absolutePath, updated)
                            Toast.makeText(this, "Patched ${targetFile.name}", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Target code not found in ${targetFile.name}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                ActionType.CREATE_DIR -> {
                    targetFile.mkdirs()
                    Toast.makeText(this, "Directory created: ${targetFile.name}", Toast.LENGTH_SHORT).show()
                }
                ActionType.RENAME -> {
                    val newFile = File(root, act.destPath)
                    targetFile.renameTo(newFile)
                    Toast.makeText(this, "Renamed to ${newFile.name}", Toast.LENGTH_SHORT).show()
                }
                ActionType.MOVE -> {
                    val destFile = File(root, act.destPath)
                    destFile.parentFile?.mkdirs()
                    targetFile.renameTo(destFile)
                    Toast.makeText(this, "Moved to ${destFile.name}", Toast.LENGTH_SHORT).show()
                }
                ActionType.DELETE -> {
                    deleteRecursive(targetFile)
                    Toast.makeText(this, "Deleted ${targetFile.name}", Toast.LENGTH_SHORT).show()
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
