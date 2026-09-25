package com.apk.builder

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.apk.builder.model.Project
import com.build.studio.AboutUsActivity
import com.build.studio.BuildAiActivity
import com.build.studio.OllamaSettingsActivity
import com.build.studio.R
import com.build.studio.SettingsMenuActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var rvProjects: RecyclerView
    private lateinit var emptyStateView: View
    private lateinit var adapter: ProjectAdapter
    private val projectList = mutableListOf<Project>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rvProjects = findViewById(R.id.rv_projects)
        emptyStateView = findViewById(R.id.tv_empty_projects)
        val fabCreate = findViewById<FloatingActionButton>(R.id.fab_create_project)

        rvProjects.layoutManager = LinearLayoutManager(this)
        adapter = ProjectAdapter()
        rvProjects.adapter = adapter

        fabCreate.setOnClickListener {
            val intent = Intent(this@MainActivity, CreateProjectActivity::class.java)
            startActivity(intent)
        }

        findViewById<View>(R.id.btn_top_ai)?.setOnClickListener {
            val intent = Intent(this@MainActivity, BuildAiActivity::class.java)
            startActivity(intent)
        }

        findViewById<View>(R.id.btn_top_settings)?.setOnClickListener {
            startActivity(Intent(this@MainActivity, SettingsMenuActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadProjects()
    }

    private fun loadProjects() {
        projectList.clear()

        val primaryDir = File("/storage/emulated/0/test-folder/projects")
        val fallbackDir = File(getExternalFilesDir(null), "projects")

        val searchDirs = listOf(primaryDir, fallbackDir)
        for (dir in searchDirs) {
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
            emptyStateView.visibility = View.VISIBLE
            rvProjects.visibility = View.GONE
        } else {
            emptyStateView.visibility = View.GONE
            rvProjects.visibility = View.VISIBLE
            adapter.notifyDataSetChanged()
        }
    }

    inner class ProjectAdapter : RecyclerView.Adapter<ProjectAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val ivIcon: ImageView = itemView.findViewById(R.id.iv_project_icon)
            val tvName: TextView = itemView.findViewById(R.id.tv_project_name)
            val tvPackage: TextView = itemView.findViewById(R.id.tv_project_package)
            val tvSdkRange: TextView = itemView.findViewById(R.id.tv_project_sdk_range)
            val tvTimestamp: TextView = itemView.findViewById(R.id.tv_project_timestamp)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_project_card, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val project = projectList[position]
            holder.tvName.text = project.name
            holder.tvPackage.text = "/Internal Storage/BUILDSTUDIO/${project.name}"
            holder.tvSdkRange.text = "Min ${project.minSdk} • Target ${project.targetSdk} • ${project.language}"

            val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
            holder.tvTimestamp.text = sdf.format(Date(project.lastModified))

            val iconFile = if (project.iconPath != null) File(project.iconPath!!) else null
            if (iconFile != null && iconFile.exists()) {
                val bmp = BitmapFactory.decodeFile(iconFile.absolutePath)
                if (bmp != null) holder.ivIcon.setImageBitmap(bmp)
                else holder.ivIcon.setImageResource(R.drawable.ic_launcher)
            } else {
                holder.ivIcon.setImageResource(R.drawable.ic_launcher)
            }

            holder.itemView.setOnClickListener {
                val intent = Intent(this@MainActivity, CodeEditorActivity::class.java).apply {
                    putExtra("project_path", project.rootPath)
                    putExtra("project_name", project.name)
                    putExtra("package_name", project.packageName)
                    putExtra("language", project.language)
                }
                startActivity(intent)
            }

            holder.itemView.setOnLongClickListener {
                showProjectOptionsDialog(project)
                true
            }
        }

        override fun getItemCount(): Int = projectList.size
    }

    private fun showProjectOptionsDialog(project: Project) {
        val options = arrayOf("Open Project", "Build Studio AI Assistant", "Delete Project")
        AlertDialog.Builder(this)
            .setTitle(project.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val intent = Intent(this, CodeEditorActivity::class.java).apply {
                            putExtra("project_path", project.rootPath)
                            putExtra("project_name", project.name)
                            putExtra("package_name", project.packageName)
                            putExtra("language", project.language)
                        }
                        startActivity(intent)
                    }
                    1 -> {
                        val intent = Intent(this, BuildAiActivity::class.java).apply {
                            putExtra("project_path", project.rootPath)
                        }
                        startActivity(intent)
                    }
                    2 -> {
                        AlertDialog.Builder(this)
                            .setTitle("Delete ${project.name}?")
                            .setMessage("Are you sure you want to permanently delete this project?")
                            .setPositiveButton("Delete") { _, _ ->
                                FileUtil.deleteDir(File(project.rootPath))
                                loadProjects()
                                Toast.makeText(this, "Project deleted", Toast.LENGTH_SHORT).show()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
            }
            .show()
    }
}
