package com.build.studio

import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.apk.builder.FileUtil
import com.apk.builder.model.Project
import com.blogspot.atifsoftwares.animatoolib.Animatoo
import com.google.android.material.textfield.TextInputLayout
import java.io.File
import java.io.FileOutputStream

class CreateProjectActivity : AppCompatActivity() {

    // Step containers
    private lateinit var chooseTemplateBg: View
    private lateinit var applicationInfoBg: View

    // Bottom navigation
    private lateinit var tvExitPrevious: TextView
    private lateinit var createBtn: View
    private lateinit var tvCreateBtn: TextView

    // Template buttons
    private lateinit var template1Btn: LinearLayout
    private lateinit var template2Btn: LinearLayout
    private lateinit var template3Btn: LinearLayout
    private lateinit var template4Btn: LinearLayout

    // Configure inputs
    private lateinit var etAppName: EditText
    private lateinit var etPackageName: EditText
    private lateinit var etMinSdk: EditText
    private lateinit var etTargetSdk: EditText
    private lateinit var tilAppName: TextInputLayout
    private lateinit var tilPackageName: TextInputLayout
    private lateinit var tilMinSdk: TextInputLayout
    private lateinit var tilTargetSdk: TextInputLayout
    private lateinit var ivLogoPreview: ImageView

    private var currentStep = 1 // 1 = Choose Template, 2 = Configure Project
    private var selectedTemplate: String = "simple" // default template
    private var pickedLogoBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.create_project)

        initViews()
        setupTemplateSelection()
        setupBottomNavigation()
        setupInputWatchers()

        // Initial screen: Step 1 (Choose Template)
        showStep(1)
    }

    private fun initViews() {
        chooseTemplateBg = findViewById(R.id.choose_template_bg) ?: findViewById(R.id.template_bg)
        applicationInfoBg = findViewById(R.id.application_info_bg)

        tvExitPrevious = findViewById(R.id.TV_EXIT_PREVIOUS)
        createBtn = findViewById(R.id.create_btn)
        tvCreateBtn = findViewById(R.id.textview5)

        template1Btn = findViewById(R.id.template_1_button)
        template2Btn = findViewById(R.id.template_2_button)
        template3Btn = findViewById(R.id.template_3_button)
        template4Btn = findViewById(R.id.template_4_button)

        etAppName = findViewById(R.id.edittext1)
        etPackageName = findViewById(R.id.edittext2)
        etMinSdk = findViewById(R.id.edittext_minsdk)
        etTargetSdk = findViewById(R.id.edittext_targetsdk)

        tilAppName = findViewById(R.id.textinputlayout1)
        tilPackageName = findViewById(R.id.textinputlayout2)
        tilMinSdk = findViewById(R.id.textinputlayout_minsdk)
        tilTargetSdk = findViewById(R.id.textinputlayout_targetsdk)

        ivLogoPreview = findViewById(R.id.logo_preview)

        findViewById<View>(R.id.logo_card)?.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
            }
            startActivityForResult(Intent.createChooser(intent, "Select App Logo"), 101)
        }
    }

    private fun setupTemplateSelection() {
        // Highlight default template 1
        highlightTemplate(template1Btn, true)
        highlightTemplate(template2Btn, false)
        highlightTemplate(template3Btn, false)
        highlightTemplate(template4Btn, false)

        template1Btn.setOnClickListener {
            selectedTemplate = "simple"
            selectTemplateButton(template1Btn)
        }

        template2Btn.setOnClickListener {
            selectedTemplate = "fab"
            selectTemplateButton(template2Btn)
        }

        template3Btn.setOnClickListener {
            selectedTemplate = "nav_drawer"
            selectTemplateButton(template3Btn)
        }

        template4Btn.setOnClickListener {
            selectedTemplate = "fullscreen"
            selectTemplateButton(template4Btn)
        }
    }

    private fun selectTemplateButton(target: LinearLayout) {
        highlightTemplate(template1Btn, target == template1Btn)
        highlightTemplate(template2Btn, target == template2Btn)
        highlightTemplate(template3Btn, target == template3Btn)
        highlightTemplate(template4Btn, target == template4Btn)
        animateViewAlpha(target)
    }

    private fun highlightTemplate(view: LinearLayout, isSelected: Boolean) {
        val density = resources.displayMetrics.density
        val radius = 16f * density
        val gd = GradientDrawable().apply {
            cornerRadius = radius
            if (isSelected) {
                setColor(Color.parseColor("#F5F3FF")) // Soft light purple tint
                setStroke((2.5f * density).toInt(), Color.parseColor("#5B53FE")) // Vibrant Purple Border
            } else {
                setColor(Color.WHITE)
                setStroke((1f * density).toInt(), Color.parseColor("#E2E8F0")) // Slate light border
            }
        }
        view.background = gd
        view.elevation = if (isSelected) 6f * density else 1f * density
    }

    private fun setupBottomNavigation() {
        // Left button (EXIT / PREVIOUS)
        tvExitPrevious.setOnClickListener {
            if (currentStep == 1) {
                finish()
                Animatoo.animateSlideDown(this)
            } else {
                // Go back to Step 1 (Choose Template)
                showStep(1)
            }
        }

        // Right button (Next / Create)
        createBtn.setOnClickListener {
            if (currentStep == 1) {
                if (selectedTemplate.isEmpty()) {
                    Toast.makeText(this, "Please select a template first", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                // Transition to Step 2 (Configure Project)
                showStep(2)
            } else {
                // Step 2: Validate and Create
                validateAndCreateProject()
            }
        }
    }

    private fun showStep(step: Int) {
        currentStep = step
        if (step == 1) {
            chooseTemplateBg.visibility = View.VISIBLE
            applicationInfoBg.visibility = View.GONE
            animateViewAlpha(chooseTemplateBg)

            tvExitPrevious.text = "EXIT"
            tvCreateBtn.text = "Next"
        } else {
            chooseTemplateBg.visibility = View.GONE
            applicationInfoBg.visibility = View.VISIBLE
            animateViewAlpha(applicationInfoBg)

            tvExitPrevious.text = "PREVIOUS"
            tvCreateBtn.text = "Create"
            etAppName.requestFocus()
        }
    }

    private fun animateViewAlpha(v: View) {
        ObjectAnimator.ofFloat(v, "alpha", 0.4f, 1.0f).apply {
            duration = 260
            start()
        }
    }

    private fun setupInputWatchers() {
        etAppName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val name = s?.toString()?.trim() ?: ""
                tilAppName.error = null
                val clean = name.lowercase().replace("[^a-z0-9]".toRegex(), "")
                if (clean.isNotEmpty()) {
                    etPackageName.setText("com.buildstudio.$clean")
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        etPackageName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                tilPackageName.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        etMinSdk.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                tilMinSdk.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        etTargetSdk.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                tilTargetSdk.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun validateAndCreateProject() {
        val appName = etAppName.text.toString().trim()
        var pkgName = etPackageName.text.toString().trim()
        val minSdkStr = etMinSdk.text.toString().trim()
        val targetSdkStr = etTargetSdk.text.toString().trim()

        tilAppName.error = null
        tilPackageName.error = null
        tilMinSdk.error = null
        tilTargetSdk.error = null

        if (appName.isEmpty()) {
            tilAppName.error = "Please enter an Application Name"
            etAppName.requestFocus()
            return
        }

        if (pkgName.isEmpty()) {
            val clean = appName.lowercase().replace("[^a-z0-9]".toRegex(), "")
            pkgName = "com.buildstudio." + (if (clean.isNotEmpty()) clean else "app")
            etPackageName.setText(pkgName)
        }

        if (!pkgName.contains(".") || pkgName.endsWith(".") || pkgName.startsWith(".")) {
            tilPackageName.error = "Enter a valid package name (e.g. com.buildstudio.app)"
            etPackageName.requestFocus()
            return
        }

        val minSdk = minSdkStr.toIntOrNull() ?: 21
        val targetSdk = targetSdkStr.toIntOrNull() ?: 34

        createProjectFiles(appName, pkgName, minSdk, targetSdk, selectedTemplate)
    }

    private fun createProjectFiles(
        appName: String,
        pkgName: String,
        minSdk: Int,
        targetSdk: Int,
        template: String
    ) {
        try {
            // Priority 1: App-internal storage (/data/user/0/com.buildstudio/files/projects/)
            var saveBase = File(filesDir, "projects")
            if (!saveBase.exists()) {
                saveBase.mkdirs()
            }
            // Fallback to external if internal cannot be created
            if (!saveBase.exists() || !saveBase.canWrite()) {
                saveBase = File("/storage/emulated/0/.BUILD STUDIO")
                if (!saveBase.exists()) saveBase.mkdirs()
                if (!saveBase.canWrite()) {
                    saveBase = File("/storage/emulated/0/test-folder/projects").apply { mkdirs() }
                }
            }

            val projectDir = File(saveBase, appName)
            if (projectDir.exists()) {
                Toast.makeText(this, "A project named '$appName' already exists", Toast.LENGTH_SHORT).show()
                return
            }

            projectDir.mkdirs()

            // Standard Android folders
            val srcDir = File(projectDir, "app/src/main/java/" + pkgName.replace(".", "/")).apply { mkdirs() }
            val resDir = File(projectDir, "app/src/main/res").apply { mkdirs() }
            val layoutDir = File(resDir, "layout").apply { mkdirs() }
            val valuesDir = File(resDir, "values").apply { mkdirs() }
            val drawableDir = File(resDir, "drawable").apply { mkdirs() }

            // Save optional custom icon
            if (pickedLogoBitmap != null) {
                try {
                    val iconFile = File(projectDir, "icon.png")
                    FileOutputStream(iconFile).use { fos ->
                        pickedLogoBitmap?.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // strings.xml
            FileUtil.writeFile(
                File(valuesDir, "strings.xml").absolutePath,
                """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">$appName</string>
</resources>
"""
            )

            // colors.xml
            FileUtil.writeFile(
                File(valuesDir, "colors.xml").absolutePath,
                """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="colorPrimary">#5B53FE</color>
    <color name="colorPrimaryDark">#4841D6</color>
    <color name="colorAccent">#5B53FE</color>
</resources>
"""
            )

            // styles.xml
            FileUtil.writeFile(
                File(valuesDir, "styles.xml").absolutePath,
                """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="AppTheme" parent="android:Theme.Material.Light.NoActionBar">
        <item name="android:colorPrimary">@color/colorPrimary</item>
        <item name="android:colorPrimaryDark">@color/colorPrimaryDark</item>
        <item name="android:colorAccent">@color/colorAccent</item>
    </style>
</resources>
"""
            )

            // Layout based on template
            val layoutContent = when (template) {
                "fab" -> """<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:gravity="center"
        android:orientation="vertical">

        <TextView
            android:id="@+id/tv_title"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Welcome to $appName"
            android:textSize="20sp"
            android:textStyle="bold" />
    </LinearLayout>

    <com.google.android.material.floatingactionbutton.FloatingActionButton
        android:id="@+id/fab"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom|end"
        android:layout_margin="16dp"
        app:srcCompat="@android:drawable/ic_input_add" />

</androidx.coordinatorlayout.widget.CoordinatorLayout>
"""
                "nav_drawer" -> """<?xml version="1.0" encoding="utf-8"?>
<androidx.drawerlayout.widget.DrawerLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/drawer_layout"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:gravity="center"
        android:orientation="vertical">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Drawer App: $appName"
            android:textSize="20sp"
            android:textStyle="bold" />
    </LinearLayout>

    <LinearLayout
        android:layout_width="280dp"
        android:layout_height="match_parent"
        android:layout_gravity="start"
        android:background="#FFFFFF"
        android:orientation="vertical"
        android:padding="16dp">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Navigation Menu"
            android:textSize="18sp"
            android:textStyle="bold" />
    </LinearLayout>

</androidx.drawerlayout.widget.DrawerLayout>
"""
                "fullscreen" -> """<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#000000">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:text="Fullscreen $appName"
        android:textColor="#FFFFFF"
        android:textSize="24sp"
        android:textStyle="bold" />

</FrameLayout>
"""
                else -> """<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:gravity="center"
    android:orientation="vertical">

    <TextView
        android:id="@+id/tv_welcome"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Welcome to $appName!"
        android:textSize="20sp"
        android:textStyle="bold" />

</LinearLayout>
"""
            }

            FileUtil.writeFile(File(layoutDir, "activity_main.xml").absolutePath, layoutContent)

            // Generate MainActivity.java
            val javaContent = """package $pkgName;

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
            FileUtil.writeFile(File(srcDir, "MainActivity.java").absolutePath, javaContent)

            // AndroidManifest.xml
            val manifestContent = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="$pkgName">

    <application
        android:allowBackup="true"
        android:label="$appName"
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
            FileUtil.writeFile(File(projectDir, "app/src/main/AndroidManifest.xml").absolutePath, manifestContent)

            // Root build.gradle
            FileUtil.writeFile(
                File(projectDir, "build.gradle").absolutePath,
                """buildscript {
    repositories {
        google()
        mavenCentral()
    }
}
allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
"""
            )

            // app/build.gradle
            FileUtil.writeFile(
                File(projectDir, "app/build.gradle").absolutePath,
                """apply plugin: 'com.android.application'

android {
    compileSdkVersion 34
    defaultConfig {
        applicationId "$pkgName"
        minSdkVersion $minSdk
        targetSdkVersion $targetSdk
        versionCode 1
        versionName "1.0"
    }
}
"""
            )

            // settings.gradle
            FileUtil.writeFile(
                File(projectDir, "settings.gradle").absolutePath,
                """include ':app'
rootProject.name = "$appName"
"""
            )

            // Save project metadata via project.json
            val project = Project(appName, pkgName, projectDir.absolutePath).apply {
                this.minSdk = minSdk
                this.targetSdk = targetSdk
                this.language = "Java"
                saveConfig()
            }

            Toast.makeText(this, "Project '$appName' created successfully! 🚀", Toast.LENGTH_SHORT).show()

            // Directly open EditorActivity with all necessary extras
            val intent = Intent(this, EditorActivity::class.java).apply {
                putExtra("project_path", projectDir.absolutePath)
                putExtra("path", projectDir.absolutePath)
                putExtra("fullPath", projectDir.absolutePath)
                putExtra("project_name", appName)
                putExtra("project", appName)
                putExtra("package_name", pkgName)
            }
            startActivity(intent)
            Animatoo.animateSlideUp(this)
            finish()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error creating project: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101 && resultCode == RESULT_OK && data?.data != null) {
            val uri: Uri = data.data!!
            try {
                // Validate strict PNG format by inspecting the 8-byte PNG signature: 89 50 4E 47 0D 0A 1A 0A
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
                    pickedLogoBitmap = null
                    ivLogoPreview.setImageResource(R.drawable.default_image)
                    Toast.makeText(this, "Invalid PNG format required", Toast.LENGTH_LONG).show()
                    return
                }

                // Strictly decoded only after PNG magic verification
                contentResolver.openInputStream(uri)?.use { stream ->
                    pickedLogoBitmap = BitmapFactory.decodeStream(stream)
                    if (pickedLogoBitmap != null) {
                        ivLogoPreview.setImageBitmap(pickedLogoBitmap)
                        Toast.makeText(this, "PNG icon selected ✓", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Invalid PNG format required", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                pickedLogoBitmap = null
                ivLogoPreview.setImageResource(R.drawable.default_image)
                Toast.makeText(this, "Invalid PNG format required", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onBackPressed() {
        if (currentStep == 2) {
            showStep(1)
        } else {
            super.onBackPressed()
            Animatoo.animateSlideDown(this)
        }
    }
}
