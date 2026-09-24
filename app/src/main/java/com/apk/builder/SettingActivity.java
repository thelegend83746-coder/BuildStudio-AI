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
            project = new Project("TestApp", "com.example.test", getFilesDir().getAbsolutePath());
        }

        initViews();
    }

    private void initViews() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        TextView tvTitle = findViewById(R.id.tv_toolbar_title);
        if (tvTitle != null) tvTitle.setText(project.getName() + " - Build Settings");

        rgJavaVersion = findViewById(R.id.rg_settings_java);
        rgDexer = findViewById(R.id.rg_settings_dexer);
        swStringFog = findViewById(R.id.sw_stringfog);
        swR8Shrinker = findViewById(R.id.sw_r8);
        swProguard = findViewById(R.id.sw_proguard);

        tvTerminalLog = findViewById(R.id.tv_terminal_output);
        svTerminalLog = findViewById(R.id.sv_terminal);
        btnRunBuild = findViewById(R.id.btn_start_compilation);

        findViewById(R.id.btn_copy_terminal).setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && tvTerminalLog != null) {
                cm.setPrimaryClip(ClipData.newPlainText("Build Log", tvTerminalLog.getText()));
                Toast.makeText(this, "Logs copied to clipboard", Toast.LENGTH_SHORT).show();
            }
        });

        btnRunBuild.setOnClickListener(v -> startCompilation());
    }

    private void startCompilation() {
        btnRunBuild.setEnabled(false);
        tvTerminalLog.setText("");
        logLine("Starting build for " + project.getName() + "...");

        CompilerAsyncTask task = new CompilerAsyncTask(this, project, new CompilerAsyncTask.CompilerCallback() {
            @Override
            public void onProgress(String message, int step, int totalSteps) {
                runOnUiThread(() -> logLine(message));
            }

            @Override
            public void onCompleted(CompilerResult result) {
                runOnUiThread(() -> {
                    btnRunBuild.setEnabled(true);
                    if (result.isSuccess()) {
                        logLine("\n[BUILD SUCCESS] APK generated at: " + result.getApkFile().getAbsolutePath());
                        DialogUtil.showApkUtilityDialog(SettingActivity.this, result.getApkFile(), project.getName());
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
