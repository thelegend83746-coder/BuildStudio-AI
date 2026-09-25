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

        adapter = ProjectListAdapter(this, projectList)
        listView.adapter = adapter

        fab.setOnClickListener {
            startActivity(Intent(this, CreateProjectActivity::class.java))
            Animatoo.animateSlideUp(this)
        }

        threeDotMenu.setOnClickListener { v ->
            showHomePopupMenu(v)
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            if (position in projectList.indices) {
                val proj = projectList[position]
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
        }
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
