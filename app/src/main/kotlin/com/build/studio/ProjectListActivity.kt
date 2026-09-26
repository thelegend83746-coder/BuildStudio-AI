package com.build.studio

import android.app.Activity
import android.app.Dialog
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.apk.builder.FileUtil
import com.apk.builder.model.Project
import com.blogspot.atifsoftwares.animatoolib.Animatoo
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ProjectListActivity : AppCompatActivity() {

    companion object {
        private const val REQ_PICK_EDIT_ICON = 201
        private const val REQ_PICK_IMPORT_ZIP = 202
        private const val REQ_PICK_IMPORT_TREE = 203
    }

    private lateinit var listView: ListView
    private lateinit var tvNoProjects: TextView
    private val projectList = mutableListOf<Project>()
    private lateinit var adapter: ProjectListAdapter

    // Edit Project state
    private var activeEditingProject: Project? = null
    private var pendingEditLogoBitmap: Bitmap? = null
    private var ivCurrentEditingLogo: ImageView? = null
    private var editProjectDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.project_list)

        listView = findViewById(R.id.listview1)
        tvNoProjects = findViewById(R.id.no_projects_found)
        val fab = findViewById<FloatingActionButton>(R.id._fab)
        val threeDotMenu = findViewById<ImageView>(R.id.three_dot_menu_image)
        val btnTopAi = findViewById<ImageView>(R.id.btn_top_ai)
        val btnTopImport = findViewById<ImageView>(R.id.btn_top_import)

        adapter = ProjectListAdapter(this, projectList)
        listView.adapter = adapter

        btnTopAi?.setOnClickListener {
            startActivity(Intent(this, BuildAiActivity::class.java))
            Animatoo.animateSlideLeft(this)
        }

        btnTopImport?.setOnClickListener {
            showImportProjectDialog()
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
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
        }

        popupView.findViewById<View>(R.id.b_ai)?.setOnClickListener {
            popupWindow.dismiss()
            startActivity(Intent(this, BuildAiActivity::class.java))
            Animatoo.animateSlideLeft(this)
        }

        popupView.findViewById<View>(R.id.b1)?.setOnClickListener {
            popupWindow.dismiss()
            SettingsBottomSheet.show(this)
        }

        popupView.findViewById<View>(R.id.b2)?.setOnClickListener {
            popupWindow.dismiss()
            startActivity(Intent(this, AboutPageActivity::class.java))
            Animatoo.animateSlideLeft(this)
        }

        // Import Project
        popupView.findViewById<View>(R.id.b3)?.setOnClickListener {
            popupWindow.dismiss()
            showImportProjectDialog()
        }

        popupWindow.showAsDropDown(anchor, 0, 0, Gravity.END)
    }

    private fun showProjectOptionsMenu(project: Project) {
        // "Rename" removed; replaced with "Edit Project Configuration"
        val options = arrayOf(
            "1. Open Project",
            "2. Edit Project Configuration",
            "3. Backup (.zip)",
            "4. Delete Project"
        )
        AlertDialog.Builder(this)
            .setTitle(project.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openProject(project)
                    1 -> showEditProjectDialog(project)
                    2 -> backupProject(project)
                    3 -> promptDeleteProject(project)
                }
            }
            .show()
    }

    // =========================================================================
    // EDIT PROJECT CONFIGURATION (Name, Icon, Package, Min/Target SDK, Versions)
    // =========================================================================
    private fun showEditProjectDialog(project: Project) {
        activeEditingProject = project
        pendingEditLogoBitmap = null

        val dialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(R.layout.dialog_edit_project)
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }
        editProjectDialog = dialog

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

        // Load existing icon preview
        val iconFile = getProjectIconFile(project)
        if (iconFile != null && iconFile.exists()) {
            val bmp = BitmapFactory.decodeFile(iconFile.absolutePath)
            if (bmp != null) ivLogo.setImageBitmap(bmp)
            else ivLogo.setImageResource(R.drawable.ic_launcher)
        } else {
            ivLogo.setImageResource(R.drawable.ic_launcher)
        }

        // Pre-fill inputs
        etName.setText(project.name)
        etPackage.setText(project.packageName)
        etMinSdk.setText(project.minSdk.toString())
        etTargetSdk.setText(project.targetSdk.toString())
        etVersionCode.setText(project.versionCode.toString())
        etVersionName.setText(project.versionName)

        // Change icon trigger
        layoutIconPicker.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
            }
            startActivityForResult(Intent.createChooser(intent, "Select App Icon (PNG required)"), REQ_PICK_EDIT_ICON)
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val newName = etName.text.toString().trim()
            val newPkg = etPackage.text.toString().trim()
            val newMinSdk = etMinSdk.text.toString().trim().toIntOrNull() ?: project.minSdk
            val newTargetSdk = etTargetSdk.text.toString().trim().toIntOrNull() ?: project.targetSdk
            val newVCode = etVersionCode.text.toString().trim().toIntOrNull() ?: project.versionCode
            val newVName = etVersionName.text.toString().trim().ifEmpty { project.versionName }

            if (newName.isEmpty()) {
                Toast.makeText(this, "App Name cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (newPkg.isEmpty()) {
                Toast.makeText(this, "Package Name cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            saveProjectConfiguration(
                project = project,
                newName = newName,
                newPkg = newPkg,
                minSdk = newMinSdk,
                targetSdk = newTargetSdk,
                vCode = newVCode,
                vName = newVName,
                iconBitmap = pendingEditLogoBitmap
            )

            dialog.dismiss()
        }

        dialog.show()
    }

    private fun getProjectIconFile(project: Project): File? {
        val candidates = listOf(
            project.iconPath?.let { File(it) },
            File(project.rootPath, "app/src/main/res/drawable-xhdpi/app_icon.png"),
            File(project.rootPath, "app/src/main/res/drawable/app_icon.png"),
            File(project.rootPath, "app/src/main/res/mipmap-xhdpi/ic_launcher.png"),
            File(project.rootPath, "app/src/main/res/mipmap/ic_launcher.png"),
            File(project.rootPath, "icon.png")
        )
        return candidates.firstOrNull { it != null && it.exists() }
    }

    private fun saveProjectConfiguration(
        project: Project,
        newName: String,
        newPkg: String,
        minSdk: Int,
        targetSdk: Int,
        vCode: Int,
        vName: String,
        iconBitmap: Bitmap?
    ) {
        val oldRootDir = File(project.rootPath)

        // 1. Save icon if modified
        if (iconBitmap != null) {
            try {
                val targets = listOf(
                    File(project.rootPath, "app/src/main/res/drawable-xhdpi/app_icon.png"),
                    File(project.rootPath, "app/src/main/res/drawable/app_icon.png"),
                    File(project.rootPath, "app/src/main/res/mipmap-xhdpi/ic_launcher.png"),
                    File(project.rootPath, "app/src/main/res/mipmap/ic_launcher.png"),
                    File(project.rootPath, "icon.png")
                )
                for (target in targets) {
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { fos ->
                        iconBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    }
                }
                project.iconPath = File(project.rootPath, "app/src/main/res/drawable-xhdpi/app_icon.png").absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Update AndroidManifest.xml
        val manifest = project.manifestFile
        if (manifest.exists()) {
            try {
                var text = manifest.readText()
                text = text.replace(Regex("""package\s*=\s*"[^"]+""""), """package="$newPkg"""")
                if (text.contains("android:minSdkVersion")) {
                    text = text.replace(Regex("""android:minSdkVersion\s*=\s*"[^"]+""""), """android:minSdkVersion="$minSdk"""")
                }
                if (text.contains("android:targetSdkVersion")) {
                    text = text.replace(Regex("""android:targetSdkVersion\s*=\s*"[^"]+""""), """android:targetSdkVersion="$targetSdk"""")
                }
                if (text.contains("android:versionCode")) {
                    text = text.replace(Regex("""android:versionCode\s*=\s*"[^"]+""""), """android:versionCode="$vCode"""")
                }
                if (text.contains("android:versionName")) {
                    text = text.replace(Regex("""android:versionName\s*=\s*"[^"]+""""), """android:versionName="$vName"""")
                }
                manifest.writeText(text)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Update strings.xml (app_name)
        val stringsFiles = listOf(
            File(project.rootPath, "app/src/main/res/values/strings.xml"),
            File(project.rootPath, "src/main/res/values/strings.xml"),
            File(project.rootPath, "res/values/strings.xml")
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

        // 4. Update build.gradle / app/build.gradle
        val buildFiles = listOf(
            File(project.rootPath, "app/build.gradle"),
            File(project.rootPath, "app/build.gradle.kts"),
            File(project.rootPath, "build.gradle")
        )
        for (bf in buildFiles) {
            if (bf.exists()) {
                try {
                    var bText = bf.readText()
                    bText = bText.replace(Regex("""applicationId\s+['"][^'"]+['"]"""), """applicationId "$newPkg"""")
                    bText = bText.replace(Regex("""namespace\s+['"][^'"]+['"]"""), """namespace "$newPkg"""")
                    bText = bText.replace(Regex("""minSdkVersion\s+\d+"""), """minSdkVersion $minSdk""")
                    bText = bText.replace(Regex("""targetSdkVersion\s+\d+"""), """targetSdkVersion $targetSdk""")
                    bText = bText.replace(Regex("""versionCode\s+\d+"""), """versionCode $vCode""")
                    bText = bText.replace(Regex("""versionName\s+['"][^'"]+['"]"""), """versionName "$vName"""")
                    bf.writeText(bText)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // 5. Update Project model fields & saveConfig
        project.name = newName
        project.packageName = newPkg
        project.minSdk = minSdk
        project.targetSdk = targetSdk
        project.versionCode = vCode
        project.versionName = vName
        project.lastModified = System.currentTimeMillis()

        // 6. Rename project folder if name changed
        if (oldRootDir.name != newName) {
            val newDir = File(oldRootDir.parentFile, newName)
            if (!newDir.exists() && oldRootDir.renameTo(newDir)) {
                project.rootPath = newDir.absolutePath
            }
        }

        project.saveConfig()
        loadProjects()
        Toast.makeText(this, "Project configuration saved! ✓", Toast.LENGTH_SHORT).show()
    }

    // =========================================================================
    // IMPORT PROJECT WORKFLOW (ZIP file or Directory from storage)
    // =========================================================================
    private fun showImportProjectDialog() {
        val dialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(R.layout.dialog_import_project)
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }

        dialog.findViewById<View>(R.id.card_import_zip)?.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                putExtra(
                    Intent.EXTRA_MIME_TYPES,
                    arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
                )
            }
            startActivityForResult(Intent.createChooser(intent, "Select Project ZIP"), REQ_PICK_IMPORT_ZIP)
        }

        dialog.findViewById<View>(R.id.card_import_folder)?.setOnClickListener {
            dialog.dismiss()
            showFolderPickerDialog()
        }

        dialog.findViewById<View>(R.id.btn_cancel_import)?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showFolderPickerDialog() {
        val dialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(R.layout.dialog_folder_picker)
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }

        val tvPath = dialog.findViewById<TextView>(R.id.tv_current_folder_path)
        val btnUp = dialog.findViewById<ImageButton>(R.id.btn_folder_up)
        val lvFolders = dialog.findViewById<ListView>(R.id.lv_folders)
        val tvEmpty = dialog.findViewById<TextView>(R.id.tv_empty_folders)
        val btnSelect = dialog.findViewById<Button>(R.id.btn_select_this_folder)
        val btnCancel = dialog.findViewById<Button>(R.id.btn_cancel_folder_picker)
        val btnSystemPicker = dialog.findViewById<Button>(R.id.btn_open_system_picker)

        // Initial directory: /storage/emulated/0
        var currentDir = File("/storage/emulated/0").takeIf { it.exists() && it.canRead() }
            ?: File("/storage/emulated/0/test-folder").takeIf { it.exists() }
            ?: filesDir

        fun refreshList() {
            tvPath.text = currentDir.absolutePath
            val subDirs = currentDir.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }?.sortedBy { it.name.lowercase() } ?: emptyList()
            if (subDirs.isEmpty()) {
                tvEmpty.visibility = View.VISIBLE
                lvFolders.visibility = View.GONE
            } else {
                tvEmpty.visibility = View.GONE
                lvFolders.visibility = View.VISIBLE
                val folderNames = subDirs.map { "📁  ${it.name}" }
                lvFolders.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, folderNames)
                lvFolders.setOnItemClickListener { _, _, position, _ ->
                    if (position in subDirs.indices) {
                        currentDir = subDirs[position]
                        refreshList()
                    }
                }
            }
        }

        btnUp.setOnClickListener {
            val parent = currentDir.parentFile
            if (parent != null && parent.canRead() && parent.absolutePath.startsWith("/storage/emulated/0")) {
                currentDir = parent
                refreshList()
            }
        }

        btnSelect.setOnClickListener {
            dialog.dismiss()
            importProjectFromFolder(currentDir)
        }

        btnSystemPicker.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            startActivityForResult(intent, REQ_PICK_IMPORT_TREE)
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        refreshList()
        dialog.show()
    }

    private fun importProjectFromZip(uri: Uri) {
        val progressDialog = ProgressDialog(this).apply {
            setMessage("Importing project from ZIP...")
            setCancelable(false)
            show()
        }

        Thread {
            try {
                // 1. Temporary extraction folder
                val tempDir = File(cacheDir, "zip_import_${System.currentTimeMillis()}").apply { mkdirs() }

                contentResolver.openInputStream(uri)?.use { inputStream ->
                    ZipInputStream(inputStream).use { zis ->
                        var entry: ZipEntry? = zis.nextEntry
                        while (entry != null) {
                            val outFile = File(tempDir, entry.name)
                            if (outFile.canonicalPath.startsWith(tempDir.canonicalPath)) {
                                if (entry.isDirectory) {
                                    outFile.mkdirs()
                                } else {
                                    outFile.parentFile?.mkdirs()
                                    FileOutputStream(outFile).use { fos ->
                                        zis.copyTo(fos)
                                    }
                                }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }

                // 2. Smart Flattening / Inner Files Detection
                // User requirement: "app me zip ke andar wala files and folder aana chahiye"
                var realProjectRoot = tempDir

                // Search for AndroidManifest.xml or app/ or src/
                val manifests = tempDir.walkTopDown().filter { it.name == "AndroidManifest.xml" }.toList()
                if (manifests.isNotEmpty()) {
                    val bestManifest = manifests.first()
                    var candidate = bestManifest.parentFile // main
                    if (candidate?.name == "main") candidate = candidate?.parentFile // src
                    if (candidate?.name == "src") candidate = candidate?.parentFile // app or root
                    if (candidate?.name == "app") candidate = candidate?.parentFile // project root
                    if (candidate != null && candidate.exists() && candidate.isDirectory) {
                        realProjectRoot = candidate
                    }
                } else {
                    // Check if tempDir has only one single wrapper subfolder
                    val subs = tempDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
                    val files = tempDir.listFiles()?.filter { it.isFile } ?: emptyList()
                    if (subs.size == 1 && files.isEmpty()) {
                        realProjectRoot = subs[0]
                    }
                }

                // 3. Detect Project Name
                var detectedName: String? = null
                val pJson = File(realProjectRoot, "project.json")
                if (pJson.exists()) {
                    try {
                        val jobj = JSONObject(pJson.readText())
                        if (jobj.has("name")) detectedName = jobj.getString("name")
                    } catch (_: Throwable) {}
                }
                if (detectedName.isNullOrEmpty()) {
                    val strFile = listOf(
                        File(realProjectRoot, "app/src/main/res/values/strings.xml"),
                        File(realProjectRoot, "src/main/res/values/strings.xml"),
                        File(realProjectRoot, "res/values/strings.xml")
                    ).firstOrNull { it.exists() }
                    if (strFile != null) {
                        val match = Regex("""<string\s+name\s*=\s*"app_name"[^>]*>(.*?)</string>""").find(strFile.readText())
                        detectedName = match?.groupValues?.get(1)?.trim()
                    }
                }
                if (detectedName.isNullOrEmpty()) {
                    detectedName = realProjectRoot.name.takeIf { it != tempDir.name } ?: "ImportedProject"
                }
                val safeProjName = detectedName.replace(Regex("[^a-zA-Z0-9_]"), "_").ifEmpty { "ImportedProject" }

                // 4. Resolve destination project directory
                val targetBase = resolveWritableProjectsDir()
                var destProjectDir = File(targetBase, safeProjName)
                var count = 1
                while (destProjectDir.exists()) {
                    destProjectDir = File(targetBase, "${safeProjName}_$count")
                    count++
                }
                destProjectDir.mkdirs()

                // 5. Copy extracted files directly into destination directory
                realProjectRoot.copyRecursively(destProjectDir, overwrite = true)
                tempDir.deleteRecursively()

                // 6. Ensure project.json is generated
                val targetPJson = File(destProjectDir, "project.json")
                if (!targetPJson.exists()) {
                    var pkgName = "com.buildstudio.${destProjectDir.name.lowercase().replace(Regex("[^a-z0-9]"), "")}"
                    val manFile = listOf(
                        File(destProjectDir, "app/src/main/AndroidManifest.xml"),
                        File(destProjectDir, "src/main/AndroidManifest.xml"),
                        File(destProjectDir, "AndroidManifest.xml")
                    ).firstOrNull { it.exists() }
                    if (manFile != null) {
                        val mMatch = Regex("""package\s*=\s*"([^"]+)"""").find(manFile.readText())
                        if (mMatch != null) pkgName = mMatch.groupValues[1]
                    }
                    val proj = Project(destProjectDir.name, pkgName, destProjectDir.absolutePath)
                    proj.saveConfig()
                }

                runOnUiThread {
                    progressDialog.dismiss()
                    loadProjects()
                    Toast.makeText(this, "Project '${destProjectDir.name}' imported successfully! ✓", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    progressDialog.dismiss()
                    Toast.makeText(this, "Failed to import ZIP: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun importProjectFromFolder(sourceDir: File) {
        if (!sourceDir.exists() || !sourceDir.isDirectory) {
            Toast.makeText(this, "Invalid folder", Toast.LENGTH_SHORT).show()
            return
        }

        val progressDialog = ProgressDialog(this).apply {
            setMessage("Importing project folder...")
            setCancelable(false)
            show()
        }

        Thread {
            try {
                // Find true root inside folder if nested
                var realRoot = sourceDir
                val manifests = sourceDir.walkTopDown().maxDepth(3).filter { it.name == "AndroidManifest.xml" }.toList()
                if (manifests.isNotEmpty()) {
                    val bestManifest = manifests.first()
                    var candidate = bestManifest.parentFile
                    if (candidate?.name == "main") candidate = candidate?.parentFile
                    if (candidate?.name == "src") candidate = candidate?.parentFile
                    if (candidate?.name == "app") candidate = candidate?.parentFile
                    if (candidate != null && candidate.exists() && candidate.isDirectory) {
                        realRoot = candidate
                    }
                }

                val safeProjName = realRoot.name.replace(Regex("[^a-zA-Z0-9_]"), "_").ifEmpty { "ImportedProject" }
                val targetBase = resolveWritableProjectsDir()

                var destProjectDir = File(targetBase, safeProjName)
                if (sourceDir.absolutePath != destProjectDir.absolutePath) {
                    var count = 1
                    while (destProjectDir.exists()) {
                        destProjectDir = File(targetBase, "${safeProjName}_$count")
                        count++
                    }
                    destProjectDir.mkdirs()
                    realRoot.copyRecursively(destProjectDir, overwrite = true)
                }

                // Ensure project.json
                val pJson = File(destProjectDir, "project.json")
                if (!pJson.exists()) {
                    var pkgName = "com.buildstudio.${destProjectDir.name.lowercase().replace(Regex("[^a-z0-9]"), "")}"
                    val manFile = listOf(
                        File(destProjectDir, "app/src/main/AndroidManifest.xml"),
                        File(destProjectDir, "src/main/AndroidManifest.xml"),
                        File(destProjectDir, "AndroidManifest.xml")
                    ).firstOrNull { it.exists() }
                    if (manFile != null) {
                        val mMatch = Regex("""package\s*=\s*"([^"]+)"""").find(manFile.readText())
                        if (mMatch != null) pkgName = mMatch.groupValues[1]
                    }
                    val proj = Project(destProjectDir.name, pkgName, destProjectDir.absolutePath)
                    proj.saveConfig()
                }

                runOnUiThread {
                    progressDialog.dismiss()
                    loadProjects()
                    Toast.makeText(this, "Project '${destProjectDir.name}' imported! ✓", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    progressDialog.dismiss()
                    Toast.makeText(this, "Import error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun importProjectFromTreeUri(treeUri: Uri) {
        val rootDoc = DocumentFile.fromTreeUri(this, treeUri) ?: return
        val projName = rootDoc.name?.replace(Regex("[^a-zA-Z0-9_]"), "_") ?: "ImportedProject"
        val targetBase = resolveWritableProjectsDir()

        var destProjectDir = File(targetBase, projName)
        var count = 1
        while (destProjectDir.exists()) {
            destProjectDir = File(targetBase, "${projName}_$count")
            count++
        }
        destProjectDir.mkdirs()

        val progressDialog = ProgressDialog(this).apply {
            setMessage("Copying files from system picker...")
            setCancelable(false)
            show()
        }

        Thread {
            try {
                fun copyDocFile(doc: DocumentFile, targetDir: File) {
                    if (doc.isDirectory) {
                        val subTarget = File(targetDir, doc.name ?: "dir").apply { mkdirs() }
                        for (child in doc.listFiles()) {
                            copyDocFile(child, subTarget)
                        }
                    } else if (doc.isFile) {
                        val outFile = File(targetDir, doc.name ?: "file")
                        contentResolver.openInputStream(doc.uri)?.use { input ->
                            FileOutputStream(outFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }

                for (child in rootDoc.listFiles()) {
                    copyDocFile(child, destProjectDir)
                }

                val proj = Project(destProjectDir.name, "com.buildstudio.${destProjectDir.name.lowercase()}", destProjectDir.absolutePath)
                proj.loadConfig()

                runOnUiThread {
                    progressDialog.dismiss()
                    loadProjects()
                    Toast.makeText(this, "Project '${destProjectDir.name}' imported! ✓", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    progressDialog.dismiss()
                    Toast.makeText(this, "System import error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun resolveWritableProjectsDir(): File {
        val candidates = listOf(
            File("/storage/emulated/0/.BUILD STUDIO"),
            File("/storage/emulated/0/test-folder/projects"),
            File(filesDir, "projects"),
            File(getExternalFilesDir(null), "projects")
        )
        for (c in candidates) {
            try {
                if (!c.exists()) c.mkdirs()
                if (c.exists() && c.canWrite()) {
                    val test = File(c, ".test_${System.currentTimeMillis()}")
                    if (test.createNewFile()) {
                        test.delete()
                        return c
                    }
                }
            } catch (_: Throwable) {}
        }
        return File(filesDir, "projects").apply { mkdirs() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data == null) return

        when (requestCode) {
            REQ_PICK_EDIT_ICON -> {
                val uri = data.data ?: return
                try {
                    // Strict PNG validation (8-byte magic header 89 50 4E 47 0D 0A 1A 0A)
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

            REQ_PICK_IMPORT_ZIP -> {
                val uri = data.data ?: return
                importProjectFromZip(uri)
            }

            REQ_PICK_IMPORT_TREE -> {
                val uri = data.data ?: return
                importProjectFromTreeUri(uri)
            }
        }
    }

    private fun backupProject(project: Project) {
        val rootDir = File(project.rootPath)
        if (!rootDir.exists()) {
            Toast.makeText(this, "Project folder not found: ${project.rootPath}", Toast.LENGTH_LONG).show()
            return
        }

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

        val progressDialog = ProgressDialog(this).apply {
            setMessage("Creating backup for ${project.name}...")
            setCancelable(false)
            show()
        }

        Thread {
            try {
                ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                    rootDir.walkTopDown().forEach { f ->
                        if (f.absolutePath == rootDir.absolutePath) return@forEach
                        val relPath = f.relativeTo(rootDir).path.replace('\\', '/')
                        if (relPath.isNotEmpty() && !relPath.startsWith("build/") && !relPath.startsWith(".git/") && relPath != "build" && relPath != ".git") {
                            if (f.isDirectory) {
                                val dirEntry = if (relPath.endsWith("/")) relPath else "$relPath/"
                                try {
                                    zos.putNextEntry(ZipEntry(dirEntry))
                                    zos.closeEntry()
                                } catch (_: Throwable) {}
                            } else {
                                try {
                                    zos.putNextEntry(ZipEntry(relPath))
                                    f.inputStream().use { it.copyTo(zos) }
                                    zos.closeEntry()
                                } catch (_: Throwable) {}
                            }
                        }
                    }
                }

                try {
                    val staticZip = File(finalDir, "${project.name}_backup.zip")
                    FileUtil.copyFile(zipFile, staticZip)
                } catch (_: Throwable) {}

                runOnUiThread {
                    progressDialog.dismiss()
                    showBackupSuccessDialog(project.name, zipFile)
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

    private fun showBackupSuccessDialog(projectName: String, zipFile: File) {
        val sizeMb = String.format(java.util.Locale.US, "%.2f MB", zipFile.length().toDouble() / (1024 * 1024))
        AlertDialog.Builder(this)
            .setTitle("Backup Created ✓")
            .setMessage("Project: $projectName\nSize: $sizeMb\n\nLocation:\n${zipFile.absolutePath}")
            .setPositiveButton("Share / Export") { _, _ ->
                try {
                    val uri = FileProvider.getUriForFile(
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

    private fun promptDeleteProject(project: Project) {
        AlertDialog.Builder(this)
            .setTitle("Delete Project")
            .setMessage("Are you sure you want to delete '${project.name}'?\nThis cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                val dir = File(project.rootPath)
                if (dir.deleteRecursively()) {
                    Toast.makeText(this, "Deleted ${project.name}", Toast.LENGTH_SHORT).show()
                    loadProjects()
                } else {
                    Toast.makeText(this, "Failed to delete project", Toast.LENGTH_SHORT).show()
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

            // Check icon candidates
            val iconCandidates = listOf(
                project.iconPath?.let { File(it) },
                File(project.rootPath, "app/src/main/res/drawable-xhdpi/app_icon.png"),
                File(project.rootPath, "app/src/main/res/drawable/app_icon.png"),
                File(project.rootPath, "app/src/main/res/mipmap-xhdpi/ic_launcher.png"),
                File(project.rootPath, "app/src/main/res/mipmap/ic_launcher.png"),
                File(project.rootPath, "icon.png")
            )
            val iconFile = iconCandidates.firstOrNull { it != null && it.exists() }
            if (iconFile != null) {
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
