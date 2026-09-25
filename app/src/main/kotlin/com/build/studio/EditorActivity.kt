package com.build.studio

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.apk.builder.DialogUtil
import com.apk.builder.FileUtil
import com.apk.builder.logger.Logger
import com.apk.builder.model.Project
import com.blogspot.atifsoftwares.animatoolib.Animatoo
import com.google.android.material.tabs.TabLayout
import com.tyron.compiler.CompilerAsyncTask
import io.github.rosemoe.sora.widget.CodeEditor
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
    private val openTabs = mutableListOf<File>()
    private val fileContentCache = mutableMapOf<String, String>()

    private lateinit var gestureDetector: GestureDetectorCompat
    private var isToolbarVisible = true
    private val fileNodes = mutableListOf<FileNode>()
    private lateinit var treeAdapter: TreeAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.editor)

            var path = intent.getStringExtra("project_path")
                ?: intent.getStringExtra("path")
                ?: intent.getStringExtra("fullPath")
                ?: intent.getStringExtra("projectPath")

            val name = intent.getStringExtra("project_name")
                ?: intent.getStringExtra("project")
                ?: intent.getStringExtra("application_name")
                ?: "Project"

            val pkg = intent.getStringExtra("package_name") ?: "com.example.app"

            if (path.isNullOrEmpty()) {
                val fallbackBase = File("/storage/emulated/0/.BUILD STUDIO")
                val existing = fallbackBase.listFiles { f -> f.isDirectory }?.firstOrNull()
                path = existing?.absolutePath ?: File(fallbackBase, "MyApplication").apply { mkdirs() }.absolutePath
            }

            currentProject = Project(name, pkg, path)

            initViews()
            setupGestures()
            setupEditor()
            setupTree()
            openDefaultFile()
        } catch (e: Throwable) {
            e.printStackTrace()
            Toast.makeText(this, "Editor loaded with fallback: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id._drawer) ?: DrawerLayout(this)
        codeEditor = findViewById(R.id.editor)
        tabLayout = findViewById(R.id.tablayout1)
        tvPrjName = findViewById(R.id.prj_name)
        toolbarLayout = findViewById(R.id.toolbar) ?: LinearLayout(this)

        val drawerView = findViewById<View>(R.id.drawer) ?: findViewById<View>(R.id._nav_view)
        rvFileTree = findViewById<RecyclerView>(R.id.recyclerview1)
            ?: drawerView?.findViewById<RecyclerView>(R.id.recyclerview1)
            ?: findViewById<RecyclerView>(R.id._drawer_recyclerview1)
            ?: drawerView?.findViewById<RecyclerView>(R.id._drawer_recyclerview1)
            ?: RecyclerView(this)

        tvPrjName.text = currentProject.name

        findViewById<View>(R.id.drawer_toggle_btn)?.setOnClickListener {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }

        findViewById<View>(R.id.undo_btn)?.setOnClickListener {
            if (codeEditor.canUndo()) codeEditor.undo()
        }

        findViewById<View>(R.id.redo_btn)?.setOnClickListener {
            if (codeEditor.canRedo()) codeEditor.redo()
        }

        // Run button (Build TextView)
        findViewById<View>(R.id.Build)?.setOnClickListener {
            saveCurrentFile()
            runBuildPipeline()
        }

        // Folder button to toggle drawer
        findViewById<View>(R.id.imageview3)?.setOnClickListener {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }

        // Overflow menu (more_popupo_menu.xml)
        findViewById<View>(R.id.menu)?.setOnClickListener { v ->
            showMorePopupMenu(v)
        }
    }

    private fun setupGestures() {
        gestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 80
            private val SWIPE_VELOCITY_THRESHOLD = 80

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent?,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null || e2 == null) return false
                val diffY = e2.y - e1.y
                val diffX = e2.x - e1.x

                if (abs(diffX) > abs(diffY)) {
                    if (abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            onSwipeRight()
                        } else {
                            onSwipeLeft()
                        }
                        return true
                    }
                } else {
                    if (abs(diffY) > SWIPE_THRESHOLD && abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffY > 0) {
                            onSwipeDown()
                        } else {
                            onSwipeUp()
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        try {
            gestureDetector.onTouchEvent(ev)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun onSwipeDown() {
        if (!isToolbarVisible) {
            isToolbarVisible = true
            toolbarLayout.visibility = View.VISIBLE
            toolbarLayout.animate().translationY(0f).alpha(1.0f).setDuration(280).start()
            tabLayout.animate().translationY(0f).setDuration(280).start()
        }
    }

    private fun onSwipeUp() {
        if (isToolbarVisible) {
            isToolbarVisible = false
            val moveUp = -toolbarLayout.height.toFloat()
            toolbarLayout.animate().translationY(moveUp).alpha(0f).setDuration(280).withEndAction {
                toolbarLayout.visibility = View.GONE
            }.start()
            tabLayout.animate().translationY(moveUp).setDuration(280).start()
        }
    }

    private fun onSwipeRight() {
        if (!drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.openDrawer(GravityCompat.START)
        } else {
            val curr = tabLayout.selectedTabPosition
            if (curr > 0) {
                tabLayout.getTabAt(curr - 1)?.select()
                codeEditor.startAnimation(AnimationUtils.loadAnimation(this, R.anim.animate_slide_in_left))
            }
        }
    }

    private fun onSwipeLeft() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            val curr = tabLayout.selectedTabPosition
            if (curr < tabLayout.tabCount - 1) {
                tabLayout.getTabAt(curr + 1)?.select()
                codeEditor.startAnimation(AnimationUtils.loadAnimation(this, R.anim.animate_slide_left_enter))
            }
        }
    }

    private fun setupEditor() {
        try {
            codeEditor.textSize = 14f
            codeEditor.isLineNumberEnabled = true
            codeEditor.isWordwrap = false

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
            list.add(FileNode(f, depth, f.isDirectory))
            if (f.isDirectory) {
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
            val padLeft = (node.depth * 24) + 12

            if (holder is DirViewHolder) {
                holder.tvName.text = node.file.name
                holder.itemView.setPadding(padLeft, 0, 12, 0)
                holder.itemView.setOnClickListener {
                    refreshTreeView()
                }
                holder.itemView.setOnLongClickListener {
                    showFolderContextMenu(node.file)
                    true
                }
            } else if (holder is FileViewHolder) {
                holder.tvName.text = node.file.name
                holder.itemView.setPadding(padLeft, 0, 12, 0)
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
    }

    class FileViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView = v.findViewById(R.id.tv_name)
    }

    private fun showFolderContextMenu(folder: File) {
        val items = arrayOf("New File", "New Folder", "Rename", "Delete")
        AlertDialog.Builder(this)
            .setTitle(folder.name)
            .setItems(items) { _, which ->
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
        val items = arrayOf("Rename", "Delete")
        AlertDialog.Builder(this)
            .setTitle(file.name)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> promptRename(file)
                    1 -> promptDelete(file)
                }
            }
            .show()
    }

    private fun promptCreateFile(folder: File) {
        val et = EditText(this).apply { hint = "filename.java / filename.xml" }
        AlertDialog.Builder(this)
            .setTitle("New File")
            .setView(et)
            .setPositiveButton("Create") { _, _ ->
                val name = et.text.toString().trim()
                if (name.isNotEmpty()) {
                    val newF = File(folder, name)
                    newF.createNewFile()
                    refreshTreeView()
                    openFileInEditor(newF)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptCreateFolder(parent: File) {
        val et = EditText(this).apply { hint = "Folder name" }
        AlertDialog.Builder(this)
            .setTitle("New Folder")
            .setView(et)
            .setPositiveButton("Create") { _, _ ->
                val name = et.text.toString().trim()
                if (name.isNotEmpty()) {
                    File(parent, name).mkdirs()
                    refreshTreeView()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptRename(target: File) {
        val et = EditText(this).apply { setText(target.name) }
        AlertDialog.Builder(this)
            .setTitle("Rename")
            .setView(et)
            .setPositiveButton("Rename") { _, _ ->
                val newName = et.text.toString().trim()
                if (newName.isNotEmpty() && newName != target.name) {
                    val newTarget = File(target.parentFile, newName)
                    val oldPath = target.absolutePath
                    if (target.renameTo(newTarget)) {
                        handleFileRenamedInEditor(oldPath, newTarget.absolutePath)
                        refreshTreeView()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptDelete(target: File) {
        AlertDialog.Builder(this)
            .setTitle("Delete")
            .setMessage("Are you sure you want to delete ${target.name}?")
            .setPositiveButton("Delete") { _, _ ->
                val path = target.absolutePath
                if (target.isDirectory) {
                    target.deleteRecursively()
                    handleFolderDeletedInEditor(path)
                } else {
                    target.delete()
                    handleFileDeletedInEditor(path)
                }
                refreshTreeView()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
            val rootDir = File(currentProject.rootPath)
            if (!rootDir.exists()) {
                rootDir.mkdirs()
            }
            val mainKt = File(currentProject.srcDir, "MainActivity.kt")
            val mainJava = File(currentProject.srcDir, "MainActivity.java")
            val manifest = currentProject.manifestFile

            when {
                mainKt.exists() -> openFileInEditor(mainKt)
                mainJava.exists() -> openFileInEditor(mainJava)
                manifest.exists() -> openFileInEditor(manifest)
                else -> {
                    val firstFile = rootDir.walkTopDown().firstOrNull { it.isFile && it.name != "project.json" }
                    if (firstFile != null) {
                        openFileInEditor(firstFile)
                    } else {
                        val defaultJava = File(currentProject.srcDir, "MainActivity.java")
                        defaultJava.parentFile?.mkdirs()
                        FileUtil.writeFile(defaultJava.absolutePath, "package ${currentProject.packageName};

public class MainActivity {
}
")
                        openFileInEditor(defaultJava)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun openFileInEditor(file: File) {
        try {
            val existingIndex = openTabs.indexOfFirst { it.absolutePath == file.absolutePath }
            if (existingIndex != -1) {
                tabLayout.getTabAt(existingIndex)?.select()
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun switchToFile(file: File) {
        try {
            activeFile = file
            val content = fileContentCache[file.absolutePath] ?: FileUtil.readFile(file.absolutePath)
            fileContentCache[file.absolutePath] = content
            codeEditor.setText(content)
            tvPrjName.text = "${currentProject.name} — ${file.name}"
            codeEditor.startAnimation(AnimationUtils.loadAnimation(this, R.anim.animate_fade_enter))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveCurrentFile() {
        val f = activeFile ?: return
        val text = codeEditor.text.toString()
        fileContentCache[f.absolutePath] = text
        FileUtil.writeFile(f.absolutePath, text)
    }

    private fun runBuildPipeline() {
        Toast.makeText(this, "Building APK...", Toast.LENGTH_SHORT).show()

        val task = CompilerAsyncTask(this, currentProject) { result ->
            runOnUiThread {
                if (result.isSuccess && result.apkFile != null) {
                    DialogUtil.showApkUtilityDialog(this, result.apkFile, currentProject.name)
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

        // Build AI item
        popupView.findViewById<View>(R.id.build_ai)?.setOnClickListener {
            popupWindow.dismiss()
            val intent = Intent(this, BuildAiActivity::class.java).apply {
                putExtra("project_path", currentProject.rootPath)
                putExtra("active_file", activeFile?.absolutePath)
            }
            startActivity(intent)
            Animatoo.animateSlideLeft(this)
        }

        popupWindow.showAsDropDown(anchor, 0, 0, Gravity.END)
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
            Animatoo.animateSlideDown(this)
        }
    }
}
