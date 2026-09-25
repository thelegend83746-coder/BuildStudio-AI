package com.build.studio

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.blogspot.atifsoftwares.animatoolib.Animatoo
import com.apk.builder.DialogUtil
import com.apk.builder.logger.Logger
import com.apk.builder.model.Project
import com.tyron.compiler.CompilerAsyncTask
import java.io.File

class BuildSettingsActivity : AppCompatActivity() {

    private lateinit var project: Project
    private lateinit var rgJavaVer: RadioGroup
    private lateinit var rgDexer: RadioGroup
    private lateinit var swStringFog: Switch
    private lateinit var swR8: Switch
    private lateinit var tvTerminal: TextView
    private lateinit var svTerminal: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_build_settings)

        val projectPath = intent.getStringExtra("project_path")
        if (projectPath == null) {
            finish(); Animatoo.animateSlideRight(this)
            return
        }

        project = Project.loadFromDirectory(File(projectPath)) ?: Project("App", "com.example.app", projectPath)

        initViews()
        loadSettings()
    }

    private fun initViews() {
        findViewById<View>(R.id.btn_back).setOnClickListener { finish(); Animatoo.animateSlideRight(this) }

        rgJavaVer = findViewById(R.id.rg_java_version)
        rgDexer = findViewById(R.id.rg_dexer)
        swStringFog = findViewById(R.id.sw_string_fog)
        swR8 = findViewById(R.id.sw_r8_shrink)
        tvTerminal = findViewById(R.id.tv_terminal_output)
        svTerminal = findViewById(R.id.sv_terminal)

        findViewById<View>(R.id.btn_copy_log)?.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Terminal Log", tvTerminal.text))
            Toast.makeText(this, "Log copied", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btn_start_build).setOnClickListener {
            saveSettings()
            startBuild()
        }
    }

    private fun loadSettings() {
        when (project.javaVersion) {
            "1.6" -> findViewById<RadioButton>(R.id.rb_java_6)?.isChecked = true
            "1.7" -> findViewById<RadioButton>(R.id.rb_java_7)?.isChecked = true
            else -> findViewById<RadioButton>(R.id.rb_java_8)?.isChecked = true
        }

        if (project.dexer.equals("Dx", ignoreCase = true)) {
            findViewById<RadioButton>(R.id.rb_dexer_dx)?.isChecked = true
        } else {
            findViewById<RadioButton>(R.id.rb_dexer_d8)?.isChecked = true
        }

        swStringFog.isChecked = project.stringFog
        swR8.isChecked = project.r8Shrink
    }

    private fun saveSettings() {
        val selectedJavaId = rgJavaVer.checkedRadioButtonId
        project.javaVersion = when (selectedJavaId) {
            R.id.rb_java_6 -> "1.6"
            R.id.rb_java_7 -> "1.7"
            else -> "1.8"
        }

        val selectedDexerId = rgDexer.checkedRadioButtonId
        project.dexer = if (selectedDexerId == R.id.rb_dexer_dx) "Dx" else "D8"

        project.stringFog = swStringFog.isChecked
        project.r8Shrink = swR8.isChecked
        project.saveConfig()
    }

    private fun startBuild() {
        tvTerminal.text = "Starting build for ${project.name}...\n"
        Logger.setListener { line ->
            runOnUiThread {
                tvTerminal.append(line + "\n")
                svTerminal.post { svTerminal.fullScroll(View.FOCUS_DOWN) }
            }
        }

        val task = CompilerAsyncTask(this, project) { result ->
            runOnUiThread {
                if (result.isSuccess && result.apkFile != null) {
                    tvTerminal.append("\n✅ Build Completed: ${result.apkFile.name}\n")
                    DialogUtil.showApkUtilityDialog(this, result.apkFile, project.name)
                } else {
                    tvTerminal.append("\n❌ Build Failed: ${result.errorMessage}\n")
                    DialogUtil.showCompilerErrorDialog(this, result.errorMessage, project.rootPath, null)
                }
            }
        }
        task.execute()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        Animatoo.animateSlideRight(this)
    }
}
