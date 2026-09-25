package com.apk.builder;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.build.studio.OllamaSettingsActivity;
import com.build.studio.R;
import java.io.File;

public class SettingActivity extends AppCompatActivity {

    private Spinner spFontSize;
    private Switch swWordWrap;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings);
        overridePendingTransition(R.anim.animate_slide_left_enter, R.anim.animate_slide_left_exit);

        prefs = getSharedPreferences("build_studio_settings", Context.MODE_PRIVATE);

        initViews();
    }

    private void initViews() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // 1. Build Studio AI Settings
        View cardAi = findViewById(R.id.card_ai_settings);
        if (cardAi != null) {
            cardAi.setOnClickListener(v -> {
                Intent intent = new Intent(this, OllamaSettingsActivity.class);
                startActivity(intent);
                overridePendingTransition(R.anim.animate_slide_left_enter, R.anim.animate_slide_left_exit);
            });
        }

        // 2. Clear App Cache
        Button btnClearCache = findViewById(R.id.btn_clear_cache);
        if (btnClearCache != null) {
            btnClearCache.setOnClickListener(v -> clearAppCache());
        }

        // 3. Editor Settings
        spFontSize = findViewById(R.id.sp_editor_font_size);
        swWordWrap = findViewById(R.id.sw_word_wrap);

        setupEditorSettings();
    }

    private void setupEditorSettings() {
        String[] sizes = {"12 sp (Small)", "14 sp (Medium)", "16 sp (Default)", "18 sp (Large)", "20 sp (Extra Large)"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, sizes);
        spFontSize.setAdapter(adapter);

        int savedSize = prefs.getInt("editor_font_size", 14);
        int selectionIndex = 1;
        if (savedSize == 12) selectionIndex = 0;
        else if (savedSize == 14) selectionIndex = 1;
        else if (savedSize == 16) selectionIndex = 2;
        else if (savedSize == 18) selectionIndex = 3;
        else if (savedSize == 20) selectionIndex = 4;
        spFontSize.setSelection(selectionIndex);

        spFontSize.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int[] sizeValues = {12, 14, 16, 18, 20};
                prefs.edit().putInt("editor_font_size", sizeValues[position]).apply();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        boolean savedWrap = prefs.getBoolean("editor_word_wrap", false);
        swWordWrap.setChecked(savedWrap);
        swWordWrap.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("editor_word_wrap", isChecked).apply();
            Toast.makeText(this, "Word wrap " + (isChecked ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
        });
    }

    private void clearAppCache() {
        try {
            deleteDir(getCacheDir());
            deleteDir(getCodeCacheDir());
            File externalCache = getExternalCacheDir();
            if (externalCache != null) deleteDir(externalCache);
            Toast.makeText(this, "App cache cleared successfully!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Cache clear failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (String child : children) {
                    boolean success = deleteDir(new File(dir, child));
                    if (!success) return false;
                }
            }
            return dir.delete();
        } else if (dir != null && dir.isFile()) {
            return dir.delete();
        }
        return false;
    }
}
