package com.apk.builder

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.apk.builder.model.Project
import com.build.studio.R
import com.google.android.material.card.MaterialCardView
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class CreateProjectActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_PICK_ICON = 1002
    }

    enum class ProjectTemplate {
        SIMPLE,
        FAB,
        NAVIGATION_DRAWER,
        FULLSCREEN
    }

    private lateinit var flipperWizard: ViewFlipper
    private lateinit var tvHeaderTitle: TextView
    private lateinit var tvStepIndicator: TextView

    // Step 1: Templates (4 cards from video)
    private lateinit var cardTemplateSimple: MaterialCardView
    private lateinit var cardTemplateFab: MaterialCardView
    private lateinit var cardTemplateNav: MaterialCardView
    private lateinit var cardTemplateFullscreen: MaterialCardView
    private lateinit var rbTemplateSimple: RadioButton
    private lateinit var rbTemplateFab: RadioButton
    private lateinit var rbTemplateNav: RadioButton
    private lateinit var rbTemplateFullscreen: RadioButton
    private lateinit var btnNextStep: Button
    private var selectedTemplate = ProjectTemplate.SIMPLE

    // Step 2: Project Details & Icon
    private lateinit var cardAppIcon: MaterialCardView
    private lateinit var ivAppIcon: ImageView
    private lateinit var tvPickIconLabel: TextView
    private lateinit var etAppName: EditText
    private lateinit var etPackageName: EditText
    private lateinit var etVersionName: EditText
    private lateinit var etVersionCode: EditText
    private lateinit var spMinSdk: Spinner
    private lateinit var spTargetSdk: Spinner
    private lateinit var tvSaveLocation: TextView
    private lateinit var btnPrevStep: Button
    private lateinit var btnCreateProject: Button

    private var pickedIconBytes: ByteArray? = null
    private var userEditedPackageName = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_project)

        initViews()
        setupTemplateSelection()
        setupSpinners()
        setupAppNameAutoPackage()
    }

    private fun initViews() {
        flipperWizard = findViewById(R.id.flipper_wizard)
        tvHeaderTitle = findViewById(R.id.tv_header_title)
        tvStepIndicator = findViewById(R.id.tv_step_indicator)

        findViewById<View>(R.id.btn_back).setOnClickListener {
            if (flipperWizard.displayedChild == 1) {
                showStep(0)
            } else {
                finish()
            }
        }

        // Step 1 views
        cardTemplateSimple = findViewById(R.id.card_template_simple)
        cardTemplateFab = findViewById(R.id.card_template_fab)
        cardTemplateNav = findViewById(R.id.card_template_nav)
        cardTemplateFullscreen = findViewById(R.id.card_template_fullscreen)
        rbTemplateSimple = findViewById(R.id.rb_template_simple)
        rbTemplateFab = findViewById(R.id.rb_template_fab)
        rbTemplateNav = findViewById(R.id.rb_template_nav)
        rbTemplateFullscreen = findViewById(R.id.rb_template_fullscreen)
        btnNextStep = findViewById(R.id.btn_next_step)

        btnNextStep.setOnClickListener {
            showStep(1)
        }

        // Step 2 views
        cardAppIcon = findViewById(R.id.card_app_icon)
        ivAppIcon = findViewById(R.id.iv_app_icon)
        tvPickIconLabel = findViewById(R.id.tv_pick_icon_label)
        etAppName = findViewById(R.id.et_app_name)
        etPackageName = findViewById(R.id.et_package_name)
        etVersionName = findViewById(R.id.et_version_name)
        etVersionCode = findViewById(R.id.et_version_code)
        spMinSdk = findViewById(R.id.sp_min_sdk)
        spTargetSdk = findViewById(R.id.sp_target_sdk)
        tvSaveLocation = findViewById(R.id.tv_save_location)
        btnPrevStep = findViewById(R.id.btn_prev_step)
        btnCreateProject = findViewById(R.id.btn_create_project)

        btnPrevStep.setOnClickListener {
            showStep(0)
        }

        cardAppIcon.setOnClickListener {
            pickCustomIcon()
        }

        btnCreateProject.setOnClickListener {
            createProjectAndLaunch()
        }
    }

    private fun showStep(stepIndex: Int) {
        flipperWizard.displayedChild = stepIndex
        if (stepIndex == 0) {
            tvHeaderTitle.text = "Choose Template"
            tvStepIndicator.text = "Step 1 of 2"
        } else {
            tvHeaderTitle.text = "Configure Project"
            tvStepIndicator.text = "Step 2 of 2"
        }
    }

    private fun setupTemplateSelection() {
        val select = { t: ProjectTemplate ->
            selectedTemplate = t
            rbTemplateSimple.isChecked = (t == ProjectTemplate.SIMPLE)
            rbTemplateFab.isChecked = (t == ProjectTemplate.FAB)
            rbTemplateNav.isChecked = (t == ProjectTemplate.NAVIGATION_DRAWER)
            rbTemplateFullscreen.isChecked = (t == ProjectTemplate.FULLSCREEN)

            val activeColor = Color.parseColor("#5E43F3")
            val inactiveColor = Color.parseColor("#E2E8F0")

            cardTemplateSimple.strokeColor = if (t == ProjectTemplate.SIMPLE) activeColor else inactiveColor
            cardTemplateFab.strokeColor = if (t == ProjectTemplate.FAB) activeColor else inactiveColor
            cardTemplateNav.strokeColor = if (t == ProjectTemplate.NAVIGATION_DRAWER) activeColor else inactiveColor
            cardTemplateFullscreen.strokeColor = if (t == ProjectTemplate.FULLSCREEN) activeColor else inactiveColor
        }

        cardTemplateSimple.setOnClickListener { select(ProjectTemplate.SIMPLE) }
        rbTemplateSimple.setOnClickListener { select(ProjectTemplate.SIMPLE) }

        cardTemplateFab.setOnClickListener { select(ProjectTemplate.FAB) }
        rbTemplateFab.setOnClickListener { select(ProjectTemplate.FAB) }

        cardTemplateNav.setOnClickListener { select(ProjectTemplate.NAVIGATION_DRAWER) }
        rbTemplateNav.setOnClickListener { select(ProjectTemplate.NAVIGATION_DRAWER) }

        cardTemplateFullscreen.setOnClickListener { select(ProjectTemplate.FULLSCREEN) }
        rbTemplateFullscreen.setOnClickListener { select(ProjectTemplate.FULLSCREEN) }

        select(ProjectTemplate.SIMPLE)
    }

    private fun setupSpinners() {
        val sdkOptions = arrayOf(
            "API 21 (Android 5.0 Lollipop)",
            "API 24 (Android 7.0 Nougat)",
            "API 26 (Android 8.0 Oreo)",
            "API 28 (Android 9.0 Pie)",
            "API 30 (Android 11)",
            "API 33 (Android 13)",
            "API 34 (Android 14 UpsideDownCake)"
        )

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sdkOptions)
        spMinSdk.adapter = adapter
        spTargetSdk.adapter = adapter

        spMinSdk.setSelection(2) // API 26
        spTargetSdk.setSelection(6) // API 34
    }

    private fun setupAppNameAutoPackage() {
        etPackageName.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) userEditedPackageName = true
        }

        etAppName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val clean = s?.toString()?.lowercase()?.replace(Regex("[^a-z0-9]"), "") ?: ""
                if (!userEditedPackageName) {
                    etPackageName.setText(if (clean.isEmpty()) "com.example.app" else "com.example.$clean")
                }
                tvSaveLocation.text = "/storage/emulated/0/BUILDSTUDIO/${if (clean.isEmpty()) "app" else clean}"
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun pickCustomIcon() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
        }
        startActivityForResult(intent, REQUEST_PICK_ICON)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_ICON && resultCode == RESULT_OK && data?.data != null) {
            try {
                contentResolver.openInputStream(data.data!!)?.use { inputStream ->
                    val bmp = BitmapFactory.decodeStream(inputStream)
                    if (bmp != null) {
                        ivAppIcon.setImageBitmap(bmp)
                        tvPickIconLabel.visibility = View.GONE
                        val stream = ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)
                        pickedIconBytes = stream.toByteArray()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to load icon: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createProjectAndLaunch() {
        val appName = etAppName.text.toString().trim()
        val packageName = etPackageName.text.toString().trim()

        if (appName.isEmpty()) {
            etAppName.error = "App name is required"
            return
        }
        if (packageName.isEmpty()) {
            etPackageName.error = "Package name is required"
            return
        }

        val projectsDir = File("/storage/emulated/0/test-folder/projects")
        projectsDir.mkdirs()

        val safeName = appName.replace(Regex("[^A-Za-z0-9_-]"), "_")
        var projectDir = File(projectsDir, safeName)
        var count = 1
        while (projectDir.exists()) {
            projectDir = File(projectsDir, "${safeName}_$count")
            count++
        }
        projectDir.mkdirs()

        val project = Project(appName, packageName, projectDir.absolutePath).apply {
            minSdk = 26
            targetSdk = 34
            versionName = etVersionName.text.toString().trim().ifEmpty { "1.0" }
            versionCode = etVersionCode.text.toString().trim().toIntOrNull() ?: 1
            language = "Kotlin"
        }

        val pkgPath = packageName.replace('.', '/')
        val ktSrcDir = File(projectDir, "app/src/main/kotlin/$pkgPath").apply { mkdirs() }
        val resDir = File(projectDir, "app/src/main/res")
        val layoutDir = File(resDir, "layout").apply { mkdirs() }
        val valuesDir = File(resDir, "values").apply { mkdirs() }
        val drawableDir = File(resDir, "drawable").apply { mkdirs() }

        if (pickedIconBytes != null) {
            val iconFile = File(projectDir, "app_icon.png")
            FileOutputStream(iconFile).use { it.write(pickedIconBytes!!) }
            project.iconPath = iconFile.absolutePath
            val resIcon = File(drawableDir, "app_icon.png")
            FileOutputStream(resIcon).use { it.write(pickedIconBytes!!) }
        }

        // AndroidManifest.xml
        FileUtil.writeFile(
            File(projectDir, "app/src/main/AndroidManifest.xml").absolutePath,
            """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="$packageName">

    <application
        android:allowBackup="true"
        android:label="@string/app_name"
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
""".trimIndent()
        )

        // MainActivity.kt
        FileUtil.writeFile(
            File(ktSrcDir, "MainActivity.kt").absolutePath,
            """package $packageName

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tvWelcome = findViewById<TextView>(R.id.tv_welcome)
        val btnAction = findViewById<Button>(R.id.btn_action)

        btnAction?.setOnClickListener {
            Toast.makeText(this, "Welcome to $appName! 🚀", Toast.LENGTH_SHORT).show()
            tvWelcome?.text = "Running on Native Android!"
        }
    }
}
""".trimIndent()
        )

        // activity_main.xml
        FileUtil.writeFile(
            File(layoutDir, "activity_main.xml").absolutePath,
            """<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:gravity="center"
    android:background="#FFFFFF"
    android:padding="24dp">

    <TextView
        android:id="@+id/tv_welcome"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Welcome to $appName"
        android:textColor="#0F172A"
        android:textSize="22sp"
        android:textStyle="bold"
        android:gravity="center"
        android:layout_marginBottom="20dp" />

    <Button
        android:id="@+id/btn_action"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Click Me"
        android:textAllCaps="false"
        android:backgroundTint="#5E43F3"
        android:textColor="#FFFFFF" />

</LinearLayout>
""".trimIndent()
        )

        // strings.xml
        FileUtil.writeFile(
            File(valuesDir, "strings.xml").absolutePath,
            """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">$appName</string>
</resources>
""".trimIndent()
        )

        // styles.xml
        FileUtil.writeFile(
            File(valuesDir, "styles.xml").absolutePath,
            """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="AppTheme" parent="android:Theme.Material.Light.NoActionBar">
        <item name="android:colorPrimary">#5E43F3</item>
    </style>
</resources>
""".trimIndent()
        )

        project.saveConfig()

        Toast.makeText(this, "Project created successfully!", Toast.LENGTH_SHORT).show()

        val intent = Intent(this, CodeEditorActivity::class.java).apply {
            putExtra("project_path", project.rootPath)
            putExtra("project_name", project.name)
            putExtra("package_name", project.packageName)
            putExtra("language", project.language)
        }
        startActivity(intent)
        finish()
    }
}
