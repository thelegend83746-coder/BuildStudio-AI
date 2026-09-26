package com.build.studio

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.apk.builder.DialogUtil
import com.apk.builder.FileUtil
import com.apk.builder.model.Project
import com.blogspot.atifsoftwares.animatoolib.Animatoo
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.tyron.compiler.CompilerAsyncTask
import io.github.rosemoe.sora.langs.java.JavaLanguage
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.schemes.SchemeGitHub
import java.io.File
import kotlin.math.abs

class EditorActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var codeEditor: CodeEditor
    private lateinit var tabLayout: TabLayout
    private lateinit var tvPrjName: TextView
    private lateinit var rvFileTree: RecyclerView
    private lateinit var toolbarLayout: LinearLayout

    private lateinit var currentProject: Project
    private var activeFile: File? = null
    private var pendingEditLogoBitmap: Bitmap? = null
    private var ivCurrentEditingLogo: ImageView? = null
    private val openTabs = mutableListOf<File>()
    private val fileContentCache = mutableMapOf<String, String>()

    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var currentFontSize = 14f
    private val fileNodes = mutableListOf<FileNode>()
    private val expandedPaths = HashSet<String>()
    private lateinit var treeAdapter: TreeAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.editor)
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        currentProject = resolveProject()

        try {
            initViews()
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        try {
            setupEditor()
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        try {
            setupTree()
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        try {
            openDefaultFile()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun resolveProject(): Project {
        try {
            val path = intent.getStringExtra("project_path")
                ?: intent.getStringExtra("path")
                ?: intent.getStringExtra("fullPath")
                ?: intent.getStringExtra("projectPath")

            val name = intent.getStringExtra("project_name")
                ?: intent.getStringExtra("project")
                ?: intent.getStringExtra("application_name")
                ?: "MyApplication"

            val pkg = intent.getStringExtra("package_name") ?: "com.example.app"

            if (!path.isNullOrEmpty()) {
                val f = File(path)
                if (f.exists() && f.isDirectory) {
                    val p = Project.loadFromDirectory(f) ?: Project(name, pkg, f.absolutePath)
                    ensureProjectStructure(p)
                    return p
                }
            }

            // Search standard directory locations for existing projects
            val candidateDirs = listOf(
                File(filesDir, "projects"),
                File(filesDir, "projects/$name"),
                File("/storage/emulated/0/test-folder/projects"),
                File("/storage/emulated/0/test-folder/projects/$name"),
                File("/storage/emulated/0/.BUILD STUDIO"),
                File("/storage/emulated/0/.BUILD STUDIO/$name"),
                File(getExternalFilesDir(null), "projects")
            )

            for (dir in candidateDirs) {
                if (dir.exists() && dir.isDirectory) {
                    val directPrj = Project.loadFromDirectory(dir)
                    if (directPrj != null) {
                        ensureProjectStructure(directPrj)
                        return directPrj
                    }
                    val subs = dir.listFiles { f -> f.isDirectory } ?: continue
                    for (sub in subs) {
                        val p = Project.loadFromDirectory(sub)
                        if (p != null) {
                            ensureProjectStructure(p)
                            return p
                        }
                    }
                }
            }

            // Fallback: create fresh project in app-internal storage (guaranteed writable)
            val base = File(filesDir, "projects")
            base.mkdirs()
            val projectDir = File(base, name).apply { mkdirs() }
            val fallbackPrj = Project(name, pkg, projectDir.absolutePath)
            ensureProjectStructure(fallbackPrj)
            return fallbackPrj
        } catch (e: Throwable) {
            e.printStackTrace()
            val emergencyDir = File(filesDir, "projects/MyApplication").apply { mkdirs() }
            val emergencyPrj = Project("MyApplication", "com.example.app", emergencyDir.absolutePath)
            ensureProjectStructure(emergencyPrj)
            return emergencyPrj
        }
    }

    private fun ensureProjectStructure(project: Project) {
        try {
            val root = File(project.rootPath).apply { mkdirs() }
            val pkgClean = if (project.packageName.isNotEmpty()) project.packageName else "com.example.app"
            val pkgDir = pkgClean.replace('.', '/')
            val javaSrc = File(root, "app/src/main/java/$pkgDir").apply { mkdirs() }
            val resDir = File(root, "app/src/main/res").apply { mkdirs() }
            val layoutDir = File(resDir, "layout").apply { mkdirs() }
            val valuesDir = File(resDir, "values").apply { mkdirs() }

            val mainActivity = File(javaSrc, "MainActivity.java")
            if (!mainActivity.exists() || mainActivity.length() == 0L) {
                val javaBoilerplate = """package $pkgClean;

import android.app.Activity;
import android.os.Bundle;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
}
"""
                FileUtil.writeFile(mainActivity.absolutePath, javaBoilerplate)
            }

            val activityMain = File(layoutDir, "activity_main.xml")
            if (!activityMain.exists() || activityMain.length() == 0L) {
                val xmlBoilerplate = """<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:gravity="center"
    android:orientation="vertical">

    <TextView
        android:id="@+id/tv_welcome"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Welcome to ${project.name}!"
        android:textSize="20sp"
        android:textStyle="bold" />

</LinearLayout>
"""
                FileUtil.writeFile(activityMain.absolutePath, xmlBoilerplate)
            }

            val manifest = File(root, "app/src/main/AndroidManifest.xml")
            if (!manifest.exists() || manifest.length() == 0L) {
                val manifestContent = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="$pkgClean">

    <application
        android:allowBackup="true"
        android:label="${project.name}"
        android:theme="@style/AppTheme">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
"""
                FileUtil.writeFile(manifest.absolutePath, manifestContent)
            }

            val appGradle = File(root, "app/build.gradle")
            if (!appGradle.exists()) {
                FileUtil.writeFile(
                    appGradle.absolutePath,
                    """apply plugin: 'com.android.application'

android {
    compileSdkVersion 34
    defaultConfig {
        applicationId "$pkgClean"
        minSdkVersion ${project.minSdk}
        targetSdkVersion ${project.targetSdk}
        versionCode 1
        versionName "1.0"
    }
}
"""
                )
            }

            project.saveConfig()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id._drawer) ?: DrawerLayout(this)
        tabLayout = findViewById(R.id.tablayout1)
        tvPrjName = findViewById(R.id.prj_name)
        toolbarLayout = findViewById(R.id.toolbar) ?: LinearLayout(this)

        tvPrjName.text = currentProject.name

        // Initialize CodeEditor from XML layout with clean fallback
        val editorContainer = findViewById<FrameLayout>(R.id.editor_container)
        val xmlEditor = findViewById<CodeEditor>(R.id.code_editor)
        if (xmlEditor != null) {
            codeEditor = xmlEditor
        } else {
            codeEditor = CodeEditor(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            editorContainer?.removeAllViews()
            editorContainer?.addView(codeEditor)
        }

        // Bottom Symbol / Code Assist Toolbar
        val symbolLayout = findViewById<com.apk.builder.SymbolLayout>(R.id.symbol_layout)
        if (::codeEditor.isInitialized) {
            symbolLayout?.setTargetEditor(codeEditor)
        }

        // Top Bar Back button: automatically saves current file and exits
        findViewById<View>(R.id.drawer_toggle_btn)?.setOnClickListener {
            saveCurrentFile()
            finish()
            Animatoo.animateSlideDown(this)
        }

        // Undo button
        findViewById<View>(R.id.undo_btn)?.setOnClickListener {
            if (::codeEditor.isInitialized && codeEditor.canUndo()) {
                codeEditor.undo()
            } else {
                Toast.makeText(this, "Nothing to undo", Toast.LENGTH_SHORT).show()
            }
        }

        // Redo button
        findViewById<View>(R.id.redo_btn)?.setOnClickListener {
            if (::codeEditor.isInitialized && codeEditor.canRedo()) {
                codeEditor.redo()
            } else {
                Toast.makeText(this, "Nothing to redo", Toast.LENGTH_SHORT).show()
            }
        }

        // Save Button: explicitly saves active file with confirmation Toast
        findViewById<View>(R.id.btn_save)?.setOnClickListener {
            val f = activeFile
            if (f != null) {
                saveCurrentFile()
                Toast.makeText(this, "Saved ${f.name} ✓", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No file open to save", Toast.LENGTH_SHORT).show()
            }
        }

        // Folder button to toggle drawer
        findViewById<View>(R.id.imageview3)?.setOnClickListener {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                setupTree()
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }

        // Run button (Build TextView)
        findViewById<View>(R.id.Build)?.setOnClickListener {
            saveCurrentFile()
            runBuildPipeline()
        }

        // Overflow menu (more_popupo_menu.xml)
        findViewById<View>(R.id.menu)?.setOnClickListener { v ->
            showMorePopupMenu(v)
        }

        // Drawer views
        val drawerView = findViewById<View>(R.id.drawer) ?: findViewById<View>(R.id._nav_view)
        rvFileTree = drawerView?.findViewById(R.id.recyclerview1)
            ?: findViewById(R.id.recyclerview1)
            ?: RecyclerView(this)

        drawerView?.findViewById<View>(R.id.btn_drawer_new_file)?.setOnClickListener {
            promptCreateFile(currentProject.srcDir)
        }
        drawerView?.findViewById<View>(R.id.btn_drawer_new_folder)?.setOnClickListener {
            promptCreateFolder(currentProject.srcDir)
        }
        drawerView?.findViewById<View>(R.id.btn_drawer_refresh)?.setOnClickListener {
            setupTree()
            Toast.makeText(this, "File tree refreshed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupEditor() {
        try {
            val prefs = getSharedPreferences("build_studio_settings", Context.MODE_PRIVATE)
            currentFontSize = prefs.getInt("editor_font_size", 14).toFloat().coerceIn(10f, 38f)
            val wordWrap = prefs.getBoolean("editor_word_wrap", false)

            codeEditor.apply {
                setTextSize(currentFontSize)
                typefaceText = Typeface.MONOSPACE
                typefaceLineNumber = Typeface.MONOSPACE
                isLineNumberEnabled = true
                isWordwrap = wordWrap
                isFocusable = true
                isFocusableInTouchMode = true
                overScrollMode = View.OVER_SCROLL_ALWAYS
                try {
                    val method = javaClass.getMethod("setEdgeEffectColor", Int::class.javaPrimitiveType)
                    method.invoke(this, Color.parseColor("#9E9E9E"))
                } catch (_: Throwable) {}

                try {
                    val scheme = colorScheme
                    scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, Color.WHITE)
                    scheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, Color.WHITE)
                    scheme.setColor(EditorColorScheme.LINE_NUMBER, Color.parseColor("#0084FF"))
                    scheme.setColor(EditorColorScheme.LINE_DIVIDER, Color.parseColor("#EEEEEE"))
                    scheme.setColor(EditorColorScheme.TEXT_NORMAL, Color.parseColor("#212121"))
                    scheme.setColor(EditorColorScheme.SELECTION_INSERT, Color.parseColor("#0084FF"))
                    scheme.setColor(EditorColorScheme.SELECTION_HANDLE, Color.parseColor("#0084FF"))
                    scheme.setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, Color.parseColor("#BBDEFB"))
                    scheme.setColor(EditorColorScheme.CURRENT_LINE, Color.parseColor("#FAFAFA"))
                } catch (_: Throwable) {}
            }

            // Smooth code pinch-to-zoom (Code Zooming)
            scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                private var baseSize = currentFontSize

                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    baseSize = currentFontSize
                    return true
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val factor = detector.scaleFactor
                    if (factor > 0.01f && factor < 100.0f) {
                        val newSize = (baseSize * factor).coerceIn(10f, 38f)
                        if (abs(newSize - currentFontSize) >= 0.25f) {
                            currentFontSize = newSize
                            codeEditor.setTextSize(currentFontSize)
                        }
                    }
                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    prefs.edit().putInt("editor_font_size", currentFontSize.toInt()).apply()
                }
            })

            codeEditor.setOnTouchListener { _, event ->
                val handled = scaleGestureDetector.onTouchEvent(event)
                if (event.pointerCount > 1 || scaleGestureDetector.isInProgress) {
                    true
                } else {
                    false
                }
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupTree() {
        try {
            val rootDir = File(currentProject.rootPath)
            if (expandedPaths.isEmpty() && rootDir.exists()) {
                expandedPaths.add(rootDir.absolutePath)
                fun expandSubDirs(dir: File) {
                    try {
                        val children = dir.listFiles() ?: return
                        for (c in children) {
                            if (c.isDirectory && c.name != "build" && c.name != ".git" && c.name != ".build_ai_backups") {
                                expandedPaths.add(c.absolutePath)
                                expandSubDirs(c)
                            }
                        }
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }
                expandSubDirs(rootDir)
            }
            rvFileTree.layoutManager = LinearLayoutManager(this)
            treeAdapter = TreeAdapter(fileNodes)
            rvFileTree.adapter = treeAdapter
            refreshTreeView()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun refreshTreeView() {
        try {
            fileNodes.clear()
            val rootDir = File(currentProject.rootPath)
            if (rootDir.exists()) {
                buildFileNodes(rootDir, fileNodes, 0)
            }
            treeAdapter.notifyDataSetChanged()
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
            if (f.name == "build" || f.name == ".git" || f.name == ".build_ai_backups") continue
            val isExpanded = expandedPaths.contains(f.absolutePath)
            list.add(FileNode(f, depth, f.isDirectory, isExpanded))
            if (f.isDirectory && isExpanded) {
                buildFileNodes(f, list, depth + 1)
            }
        }
    }

    inner class TreeAdapter(private val nodes: List<FileNode>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(position: Int): Int {
            return if (nodes[position].isDirectory) 1 else 2
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == 1) {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_dir, parent, false)
                DirViewHolder(v)
            } else {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
                FileViewHolder(v)
            }
        }

        override fun getItemCount(): Int = nodes.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val node = nodes[position]
            val padLeft = (node.depth * 18) + 12

            if (holder is DirViewHolder) {
                holder.tvName.text = node.file.name
                holder.itemView.setPadding(padLeft, 0, 12, 0)

                if (node.isExpanded) {
                    holder.ivArrow?.setImageResource(R.drawable.arrow1)
                } else {
                    holder.ivArrow?.setImageResource(R.drawable.arrow)
                }

                holder.itemView.setOnClickListener {
                    if (expandedPaths.contains(node.file.absolutePath)) {
                        expandedPaths.remove(node.file.absolutePath)
                    } else {
                        expandedPaths.add(node.file.absolutePath)
                    }
                    refreshTreeView()
                }
                holder.itemView.setOnLongClickListener {
                    showFolderContextMenu(node.file)
                    true
                }
            } else if (holder is FileViewHolder) {
                holder.tvName.text = node.file.name
                holder.itemView.setPadding(padLeft, 0, 12, 0)

                val nameLower = node.file.name.lowercase()
                if (nameLower.endsWith(".java")) {
                    holder.ivIcon.setImageResource(R.drawable.java_96)
                } else {
                    holder.ivIcon.setImageResource(R.drawable.file)
                }

                holder.itemView.setOnClickListener {
                    openFileInEditor(node.file)
                    drawerLayout.closeDrawer(GravityCompat.START)
                }
                holder.itemView.setOnLongClickListener {
                    showFileContextMenu(node.file)
                    true
                }
            }
        }
    }

    class DirViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView = v.findViewById(R.id.tv_name)
        val ivArrow: ImageView? = v.findViewById(R.id.iv_arrow)
        val ivIcon: ImageView? = v.findViewById(R.id.imageview2)
    }

    class FileViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView = v.findViewById(R.id.tv_name)
        val ivIcon: ImageView = v.findViewById(R.id.imageview1)
    }

    private fun showFolderContextMenu(folder: File) {
        val options = arrayOf("📄  Create File", "📁  Create Folder", "✏️  Rename", "🗑️  Delete")
        AlertDialog.Builder(this)
            .setTitle(folder.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> promptCreateFile(folder)
                    1 -> promptCreateFolder(folder)
                    2 -> promptRename(folder)
                    3 -> promptDelete(folder)
                }
            }
            .show()
    }

    private fun showFileContextMenu(file: File) {
        val options = arrayOf("✏️  Rename", "🗑️  Delete")
        AlertDialog.Builder(this)
            .setTitle(file.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> promptRename(file)
                    1 -> promptDelete(file)
                }
            }
            .show()
    }

    private fun promptCreateFile(folder: File) {
        val dialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(R.layout.dialog_input)
            window?.apply {
                setBackgroundDrawableResource(android.R.color.transparent)
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }

        val tvTitle = dialog.findViewById<TextView>(R.id.dialog_title)
        val etInput = dialog.findViewById<EditText>(R.id.dialog_input)
        val btnCancel = dialog.findViewById<View>(R.id.btn_cancel)
        val btnSubmit = dialog.findViewById<View>(R.id.btn_submit)

        tvTitle?.text = "Create File in ${folder.name}"
        etInput?.hint = "e.g. MyClass.java or layout.xml"

        btnCancel?.setOnClickListener { dialog.dismiss() }
        btnSubmit?.setOnClickListener {
            val name = etInput?.text?.toString()?.trim() ?: ""
            if (name.isEmpty()) {
                etInput?.error = "File name cannot be empty"
                return@setOnClickListener
            }
            val newFile = File(folder, name)
            if (newFile.exists()) {
                etInput?.error = "File already exists"
                return@setOnClickListener
            }

            try {
                if (name.endsWith(".java")) {
                    val className = name.substringBeforeLast(".")
                    val javaDir = File(currentProject.rootPath, "app/src/main/java")
                    val relPath = if (folder.absolutePath.startsWith(javaDir.absolutePath)) {
                        folder.absolutePath.removePrefix(javaDir.absolutePath).trim(File.separatorChar).replace(File.separatorChar, '.')
                    } else ""
                    val pkg = if (relPath.isNotEmpty()) relPath else currentProject.packageName
                    val template = "package $pkg;\n\npublic class $className {\n    \n}\n"
                    FileUtil.writeFile(newFile.absolutePath, template)
                } else if (name.endsWith(".xml")) {
                    val template = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"match_parent\"\n    android:orientation=\"vertical\">\n\n</LinearLayout>\n"
                    FileUtil.writeFile(newFile.absolutePath, template)
                } else {
                    newFile.createNewFile()
                }

                expandedPaths.add(folder.absolutePath)
                refreshTreeView()
                openFileInEditor(newFile)
                drawerLayout.closeDrawer(GravityCompat.START)
                Toast.makeText(this@EditorActivity, "Created ${newFile.name}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            } catch (e: Exception) {
                Toast.makeText(this@EditorActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        dialog.show()
    }

    private fun promptCreateFolder(parent: File) {
        val dialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(R.layout.dialog_input)
            window?.apply {
                setBackgroundDrawableResource(android.R.color.transparent)
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }

        val tvTitle = dialog.findViewById<TextView>(R.id.dialog_title)
        val etInput = dialog.findViewById<EditText>(R.id.dialog_input)
        val btnCancel = dialog.findViewById<View>(R.id.btn_cancel)
        val btnSubmit = dialog.findViewById<View>(R.id.btn_submit)

        tvTitle?.text = "Create Folder in ${parent.name}"
        etInput?.hint = "Folder name"

        btnCancel?.setOnClickListener { dialog.dismiss() }
        btnSubmit?.setOnClickListener {
            val name = etInput?.text?.toString()?.trim() ?: ""
            if (name.isEmpty()) {
                etInput?.error = "Folder name cannot be empty"
                return@setOnClickListener
            }
            val newDir = File(parent, name)
            if (newDir.exists()) {
                etInput?.error = "Folder already exists"
                return@setOnClickListener
            }

            try {
                newDir.mkdirs()
                expandedPaths.add(parent.absolutePath)
                expandedPaths.add(newDir.absolutePath)
                refreshTreeView()
                Toast.makeText(this@EditorActivity, "Created ${newDir.name}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            } catch (e: Exception) {
                Toast.makeText(this@EditorActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        dialog.show()
    }

    private fun promptRename(target: File) {
        val dialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(R.layout.dialog_input)
            window?.apply {
                setBackgroundDrawableResource(android.R.color.transparent)
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }

        val tvTitle = dialog.findViewById<TextView>(R.id.dialog_title)
        val etInput = dialog.findViewById<EditText>(R.id.dialog_input)
        val btnCancel = dialog.findViewById<View>(R.id.btn_cancel)
        val btnSubmit = dialog.findViewById<View>(R.id.btn_submit)

        tvTitle?.text = "Rename ${target.name}"
        etInput?.setText(target.name)
        etInput?.setSelection(target.name.length)

        btnCancel?.setOnClickListener { dialog.dismiss() }
        btnSubmit?.setOnClickListener {
            val newName = etInput?.text?.toString()?.trim() ?: ""
            if (newName.isEmpty()) {
                etInput?.error = "Name cannot be empty"
                return@setOnClickListener
            }
            if (newName == target.name) {
                dialog.dismiss()
                return@setOnClickListener
            }

            val newTarget = File(target.parentFile, newName)
            if (newTarget.exists()) {
                etInput?.error = "Name already exists"
                return@setOnClickListener
            }

            val oldPath = target.absolutePath
            if (target.renameTo(newTarget)) {
                if (expandedPaths.contains(oldPath)) {
                    expandedPaths.remove(oldPath)
                    expandedPaths.add(newTarget.absolutePath)
                }
                handleFileRenamedInEditor(oldPath, newTarget.absolutePath)
                refreshTreeView()
                Toast.makeText(this@EditorActivity, "Renamed to ${newTarget.name}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            } else {
                Toast.makeText(this@EditorActivity, "Failed to rename", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun promptDelete(target: File) {
        val dialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(R.layout.delete_dialog)
            window?.apply {
                setBackgroundDrawableResource(android.R.color.transparent)
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }

        val tvTitle = dialog.findViewById<TextView>(R.id.dialog_title)
        val tvMsg = dialog.findViewById<TextView>(R.id.dialog_msg)
        val btnCancel = dialog.findViewById<View>(R.id.btn_cancel)
        val btnConfirm = dialog.findViewById<View>(R.id.t1)

        tvTitle?.text = "Delete ${target.name}"
        tvMsg?.text = "Are you sure you want to delete ${target.name}? This will permanently remove it."

        btnCancel?.setOnClickListener { dialog.dismiss() }
        btnConfirm?.setOnClickListener {
            val path = target.absolutePath
            expandedPaths.remove(path)
            if (target.isDirectory) {
                target.deleteRecursively()
                handleFolderDeletedInEditor(path)
            } else {
                target.delete()
                handleFileDeletedInEditor(path)
            }
            refreshTreeView()
            Toast.makeText(this@EditorActivity, "Deleted ${target.name}", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun handleFileDeletedInEditor(deletedPath: String) {
        val idx = openTabs.indexOfFirst { it.absolutePath == deletedPath }
        if (idx != -1) {
            tabLayout.removeTabAt(idx)
            openTabs.removeAt(idx)
            fileContentCache.remove(deletedPath)
            if (openTabs.isNotEmpty()) {
                val nextIdx = minOf(idx, openTabs.size - 1)
                tabLayout.getTabAt(nextIdx)?.select()
            } else {
                codeEditor.setText("")
                tvPrjName.text = currentProject.name
                activeFile = null
            }
        }
    }

    private fun handleFileRenamedInEditor(oldPath: String, newPath: String) {
        val idx = openTabs.indexOfFirst { it.absolutePath == oldPath }
        if (idx != -1) {
            val newFile = File(newPath)
            openTabs[idx] = newFile
            tabLayout.getTabAt(idx)?.text = newFile.name
            tabLayout.getTabAt(idx)?.tag = newFile
            val cached = fileContentCache.remove(oldPath)
            if (cached != null) fileContentCache[newPath] = cached
            if (activeFile?.absolutePath == oldPath) {
                activeFile = newFile
                tvPrjName.text = "${currentProject.name} — ${newFile.name}"
            }
        }
    }

    private fun handleFolderDeletedInEditor(folderPath: String) {
        val toRemove = openTabs.filter { it.absolutePath.startsWith(folderPath) }
        for (f in toRemove) {
            handleFileDeletedInEditor(f.absolutePath)
        }
    }

    private fun openDefaultFile() {
        try {
            ensureProjectStructure(currentProject)

            var targetFile: File? = null

            // 0. Check editorOpened.json for last opened files
            val openedJsonFile = File(currentProject.rootPath, "editorOpened.json")
            if (openedJsonFile.exists()) {
                try {
                    val raw = openedJsonFile.readText()
                    val arr = org.json.JSONArray(raw)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val p = obj.optString("path", "")
                        if (p.isNotEmpty()) {
                            val f = File(p)
                            if (f.exists() && f.isFile) {
                                targetFile = f
                                break
                            }
                        }
                    }
                } catch (_: Throwable) {}
            }

            // 1. Search for MainActivity in srcDir
            val srcDir = currentProject.srcDir
            if (targetFile == null && srcDir.exists()) {
                targetFile = findFileRecursively(srcDir) { it.name.startsWith("MainActivity") }
            }

            // 2. Search for any .java or .kt file in project
            if (targetFile == null) {
                targetFile = findFileRecursively(File(currentProject.rootPath)) { it.name.endsWith(".java") || it.name.endsWith(".kt") }
            }

            // 3. Fallback to layout XML
            if (targetFile == null) {
                val layoutFile = File(currentProject.resDir, "layout/activity_main.xml")
                if (layoutFile.exists()) targetFile = layoutFile
            }

            // 4. Fallback to manifest
            if (targetFile == null) {
                val manifest = currentProject.manifestFile
                if (manifest.exists()) targetFile = manifest
            }

            // 5. Ultimate fallback: create MainActivity.java
            if (targetFile == null || !targetFile.exists()) {
                val defaultJava = File(currentProject.srcDir, "MainActivity.java")
                defaultJava.parentFile?.mkdirs()
                val pkg = if (currentProject.packageName.isNotEmpty()) currentProject.packageName else "com.example.app"
                val defaultContent = """package $pkg;

import android.app.Activity;
import android.os.Bundle;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
}
"""
                FileUtil.writeFile(defaultJava.absolutePath, defaultContent)
                targetFile = defaultJava
            }

            if (targetFile != null) {
                openFileInEditor(targetFile)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun findFileRecursively(dir: File, predicate: (File) -> Boolean): File? {
        try {
            val children = dir.listFiles() ?: return null
            for (f in children) {
                if (f.isDirectory) {
                    if (f.name == "build" || f.name == ".git" || f.name == ".build_ai_backups") continue
                    val found = findFileRecursively(f, predicate)
                    if (found != null) return found
                } else if (predicate(f)) {
                    return f
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return null
    }

    private fun openFileInEditor(file: File) {
        try {
            if (!file.exists()) {
                file.parentFile?.mkdirs()
                FileUtil.writeFile(file.absolutePath, "// File: ${file.name}\n")
            }

            var existingIndex = -1
            for (i in 0 until tabLayout.tabCount) {
                val tab = tabLayout.getTabAt(i)
                if ((tab?.tag as? File)?.absolutePath == file.absolutePath) {
                    existingIndex = i
                    break
                }
            }

            if (existingIndex != -1) {
                tabLayout.getTabAt(existingIndex)?.select()
            } else {
                val tab = tabLayout.newTab().apply {
                    text = file.name.uppercase()
                    tag = file
                }
                tabLayout.addTab(tab)
                openTabs.add(file)

                // Long click on tab to prompt closing tab
                tab.view.setOnLongClickListener {
                    promptCloseTab(tab, file)
                    true
                }

                tab.select()
            }
            switchToFile(file)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun promptCloseTab(tab: TabLayout.Tab, file: File) {
        AlertDialog.Builder(this)
            .setTitle("Close ${file.name}?")
            .setMessage("Save and close this tab?")
            .setPositiveButton("Close") { _, _ ->
                saveCurrentFile()
                val idx = openTabs.indexOfFirst { it.absolutePath == file.absolutePath }
                if (idx != -1) {
                    openTabs.removeAt(idx)
                    fileContentCache.remove(file.absolutePath)
                    tabLayout.removeTab(tab)
                    if (openTabs.isNotEmpty()) {
                        val nextIdx = (idx - 1).coerceAtLeast(0)
                        tabLayout.getTabAt(nextIdx)?.select()
                        switchToFile(openTabs[nextIdx])
                    } else {
                        activeFile = null
                        if (::codeEditor.isInitialized) codeEditor.setText("")
                        tvPrjName.text = currentProject.name
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun switchToFile(file: File) {
        try {
            activeFile = file
            var content = fileContentCache[file.absolutePath]
            if (content == null) {
                content = if (file.exists()) FileUtil.readFile(file.absolutePath) else ""
                if (content.isEmpty() && file.name.endsWith(".java")) {
                    val pkg = if (currentProject.packageName.isNotEmpty()) currentProject.packageName else "com.example.app"
                    val className = file.nameWithoutExtension
                    content = """package $pkg;

import android.app.Activity;
import android.os.Bundle;

public class $className extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
}
"""
                    FileUtil.writeFile(file.absolutePath, content)
                }
            }
            fileContentCache[file.absolutePath] = content

            if (::codeEditor.isInitialized) {
                if (file.name.endsWith(".java", ignoreCase = true) || file.name.endsWith(".kt", ignoreCase = true)) {
                    try {
                        codeEditor.setEditorLanguage(JavaLanguage())
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                } else {
                    try {
                        codeEditor.setEditorLanguage(null)
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }

                codeEditor.setText(content)
                codeEditor.post {
                    codeEditor.setText(content)
                }
            }

            tvPrjName.text = "${currentProject.name} — ${file.name}"

            // Persist active file to editorOpened.json
            try {
                val openedJson = org.json.JSONArray().apply {
                    put(org.json.JSONObject().put("path", file.absolutePath))
                }
                File(currentProject.rootPath, "editorOpened.json").writeText(openedJson.toString())
            } catch (_: Throwable) {}
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun saveCurrentFile() {
        val f = activeFile ?: return
        if (!::codeEditor.isInitialized) return
        val text = codeEditor.text.toString()
        fileContentCache[f.absolutePath] = text
        FileUtil.writeFile(f.absolutePath, text)
        currentProject.lastModified = System.currentTimeMillis()
        currentProject.saveConfig()
    }

    override fun onPause() {
        super.onPause()
        saveCurrentFile()
    }

    private fun runBuildPipeline() {
        saveCurrentFile()

        val progressDialog = android.app.ProgressDialog(this).apply {
            setTitle("Building APK")
            setMessage("Initiating build pipeline...")
            setCancelable(false)
            show()
        }

        val task = CompilerAsyncTask(this, currentProject) { result ->
            runOnUiThread {
                if (progressDialog.isShowing) {
                    progressDialog.dismiss()
                }
                if (result.isSuccess && result.apkFile != null) {
                    DialogUtil.showApkUtilityDialog(this, result.apkFile, currentProject.name, currentProject.packageName)
                } else {
                    DialogUtil.showCompilerErrorDialog(
                        this,
                        result.errorMessage,
                        currentProject.rootPath,
                        activeFile?.absolutePath
                    )
                }
            }
        }

        task.onProgressListener = { msg, step, total ->
            runOnUiThread {
                if (progressDialog.isShowing) {
                    progressDialog.setMessage("[$step/$total] $msg")
                }
            }
        }

        task.execute()
    }

    private fun showMorePopupMenu(anchor: View) {
        val popupView = LayoutInflater.from(this).inflate(R.layout.more_popupo_menu, null)
        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
        }

        // 1. Add Library: Opens dependency manager dialog
        popupView.findViewById<View>(R.id.menu_add_library)?.setOnClickListener {
            popupWindow.dismiss()
            showAddLibraryDialog()
        }

        // 2. File Explorer: Opens side drawer showing full project tree
        popupView.findViewById<View>(R.id.menu_file_explorer)?.setOnClickListener {
            popupWindow.dismiss()
            if (!drawerLayout.isDrawerOpen(GravityCompat.START)) {
                setupTree()
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }

        // 3. Build AI: Launches integrated AI assistant
        popupView.findViewById<View>(R.id.build_ai)?.setOnClickListener {
            popupWindow.dismiss()
            val intent = Intent(this, BuildAiActivity::class.java).apply {
                putExtra("project_path", currentProject.rootPath)
                putExtra("active_file", activeFile?.absolutePath)
            }
            startActivity(intent)
            Animatoo.animateSlideLeft(this)
        }

        // 4. Backup Project (.zip)
        popupView.findViewById<View>(R.id.menu_backup)?.setOnClickListener {
            popupWindow.dismiss()
            saveCurrentFile()
            backupCurrentProject()
        }

        // 5. Configure Project
        popupView.findViewById<View>(R.id.menu_edit_project)?.setOnClickListener {
            popupWindow.dismiss()
            saveCurrentFile()
            showConfigureProjectDialog()
        }

        // Shortcuts to quickly create Java or Resource file
        popupView.findViewById<View>(R.id.java_file)?.setOnClickListener {
            popupWindow.dismiss()
            promptCreateFile(currentProject.srcDir)
        }

        popupView.findViewById<View>(R.id.res_file)?.setOnClickListener {
            popupWindow.dismiss()
            val layoutDir = File(currentProject.resDir, "layout").apply { mkdirs() }
            promptCreateFile(layoutDir)
        }

        popupWindow.showAsDropDown(anchor, 0, 0, Gravity.END)
    }

    private fun backupCurrentProject() {
        val rootDir = File(currentProject.rootPath)
        if (!rootDir.exists()) {
            Toast.makeText(this, "Project folder not found: ${currentProject.rootPath}", Toast.LENGTH_SHORT).show()
            return
        }

        val candidates = mutableListOf<File>()
        try {
            val pubDownload = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            candidates.add(File(pubDownload, "BuildStudio/Backups"))
            candidates.add(pubDownload)
        } catch (_: Throwable) {}
        candidates.add(File("/storage/emulated/0/test-folder"))
        candidates.add(File("/storage/emulated/0/.BUILD STUDIO/backups"))
        getExternalFilesDir("backups")?.let { candidates.add(it) }
        candidates.add(File(filesDir, "backups"))

        var targetDir: File? = null
        for (candidate in candidates) {
            try {
                if (!candidate.exists()) candidate.mkdirs()
                if (candidate.exists() && candidate.canWrite()) {
                    val testFile = File(candidate, ".test_write_${System.currentTimeMillis()}")
                    if (testFile.createNewFile()) {
                        testFile.delete()
                        targetDir = candidate
                        break
                    }
                }
            } catch (_: Throwable) {}
        }

        val finalDir = targetDir ?: File(filesDir, "backups").apply { mkdirs() }
        val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
        val zipFileName = "${currentProject.name}_backup_$timeStamp.zip"
        val zipFile = File(finalDir, zipFileName)

        val progressDialog = android.app.ProgressDialog(this).apply {
            setMessage("Creating backup for ${currentProject.name}...")
            setCancelable(false)
            show()
        }

        Thread {
            try {
                java.util.zip.ZipOutputStream(java.io.FileOutputStream(zipFile)).use { zos ->
                    rootDir.walkTopDown().forEach { f ->
                        if (f.absolutePath == rootDir.absolutePath) return@forEach
                        val relPath = f.relativeTo(rootDir).path.replace('\\', '/')
                        if (relPath.isNotEmpty() && !relPath.startsWith("build/") && !relPath.startsWith(".git/") && relPath != "build" && relPath != ".git") {
                            if (f.isDirectory) {
                                val dirEntry = if (relPath.endsWith("/")) relPath else "$relPath/"
                                try {
                                    zos.putNextEntry(java.util.zip.ZipEntry(dirEntry))
                                    zos.closeEntry()
                                } catch (_: Throwable) {}
                            } else {
                                try {
                                    zos.putNextEntry(java.util.zip.ZipEntry(relPath))
                                    f.inputStream().use { it.copyTo(zos) }
                                    zos.closeEntry()
                                } catch (_: Throwable) {}
                            }
                        }
                    }
                }

                try {
                    val staticZip = File(finalDir, "${currentProject.name}_backup.zip")
                    FileUtil.copyFile(zipFile, staticZip)
                } catch (_: Throwable) {}

                runOnUiThread {
                    progressDialog.dismiss()
                    val sizeMb = String.format(java.util.Locale.US, "%.2f MB", zipFile.length().toDouble() / (1024 * 1024))
                    AlertDialog.Builder(this)
                        .setTitle("Backup Created ✓")
                        .setMessage("Project: ${currentProject.name}\nSize: $sizeMb\n\nLocation:\n${zipFile.absolutePath}")
                        .setPositiveButton("Share / Export") { _, _ ->
                            try {
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    this,
                                    "${packageName}.provider",
                                    zipFile
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/zip"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                startActivity(Intent.createChooser(shareIntent, "Share Backup Zip"))
                            } catch (e: Exception) {
                                Toast.makeText(this, "Share error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton("OK", null)
                        .show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    progressDialog.dismiss()
                    AlertDialog.Builder(this)
                        .setTitle("Backup Error")
                        .setMessage("Failed to create backup:\n${e.message}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }.start()
    }

    private fun showAddLibraryDialog() {
        val dialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(R.layout.dialog_input)
            window?.apply {
                setBackgroundDrawableResource(android.R.color.transparent)
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }

        val tvTitle = dialog.findViewById<TextView>(R.id.dialog_title)
        val etInput = dialog.findViewById<EditText>(R.id.dialog_input)
        val btnCancel = dialog.findViewById<View>(R.id.btn_cancel)
        val btnSubmit = dialog.findViewById<View>(R.id.btn_submit)

        tvTitle?.text = "Add Library (AAR / Maven)"
        etInput?.hint = "e.g. androidx.recyclerview:recyclerview:1.3.2"

        btnCancel?.setOnClickListener { dialog.dismiss() }
        btnSubmit?.setOnClickListener {
            val dep = etInput?.text?.toString()?.trim() ?: ""
            if (dep.isEmpty()) {
                etInput?.error = "Please enter dependency coordinate"
                return@setOnClickListener
            }

            try {
                val gradleFile = File(currentProject.rootPath, "app/build.gradle")
                if (gradleFile.exists()) {
                    var content = gradleFile.readText()
                    if (content.contains("dependencies {")) {
                        content = content.replaceFirst("dependencies {", "dependencies {\n    implementation '$dep'")
                    } else {
                        content += "\n\ndependencies {\n    implementation '$dep'\n}\n"
                    }
                    gradleFile.writeText(content)
                }

                val libsDir = File(currentProject.rootPath, "app/Build/libs/${dep.replace(':', '_')}").apply { mkdirs() }
                Toast.makeText(this, "Added library: $dep 🚀", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            } catch (e: Exception) {
                Toast.makeText(this, "Error adding library: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        dialog.show()
    }

    private fun showConfigureProjectDialog() {
        pendingEditLogoBitmap = null
        val dialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(R.layout.dialog_edit_project)
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }

        val ivLogo = dialog.findViewById<ImageView>(R.id.iv_edit_logo)
        val layoutIconPicker = dialog.findViewById<View>(R.id.layout_edit_icon_picker)
        val etName = dialog.findViewById<TextInputEditText>(R.id.et_edit_app_name)
        val etPackage = dialog.findViewById<TextInputEditText>(R.id.et_edit_package_name)
        val etMinSdk = dialog.findViewById<TextInputEditText>(R.id.et_edit_min_sdk)
        val etTargetSdk = dialog.findViewById<TextInputEditText>(R.id.et_edit_target_sdk)
        val etVersionCode = dialog.findViewById<TextInputEditText>(R.id.et_edit_version_code)
        val etVersionName = dialog.findViewById<TextInputEditText>(R.id.et_edit_version_name)
        val btnSave = dialog.findViewById<Button>(R.id.btn_save_config)
        val btnCancel = dialog.findViewById<Button>(R.id.btn_cancel_config)

        ivCurrentEditingLogo = ivLogo

        val iconCandidates = listOf(
            currentProject.iconPath?.let { File(it) },
            File(currentProject.rootPath, "app/src/main/res/drawable-xhdpi/app_icon.png"),
            File(currentProject.rootPath, "app/src/main/res/drawable/app_icon.png"),
            File(currentProject.rootPath, "app/src/main/res/mipmap-xhdpi/ic_launcher.png"),
            File(currentProject.rootPath, "app/src/main/res/mipmap/ic_launcher.png"),
            File(currentProject.rootPath, "icon.png")
        )
        val iconFile = iconCandidates.firstOrNull { it != null && it.exists() }
        if (iconFile != null) {
            val bmp = BitmapFactory.decodeFile(iconFile.absolutePath)
            if (bmp != null) ivLogo?.setImageBitmap(bmp)
            else ivLogo?.setImageResource(R.drawable.ic_launcher)
        } else {
            ivLogo?.setImageResource(R.drawable.ic_launcher)
        }

        etName?.setText(currentProject.name)
        etPackage?.setText(currentProject.packageName)
        etMinSdk?.setText(currentProject.minSdk.toString())
        etTargetSdk?.setText(currentProject.targetSdk.toString())
        etVersionCode?.setText(currentProject.versionCode.toString())
        etVersionName?.setText(currentProject.versionName)

        layoutIconPicker?.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
            }
            startActivityForResult(Intent.createChooser(intent, "Select App Icon (PNG required)"), 301)
        }

        btnCancel?.setOnClickListener { dialog.dismiss() }

        btnSave?.setOnClickListener {
            val newName = etName?.text.toString().trim()
            val newPkg = etPackage?.text.toString().trim()
            val newMinSdk = etMinSdk?.text.toString().trim().toIntOrNull() ?: currentProject.minSdk
            val newTargetSdk = etTargetSdk?.text.toString().trim().toIntOrNull() ?: currentProject.targetSdk
            val newVCode = etVersionCode?.text.toString().trim().toIntOrNull() ?: currentProject.versionCode
            val newVName = etVersionName?.text.toString().trim().ifEmpty { currentProject.versionName }

            if (newName.isEmpty()) {
                Toast.makeText(this, "App Name cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (newPkg.isEmpty()) {
                Toast.makeText(this, "Package Name cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Save Icon if changed
            val bmp = pendingEditLogoBitmap
            if (bmp != null) {
                try {
                    val targets = listOf(
                        File(currentProject.rootPath, "app/src/main/res/drawable-xhdpi/app_icon.png"),
                        File(currentProject.rootPath, "app/src/main/res/drawable/app_icon.png"),
                        File(currentProject.rootPath, "app/src/main/res/mipmap-xhdpi/ic_launcher.png"),
                        File(currentProject.rootPath, "app/src/main/res/mipmap/ic_launcher.png"),
                        File(currentProject.rootPath, "icon.png")
                    )
                    for (t in targets) {
                        t.parentFile?.mkdirs()
                        java.io.FileOutputStream(t).use { fos ->
                            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
                        }
                    }
                    currentProject.iconPath = File(currentProject.rootPath, "app/src/main/res/drawable-xhdpi/app_icon.png").absolutePath
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Update Manifest
            val manifest = currentProject.manifestFile
            if (manifest.exists()) {
                try {
                    var mText = manifest.readText()
                    mText = mText.replace(Regex("""package\s*=\s*"[^"]+""""), """package="$newPkg"""")
                    if (mText.contains("android:minSdkVersion")) {
                        mText = mText.replace(Regex("""android:minSdkVersion\s*=\s*"[^"]+""""), """android:minSdkVersion="$newMinSdk"""")
                    }
                    if (mText.contains("android:targetSdkVersion")) {
                        mText = mText.replace(Regex("""android:targetSdkVersion\s*=\s*"[^"]+""""), """android:targetSdkVersion="$newTargetSdk"""")
                    }
                    if (mText.contains("android:versionCode")) {
                        mText = mText.replace(Regex("""android:versionCode\s*=\s*"[^"]+""""), """android:versionCode="$newVCode"""")
                    }
                    if (mText.contains("android:versionName")) {
                        mText = mText.replace(Regex("""android:versionName\s*=\s*"[^"]+""""), """android:versionName="$newVName"""")
                    }
                    manifest.writeText(mText)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Update strings.xml
            val stringsFiles = listOf(
                File(currentProject.rootPath, "app/src/main/res/values/strings.xml"),
                File(currentProject.rootPath, "src/main/res/values/strings.xml"),
                File(currentProject.rootPath, "res/values/strings.xml")
            )
            for (sf in stringsFiles) {
                if (sf.exists()) {
                    try {
                        var sText = sf.readText()
                        sText = sText.replace(
                            Regex("""<string\s+name\s*=\s*"app_name"[^>]*>.*?</string>"""),
                            """<string name="app_name">$newName</string>"""
                        )
                        sf.writeText(sText)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // Update build.gradle
            val buildFiles = listOf(
                File(currentProject.rootPath, "app/build.gradle"),
                File(currentProject.rootPath, "app/build.gradle.kts"),
                File(currentProject.rootPath, "build.gradle")
            )
            for (bf in buildFiles) {
                if (bf.exists()) {
                    try {
                        var bText = bf.readText()
                        bText = bText.replace(Regex("""applicationId\s+['"][^'"]+['"]"""), """applicationId "$newPkg"""")
                        bText = bText.replace(Regex("""namespace\s+['"][^'"]+['"]"""), """namespace "$newPkg"""")
                        bText = bText.replace(Regex("""minSdkVersion\s+\d+"""), """minSdkVersion $newMinSdk""")
                        bText = bText.replace(Regex("""targetSdkVersion\s+\d+"""), """targetSdkVersion $newTargetSdk""")
                        bText = bText.replace(Regex("""versionCode\s+\d+"""), """versionCode $newVCode""")
                        bText = bText.replace(Regex("""versionName\s+['"][^'"]+['"]"""), """versionName "$newVName"""")
                        bf.writeText(bText)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            currentProject.name = newName
            currentProject.packageName = newPkg
            currentProject.minSdk = newMinSdk
            currentProject.targetSdk = newTargetSdk
            currentProject.versionCode = newVCode
            currentProject.versionName = newVName
            currentProject.saveConfig()

            tvPrjName.text = "${currentProject.name} — ${activeFile?.name ?: "Editor"}"
            Toast.makeText(this, "Configuration updated! ✓", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 301 && resultCode == RESULT_OK && data?.data != null) {
            val uri = data.data!!
            try {
                // Strict 8-byte PNG validation (89 50 4E 47 0D 0A 1A 0A)
                var isPng = false
                contentResolver.openInputStream(uri)?.use { stream ->
                    val header = ByteArray(8)
                    val read = stream.read(header)
                    if (read == 8 &&
                        header[0] == 0x89.toByte() &&
                        header[1] == 0x50.toByte() && // P
                        header[2] == 0x4E.toByte() && // N
                        header[3] == 0x47.toByte() && // G
                        header[4] == 0x0D.toByte() && // \r
                        header[5] == 0x0A.toByte() && // \n
                        header[6] == 0x1A.toByte() && // EOF
                        header[7] == 0x0A.toByte()    // \n
                    ) {
                        isPng = true
                    }
                }
                if (!isPng) {
                    pendingEditLogoBitmap = null
                    Toast.makeText(this, "Invalid PNG format required", Toast.LENGTH_LONG).show()
                    return
                }
                contentResolver.openInputStream(uri)?.use { stream ->
                    pendingEditLogoBitmap = BitmapFactory.decodeStream(stream)
                    if (pendingEditLogoBitmap != null) {
                        ivCurrentEditingLogo?.setImageBitmap(pendingEditLogoBitmap)
                        Toast.makeText(this, "PNG icon selected ✓", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Invalid PNG format required", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                pendingEditLogoBitmap = null
                Toast.makeText(this, "Invalid PNG format required", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            saveCurrentFile()
            super.onBackPressed()
            Animatoo.animateSlideDown(this)
        }
    }
}
