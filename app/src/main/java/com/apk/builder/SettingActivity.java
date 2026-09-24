package com.apk.builder;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.apk.builder.logger.Logger;
import com.apk.builder.model.Project;
import com.build.studio.R;
import com.tyron.compiler.CompilerAsyncTask;
import com.tyron.compiler.CompilerResult;
import java.io.File;

public class SettingActivity extends AppCompatActivity {

    private Project project;
    private RadioGroup rgJavaVersion, rgDexer;
    private Switch swStringFog, swR8Shrinker, swProguard;
    private TextView tvTerminalLog;
    private ScrollView svTerminalLog;
    private Button btnRunBuild;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_build_settings);
        overridePendingTransition(R.anim.animate_slide_left_enter, R.anim.animate_slide_left_exit);

        String path = getIntent().getStringExtra("project_path");
        String name = getIntent().getStringExtra("project_name");
        String pkg = getIntent().getStringExtra("package_name");

        if (path != null) {
            project = new Project(name != null ? name : "Project", pkg != null ? pkg : "com.example", path);
        } else {
            project = findRecentProject();
            if (project == null) {
                project = createDemoProject();
            }
        }

        initViews();
    }

    private Project findRecentProject() {
        try {
            File projectsDir = new File(android.os.Environment.getExternalStorageDirectory(), "BUILD STUDIO/projects");
            if (projectsDir.exists() && projectsDir.isDirectory()) {
                File[] files = projectsDir.listFiles(File::isDirectory);
                if (files != null && files.length > 0) {
                    java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                    for (File f : files) {
                        Project p = Project.loadFromDirectory(f);
                        if (p != null) return p;
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Project createDemoProject() {
        File demoDir = new File(getFilesDir(), "projects/TestApp");
        demoDir.mkdirs();
        File srcDir = new File(demoDir, "app/src/main/java/com/example/testapp");
        srcDir.mkdirs();
        File resValues = new File(demoDir, "app/src/main/res/values");
        resValues.mkdirs();

        FileUtil.writeFile(new File(demoDir, "app/src/main/AndroidManifest.xml").getAbsolutePath(),
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"com.example.testapp\">\n    <application android:label=\"@string/app_name\" android:theme=\"@style/AppTheme\">\n        <activity android:name=\".MainActivity\" android:exported=\"true\">\n            <intent-filter>\n                <action android:name=\"android.intent.action.MAIN\" />\n                <category android:name=\"android.intent.category.LAUNCHER\" />\n            </intent-filter>\n        </activity>\n    </application>\n</manifest>\n");
        FileUtil.writeFile(new File(srcDir, "MainActivity.java").getAbsolutePath(),
                "package com.example.testapp;\nimport android.app.Activity;\nimport android.os.Bundle;\nimport android.widget.TextView;\npublic class MainActivity extends Activity {\n    @Override\n    protected void onCreate(Bundle savedInstanceState) {\n        super.onCreate(savedInstanceState);\n        TextView tv = new TextView(this);\n        tv.setText(\"Hello from TestApp!\");\n        setContentView(tv);\n    }\n}\n");
        FileUtil.writeFile(new File(resValues, "strings.xml").getAbsolutePath(),
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n    <string name=\"app_name\">TestApp</string>\n</resources>\n");
        FileUtil.writeFile(new File(resValues, "styles.xml").getAbsolutePath(),
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n    <style name=\"AppTheme\" parent=\"@android:style/Theme.Material.Light.NoActionBar\" />\n</resources>\n");

        Project p = new Project("TestApp", "com.example.testapp", demoDir.getAbsolutePath());
        p.saveConfig();
        return p;
    }

    private void initViews() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        TextView tvTitle = findViewById(R.id.tv_header_title);
        if (tvTitle != null) tvTitle.setText(project.getName() + " - Build Settings");

        rgJavaVersion = findViewById(R.id.rg_java_version);
        rgDexer = findViewById(R.id.rg_dexer);
        swStringFog = findViewById(R.id.sw_string_fog);
        swR8Shrinker = findViewById(R.id.sw_r8_shrink);

        tvTerminalLog = findViewById(R.id.tv_terminal_output);
        svTerminalLog = findViewById(R.id.sv_terminal);
        btnRunBuild = findViewById(R.id.btn_start_build);

        View btnCopy = findViewById(R.id.btn_copy_log);
        if (btnCopy != null) {
            btnCopy.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null && tvTerminalLog != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("Build Log", tvTerminalLog.getText()));
                    Toast.makeText(this, "Logs copied to clipboard", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnRunBuild != null) {
            btnRunBuild.setOnClickListener(v -> startCompilation());
        }
    }

    private void startCompilation() {
        if (btnRunBuild != null) btnRunBuild.setEnabled(false);
        if (tvTerminalLog != null) tvTerminalLog.setText("");
        logLine("Starting build for " + project.getName() + "...");

        CompilerAsyncTask task = new CompilerAsyncTask(this, project, new CompilerAsyncTask.CompilerCallback() {
            @Override
            public void onProgress(String message, int step, int totalSteps) {
                runOnUiThread(() -> logLine(message));
            }

            @Override
            public void onComplete(CompilerResult result) {
                runOnUiThread(() -> {
                    if (btnRunBuild != null) btnRunBuild.setEnabled(true);
                    if (result.isSuccess()) {
                        logLine("\n[BUILD SUCCESS] APK generated at: " + result.getOutputApk().getAbsolutePath());
                        DialogUtil.showApkUtilityDialog(SettingActivity.this, result.getOutputApk(), project.getName());
                    } else {
                        logLine("\n[BUILD FAILED] " + result.getErrorMessage());
                    }
                });
            }
        });
        task.execute();
    }

    private void logLine(String line) {
        if (tvTerminalLog != null) {
            tvTerminalLog.append(line + "\n");
            if (svTerminalLog != null) {
                svTerminalLog.post(() -> svTerminalLog.fullScroll(View.FOCUS_DOWN));
            }
        }
    }
}
