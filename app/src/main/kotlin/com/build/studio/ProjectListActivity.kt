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

        val primaryDir = File("/storage/emulated/0/.BUILD STUDIO")
        val fallbackDir = File("/storage/emulated/0/test-folder/projects")
        val internalDir = File(getExternalFilesDir(null), "projects")

        for (dir in listOf(primaryDir, fallbackDir, internalDir)) {
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

        popupView.findViewById<View>(R.id.b1)?.setOnClickListener {
            popupWindow.dismiss()
            startActivity(Intent(this, SettingsActivity::class.java))
            Animatoo.animateSlideLeft(this)
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
        val backupDir = File("/storage/emulated/0/test-folder")
        if (!backupDir.exists()) backupDir.mkdirs()
        val zipFile = File(backupDir, "${project.name}_backup.zip")
        android.widget.Toast.makeText(this, "Creating backup...", android.widget.Toast.LENGTH_SHORT).show()

        Thread {
            try {
                val rootDir = File(project.rootPath)
                java.util.zip.ZipOutputStream(java.io.FileOutputStream(zipFile)).use { zos ->
                    rootDir.walkTopDown().forEach { f ->
                        val relPath = f.relativeTo(rootDir).path
                        if (relPath.isNotEmpty() && !relPath.startsWith("build") && !relPath.startsWith(".gradle")) {
                            if (f.isDirectory) {
                                val dirEntry = if (relPath.endsWith("/")) relPath else "$relPath/"
                                zos.putNextEntry(java.util.zip.ZipEntry(dirEntry))
                                zos.closeEntry()
                            } else {
                                zos.putNextEntry(java.util.zip.ZipEntry(relPath))
                                f.inputStream().use { it.copyTo(zos) }
                                zos.closeEntry()
                            }
                        }
                    }
                }
                runOnUiThread {
                    android.widget.Toast.makeText(this, "Backup saved to: ${zipFile.absolutePath}", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    android.widget.Toast.makeText(this, "Backup failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
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

        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.projects_list, parent, false)
            val project = items[position]

            val tvTitle = view.findViewById<TextView>(R.id.text1)
            val tvPath = view.findViewById<TextView>(R.id.text2)
            val ivIcon = view.findViewById<ImageView>(R.id.image1)

            tvTitle.text = project.name
            tvPath.text = project.rootPath

            val iconFile = File(project.rootPath, "icon.png")
            if (iconFile.exists()) {
                val bmp = BitmapFactory.decodeFile(iconFile.absolutePath)
                if (bmp != null) ivIcon.setImageBitmap(bmp)
                else ivIcon.setImageResource(R.drawable.default_image)
            } else {
                ivIcon.setImageResource(R.drawable.default_image)
            }

            return view
        }
    }
}
