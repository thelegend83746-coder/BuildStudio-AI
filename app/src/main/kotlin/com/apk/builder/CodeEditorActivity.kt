package com.apk.builder

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.apk.builder.logger.Logger
import com.apk.builder.model.Project
import com.build.studio.BuildAiActivity
import com.build.studio.BuildSettingsActivity
import com.build.studio.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.tabs.TabLayout
import com.tyron.compiler.CompilerAsyncTask
import com.tyron.compiler.CompilerResult
import io.github.rosemoe.sora.widget.CodeEditor
import java.io.File

class CodeEditorActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var codeEditor: CodeEditor
    private lateinit var tabLayout: TabLayout
    private lateinit var tvTitle: TextView
    private lateinit var tvCursorPos: TextView
    private lateinit var tvSheetTitle: TextView
    private lateinit var tvSheetLog: TextView
    private lateinit var svSheetLog: ScrollView
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var rvFileTree: RecyclerView

    private lateinit var pullDownSymbolBar: LinearLayout
    private lateinit var btnToggleSymbolBar: ImageView
    private lateinit var llSymbolsContainer: LinearLayout
    private var isSymbolBarVisible = true

    private lateinit var currentProject: Project
    private var activeFile: File? = null
    private val openTabs = mutableListOf<File>()
    private val fileContentCache = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_code_editor)
        overridePendingTransition(R.anim.animate_slide_left_enter, R.anim.animate_slide_left_exit)

        val path = intent.getStringExtra("project_path")
        val name = intent.getStringExtra("project_name")
        val pkg = intent.getStringExtra("package_name")

        if (path == null) {
            Toast.makeText(this, "No project path provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        currentProject = Project(name ?: "Project", pkg ?: "com.example", path)

        initViews()
        setupEditor()
        setupSymbolBar()
        setupBottomSheet()
        setupTree()

        openDefaultFile()
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id._drawer)
        codeEditor = findViewById(R.id.code_editor)
        tabLayout = findViewById(R.id.tab_layout)
        tvTitle = findViewById(R.id.tv_title)
        tvCursorPos = findViewById(R.id.tv_cursor_pos)
        rvFileTree = findViewById(R.id.rv_file_tree)

        tvTitle.text = currentProject.name

        findViewById<View>(R.id.btn_drawer).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
        findViewById<View>(R.id.btn_undo).setOnClickListener {
            if (codeEditor.canUndo()) codeEditor.undo()
        }
        findViewById<View>(R.id.btn_redo).setOnClickListener {
            if (codeEditor.canRedo()) codeEditor.redo()
        }
        findViewById<View>(R.id.btn_search)?.setOnClickListener {
            DialogUtil.showSearchReplaceDialog(this, codeEditor)
        }
        findViewById<View>(R.id.btn_top_ai_editor)?.setOnClickListener {
            val intent = Intent(this, BuildAiActivity::class.java).apply {
                putExtra("project_path", currentProject.rootPath)
                putExtra("active_file", activeFile?.absolutePath)
            }
            startActivity(intent)
            overridePendingTransition(R.anim.animate_slide_left_enter, R.anim.animate_slide_left_exit)
        }
        findViewById<View>(R.id.btn_run).setOnClickListener {
            saveCurrentFile()
            runBuildPipeline()
        }
        findViewById<View>(R.id.btn_more)?.setOnClickListener { v ->
            showMorePopupMenu(v)
        }

        // Bottom handle bar actions
        findViewById<View>(R.id.btn_action_save)?.setOnClickListener {
            saveCurrentFile()
            Toast.makeText(this, "File saved", Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.btn_action_select_all)?.setOnClickListener {
            codeEditor.selectAll()
        }
        findViewById<View>(R.id.btn_action_copy)?.setOnClickListener {
            codeEditor.copyText()
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.btn_action_paste)?.setOnClickListener {
            codeEditor.pasteText()
        }

        // Drawer buttons
        findViewById<View>(R.id.btn_new_file)?.setOnClickListener {
            showNewFileDialog()
        }
        findViewById<View>(R.id.btn_refresh_tree)?.setOnClickListener {
            setupTree()
            Toast.makeText(this, "File tree refreshed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupEditor() {
        val prefs = getSharedPreferences("build_studio_settings", Context.MODE_PRIVATE)
        val fontSize = prefs.getInt("editor_font_size", 14).toFloat()
        val wordWrap = prefs.getBoolean("editor_word_wrap", false)

        codeEditor.setTextSize(fontSize)
        codeEditor.isWordwrap = wordWrap
        codeEditor.isLineNumberEnabled = true

        codeEditor.subscribeEvent(io.github.rosemoe.sora.event.SelectionChangeEvent::class.java) { _, _ ->
            val cursor = codeEditor.cursor
            tvCursorPos.text = "Ln ${cursor.leftLine + 1}, Col ${cursor.leftColumn + 1}"
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val file = tab.tag as? File
                if (file != null && file != activeFile) {
                    saveCurrentFile()
                    switchToFile(file)
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun setupSymbolBar() {
        pullDownSymbolBar = findViewById(R.id.pull_down_symbol_bar)
        btnToggleSymbolBar = findViewById(R.id.btn_toggle_symbol_bar)
        llSymbolsContainer = findViewById(R.id.ll_symbols_container)

        val symbols = SymbolLayout.DEFAULT_SYMBOLS
        llSymbolsContainer.removeAllViews()
        for (sym in symbols) {
            val chip = TextView(this).apply {
                text = sym
                textSize = 15f
                setTextColor(Color.WHITE)
                val pad = LayoutToolKit.dpToPx(context, 10f)
                val padV = LayoutToolKit.dpToPx(context, 6f)
                setPadding(pad, padV, pad, padV)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(LayoutToolKit.dpToPx(context, 3f), 0, LayoutToolKit.dpToPx(context, 3f), 0)
                }
                layoutParams = lp
                background = LayoutToolKit.createRippleDrawable(
                    Color.parseColor("#1A222D"),
                    Color.parseColor("#2979FF"),
                    8f,
                    context
                )
                setOnClickListener {
                    codeEditor.insertText(sym, sym.length)
                }
            }
            llSymbolsContainer.addView(chip)
        }

        btnToggleSymbolBar.setOnClickListener {
            isSymbolBarVisible = !isSymbolBarVisible
            findViewById<View>(R.id.hsv_symbols).visibility = if (isSymbolBarVisible) View.VISIBLE else View.GONE
            btnToggleSymbolBar.rotation = if (isSymbolBarVisible) 0f else 180f
        }
    }

    private fun setupBottomSheet() {
        val sheetView = findViewById<View>(R.id.bottom_sheet_panel)
        bottomSheetBehavior = BottomSheetBehavior.from(sheetView).apply {
            peekHeight = 0
            state = BottomSheetBehavior.STATE_COLLAPSED
        }

        tvSheetTitle = findViewById(R.id.tv_sheet_title)
        tvSheetLog = findViewById(R.id.tv_sheet_log)
        svSheetLog = findViewById(R.id.sv_sheet_log)

        findViewById<View>(R.id.btn_sheet_close).setOnClickListener {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }

        findViewById<View>(R.id.btn_copy_terminal_log).setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Terminal Log", tvSheetLog.text))
            Toast.makeText(this, "Logs copied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupTree() {
        rvFileTree.layoutManager = LinearLayoutManager(this)
        val rootDir = File(currentProject.rootPath)
        val nodes = mutableListOf<FileNode>()
        buildFileNodes(rootDir, nodes, 0)
        rvFileTree.adapter = FileTreeAdapter(nodes)
    }

    data class FileNode(
        val file: File,
        val depth: Int,
        val isDirectory: Boolean,
        var isExpanded: Boolean = true
    )

    private fun buildFileNodes(dir: File, list: MutableList<FileNode>, depth: Int) {
        val files = dir.listFiles() ?: return
        val sorted = files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        for (f in sorted) {
            // Ignore build output noise
            if (f.name == "build" || f.name == ".git") continue
            list.add(FileNode(f, depth, f.isDirectory))
            if (f.isDirectory) {
                buildFileNodes(f, list, depth + 1)
            }
        }
    }

    inner class FileTreeAdapter(private val nodes: List<FileNode>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(position: Int): Int = if (nodes[position].isDirectory) 1 else 2

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == 1) {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_file_tree_dir, parent, false)
                DirViewHolder(v)
            } else {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_file_tree_file, parent, false)
                FileViewHolder(v)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val node = nodes[position]
            val indent = LayoutToolKit.dpToPx(this@CodeEditorActivity, (node.depth * 16 + 12).toFloat())

            if (holder is DirViewHolder) {
                holder.tvName.text = node.file.name
                holder.itemView.setPadding(indent, holder.itemView.paddingTop, holder.itemView.paddingRight, holder.itemView.paddingBottom)
                holder.itemView.setOnClickListener {
                    Toast.makeText(this@CodeEditorActivity, node.file.name, Toast.LENGTH_SHORT).show()
                }
            } else if (holder is FileViewHolder) {
                holder.tvName.text = node.file.name
                holder.itemView.setPadding(indent, holder.itemView.paddingTop, holder.itemView.paddingRight, holder.itemView.paddingBottom)
                holder.itemView.setOnClickListener {
                    openFileInEditor(node.file)
                    drawerLayout.closeDrawer(GravityCompat.START)
                }
            }
        }

        override fun getItemCount(): Int = nodes.size

        inner class DirViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvName: TextView = v.findViewById(R.id.tv_dir_name)
        }

        inner class FileViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvName: TextView = v.findViewById(R.id.tv_file_name)
        }
    }

    private fun openDefaultFile() {
        // Try finding MainActivity.kt or MainActivity.java
        val srcDir = currentProject.srcDir
        var defaultFile: File? = null

        fun findMain(dir: File) {
            val children = dir.listFiles() ?: return
            for (c in children) {
                if (c.isDirectory) findMain(c)
                else if (c.name.startsWith("MainActivity")) {
                    defaultFile = c
                    return
                }
            }
        }

        if (srcDir.exists()) findMain(srcDir)

        if (defaultFile == null) {
            val manifest = currentProject.manifestFile
            if (manifest.exists()) defaultFile = manifest
        }

        if (defaultFile != null) {
            openFileInEditor(defaultFile!!)
        }
    }

    private fun openFileInEditor(file: File) {
        if (!file.exists()) return

        var tabIndex = -1
        for (i in 0 until tabLayout.tabCount) {
            val tab = tabLayout.getTabAt(i)
            if (tab?.tag == file) {
                tabIndex = i
                break
            }
        }

        if (tabIndex != -1) {
            tabLayout.getTabAt(tabIndex)?.select()
        } else {
            val tab = tabLayout.newTab().apply {
                text = file.name
                tag = file
            }
            tabLayout.addTab(tab)
            openTabs.add(file)
            tab.select()
        }

        switchToFile(file)
    }

    private fun switchToFile(file: File) {
        activeFile = file
        val content = fileContentCache[file.absolutePath] ?: FileUtil.readFile(file.absolutePath)
        fileContentCache[file.absolutePath] = content
        codeEditor.setText(content)
        tvTitle.text = "${currentProject.name} — ${file.name}"
    }

    private fun saveCurrentFile() {
        val f = activeFile ?: return
        val text = codeEditor.text.toString()
        fileContentCache[f.absolutePath] = text
        FileUtil.writeFile(f.absolutePath, text)
    }

    private fun runBuildPipeline() {
        tvSheetTitle.text = "Building APK..."
        tvSheetLog.text = ""
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED

        Logger.setListener { line ->
            runOnUiThread {
                tvSheetLog.append(line + "\n")
                svSheetLog.post { svSheetLog.fullScroll(View.FOCUS_DOWN) }
            }
        }

        val task = CompilerAsyncTask(this, currentProject) { result ->
            runOnUiThread {
                if (result.isSuccess && result.apkFile != null) {
                    tvSheetTitle.text = "Build SUCCESS! 🚀"
                    DialogUtil.showApkUtilityDialog(this, result.apkFile, currentProject.name)
                } else {
                    tvSheetTitle.text = "Build FAILED ❌"
                    DialogUtil.showCompilerErrorDialog(
                        this,
                        result.errorMessage,
                        currentProject.rootPath,
                        activeFile?.absolutePath
                    )
                }
            }
        }
        task.execute()
    }

    private fun showMorePopupMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add("Build Studio AI Assistant")
        popup.menu.add("Settings")
        popup.menu.add("Close Project")

        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Build Studio AI Assistant" -> {
                    val intent = Intent(this, BuildAiActivity::class.java).apply {
                        putExtra("project_path", currentProject.rootPath)
                        putExtra("active_file", activeFile?.absolutePath)
                    }
                    startActivity(intent)
                    overridePendingTransition(R.anim.animate_slide_left_enter, R.anim.animate_slide_left_exit)
                    true
                }
                "Settings" -> {
                    startActivity(Intent(this, com.build.studio.SettingsMenuActivity::class.java))
                    true
                }
                "Close Project" -> {
                    finish()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showNewFileDialog() {
        val view = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_1, null)
        val et = EditText(this).apply {
            hint = "FileName.kt or layout.xml"
        }
        AlertDialog.Builder(this)
            .setTitle("Create New File")
            .setView(et)
            .setPositiveButton("Create") { _, _ ->
                val fileName = et.text.toString().trim()
                if (fileName.isNotEmpty()) {
                    val targetDir = if (fileName.endsWith(".xml")) {
                        File(currentProject.resDir, "layout")
                    } else {
                        currentProject.srcDir
                    }
                    targetDir.mkdirs()
                    val newFile = File(targetDir, fileName)
                    if (!newFile.exists()) {
                        FileUtil.writeFile(newFile.absolutePath, "// New file $fileName\n")
                        setupTree()
                        openFileInEditor(newFile)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
