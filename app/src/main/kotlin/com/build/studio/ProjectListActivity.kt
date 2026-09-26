package com.build.studio

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.apk.builder.model.Project
import com.blogspot.atifsoftwares.animatoolib.Animatoo
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File

class ProjectListActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var tvNoProjects: TextView
    private val projectList = mutableListOf<Project>()
    private lateinit var adapter: ProjectListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.project_list)

        listView = findViewById(R.id.listview1)
        tvNoProjects = findViewById(R.id.no_projects_found)
        val fab = findViewById<FloatingActionButton>(R.id._fab)
        val threeDotMenu = findViewById<ImageView>(R.id.three_dot_menu_image)

        val btnTopAi = findViewById<ImageView>(R.id.btn_top_ai)

        adapter = ProjectListAdapter(this, projectList)
        listView.adapter = adapter

        btnTopAi?.setOnClickListener {
            startActivity(Intent(this, BuildAiActivity::class.java))
            Animatoo.animateSlideLeft(this)
        }

        fab.setOnClickListener {
            startActivity(Intent(this, CreateProjectActivity::class.java))
            Animatoo.animateSlideUp(this)
        }

        threeDotMenu.setOnClickListener { v ->
            showHomePopupMenu(v)
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            if (position in projectList.indices) {
                openProject(projectList[position])
            }
        }

        listView.setOnItemLongClickListener { _, _, position, _ ->
            if (position in projectList.indices) {
                showProjectOptionsMenu(projectList[position])
                true
            } else false
        }
    }

    private fun openProject(proj: Project) {
        val intent = Intent(this, EditorActivity::class.java).apply {
            putExtra("project_path", proj.rootPath)
            putExtra("path", proj.rootPath)
            putExtra("fullPath", proj.rootPath)
            putExtra("project_name", proj.name)
            putExtra("project", proj.name)
            putExtra("package_name", proj.packageName)
        }
        startActivity(intent)
        Animatoo.animateSlideUp(this)
    }

    override fun onResume() {
        super.onResume()
        loadProjects()
    }

    private fun loadProjects() {
        projectList.clear()

        val internalDir1 = File(filesDir, "projects")
        val internalDir2 = File("/data/user/0/com.buildstudio/files/projects")
        val primaryDir = File("/storage/emulated/0/.BUILD STUDIO")
        val fallbackDir = File("/storage/emulated/0/test-folder/projects")
        val externalFiles = File(getExternalFilesDir(null), "projects")

        for (dir in listOf(internalDir1, internalDir2, primaryDir, fallbackDir, externalFiles)) {
            if (dir.exists() && dir.isDirectory) {
                val subDirs = dir.listFiles { f -> f.isDirectory } ?: continue
                for (sub in subDirs) {
                    val p = Project.loadFromDirectory(sub)
                    if (p != null && projectList.none { it.rootPath == p.rootPath }) {
                        projectList.add(p)
                    }
                }
            }
        }

        projectList.sortByDescending { it.lastModified }

        if (projectList.isEmpty()) {
            tvNoProjects.visibility = View.VISIBLE
            listView.visibility = View.GONE
        } else {
            tvNoProjects.visibility = View.GONE
            listView.visibility = View.VISIBLE
            adapter.notifyDataSetChanged()
        }
    }

    private fun showHomePopupMenu(anchor: View) {
        val popupView = LayoutInflater.from(this).inflate(R.layout.home_popup_menu, null)
        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            isOutsideTouchable = true
        }

        popupView.findViewById<View>(R.id.b_ai)?.setOnClickListener {
            popupWindow.dismiss()
            startActivity(Intent(this, BuildAiActivity::class.java))
            Animatoo.animateSlideLeft(this)
        }

        // Settings opens the tabbed Settings Bottom Sheet
        popupView.findViewById<View>(R.id.b1)?.setOnClickListener {
            popupWindow.dismiss()
            SettingsBottomSheet.show(this)
        }

        popupView.findViewById<View>(R.id.b2)?.setOnClickListener {
            popupWindow.dismiss()
            startActivity(Intent(this, AboutPageActivity::class.java))
            Animatoo.animateSlideLeft(this)
        }

        popupView.findViewById<View>(R.id.b3)?.setOnClickListener {
            popupWindow.dismiss()
            startActivity(Intent(this, CreateProjectActivity::class.java))
            Animatoo.animateSlideUp(this)
        }

        popupWindow.showAsDropDown(anchor, 0, 0, Gravity.END)
    }

    private fun showProjectOptionsMenu(project: Project) {
        val options = arrayOf("1. Open", "2. Rename", "3. Backup (.zip)", "4. Delete")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(project.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openProject(project)
                    1 -> promptRenameProject(project)
                    2 -> backupProject(project)
                    3 -> promptDeleteProject(project)
                }
            }
            .show()
    }

    private fun promptRenameProject(project: Project) {
        val input = android.widget.EditText(this).apply {
            setText(project.name)
            setSelection(project.name.length)
        }
        val container = android.widget.FrameLayout(this).apply {
            setPadding(50, 20, 50, 20)
            addView(input)
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Rename Project")
            .setView(container)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != project.name) {
                    val oldDir = File(project.rootPath)
                    val newDir = File(oldDir.parentFile, newName)
                    if (newDir.exists()) {
                        android.widget.Toast.makeText(this, "Project with this name already exists", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        if (oldDir.renameTo(newDir)) {
                            val pJson = File(newDir, "project.json")
                            if (pJson.exists()) {
                                try {
                                    val content = pJson.readText()
                                    val updated = content.replaceFirst(Regex("\"name\"\\s*:\\s*\"[^\"]+\""), "\"name\": \"$newName\"")
                                    pJson.writeText(updated)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            android.widget.Toast.makeText(this, "Renamed to $newName", android.widget.Toast.LENGTH_SHORT).show()
                            loadProjects()
                        } else {
                            android.widget.Toast.makeText(this, "Failed to rename", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun backupProject(project: Project) {
        val rootDir = File(project.rootPath)
        if (!rootDir.exists()) {
            android.widget.Toast.makeText(this, "Project folder not found: ${project.rootPath}", android.widget.Toast.LENGTH_LONG).show()
            return
        }

        // Test and find guaranteed writable directory
        val candidates = mutableListOf<File>()
        try {
            val pubDownload = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            candidates.add(File(pubDownload, "BuildStudio/Backups"))
            candidates.add(pubDownload)
        } catch (_: Throwable) {}
        candidates.add(File("/storage/emulated/0/.BUILD STUDIO/backups"))
        candidates.add(File("/storage/emulated/0/test-folder"))
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
        val zipFileName = "${project.name}_backup_$timeStamp.zip"
        val zipFile = File(finalDir, zipFileName)

        val progressDialog = android.app.ProgressDialog(this).apply {
            setMessage("Creating backup for ${project.name}...")
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
                    val staticZip = File(finalDir, "${project.name}_backup.zip")
                    com.apk.builder.FileUtil.copyFile(zipFile, staticZip)
                } catch (_: Throwable) {}

                runOnUiThread {
                    progressDialog.dismiss()
                    showBackupSuccessDialog(project.name, zipFile)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    progressDialog.dismiss()
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Backup Error")
                        .setMessage("Failed to create backup:\n${e.message}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }.start()
    }

    private fun showBackupSuccessDialog(projectName: String, zipFile: File) {
        val sizeMb = String.format(java.util.Locale.US, "%.2f MB", zipFile.length().toDouble() / (1024 * 1024))
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Backup Created ✓")
            .setMessage("Project: $projectName\nSize: $sizeMb\n\nLocation:\n${zipFile.absolutePath}")
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
                    android.widget.Toast.makeText(this, "Share error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("OK", null)
            .show()
    }

    private fun promptDeleteProject(project: Project) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Project")
            .setMessage("Are you sure you want to delete '${project.name}'?\nThis cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                val dir = File(project.rootPath)
                if (dir.deleteRecursively()) {
                    android.widget.Toast.makeText(this, "Deleted ${project.name}", android.widget.Toast.LENGTH_SHORT).show()
                    loadProjects()
                } else {
                    android.widget.Toast.makeText(this, "Failed to delete project", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    class ProjectListAdapter(
        private val context: Context,
        private val items: List<Project>
    ) : BaseAdapter() {

        private val dateFormat = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())

        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_project_card, parent, false)
            val project = items[position]

            val tvTitle = view.findViewById<TextView>(R.id.tv_project_name) ?: view.findViewById<TextView>(R.id.text1)
            val tvPackage = view.findViewById<TextView>(R.id.tv_project_package) ?: view.findViewById<TextView>(R.id.text2)
            val tvTimestamp = view.findViewById<TextView>(R.id.tv_project_timestamp)
            val tvSdkRange = view.findViewById<TextView>(R.id.tv_project_sdk_range)
            val ivIcon = view.findViewById<ImageView>(R.id.iv_project_icon) ?: view.findViewById<ImageView>(R.id.image1)

            tvTitle?.text = project.name
            tvPackage?.text = project.packageName
            tvTimestamp?.text = dateFormat.format(java.util.Date(project.lastModified))
            tvSdkRange?.text = "SDK ${project.minSdk} - ${project.targetSdk}"

            val iconFile = File(project.rootPath, "icon.png")
            if (iconFile.exists()) {
                val bmp = BitmapFactory.decodeFile(iconFile.absolutePath)
                if (bmp != null) ivIcon?.setImageBitmap(bmp)
                else ivIcon?.setImageResource(R.drawable.ic_launcher)
            } else {
                ivIcon?.setImageResource(R.drawable.ic_launcher)
            }

            view.findViewById<View>(R.id.btn_project_options)?.setOnClickListener {
                (context as? ProjectListActivity)?.showProjectOptionsMenu(project)
            }

            return view
        }
    }
}
