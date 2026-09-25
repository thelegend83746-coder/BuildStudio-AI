package com.apk.builder;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import com.apk.builder.model.Project;
import com.build.studio.R;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class CreateProjectActivity extends AppCompatActivity {

    private static final int REQUEST_PICK_ICON = 1002;

    public enum ProjectTemplate {
        SIMPLE,
        FAB,
        NAVIGATION_DRAWER
    }

    private ViewFlipper flipperWizard;
    private TextView tvHeaderTitle, tvStepIndicator;

    // Step 1: Templates
    private CardView cardTemplateSimple, cardTemplateFab, cardTemplateNav;
    private RadioButton rbTemplateSimple, rbTemplateFab, rbTemplateNav;
    private Button btnNextStep;
    private ProjectTemplate selectedTemplate = ProjectTemplate.SIMPLE;

    // Step 2: Project Details & Icon
    private CardView cardAppIcon;
    private ImageView ivAppIcon;
    private TextView tvPickIconLabel;
    private EditText etAppName, etPackageName, etVersionName, etVersionCode;
    private Spinner spMinSdk, spTargetSdk;
    private Button btnPrevStep, btnCreateProject;

    private byte[] pickedIconBytes = null;
    private boolean userEditedPackageName = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_project);
        overridePendingTransition(R.anim.animate_slide_left_enter, R.anim.animate_slide_left_exit);

        initViews();
        setupTemplateSelection();
        setupSpinners();
        setupAppNameAutoPackage();
    }

    private void initViews() {
        flipperWizard = findViewById(R.id.flipper_wizard);
        tvHeaderTitle = findViewById(R.id.tv_header_title);
        tvStepIndicator = findViewById(R.id.tv_step_indicator);

        findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (flipperWizard.getDisplayedChild() == 1) {
                showStep(0);
            } else {
                finish();
            }
        });

        // Step 1 views
        cardTemplateSimple = findViewById(R.id.card_template_simple);
        cardTemplateFab = findViewById(R.id.card_template_fab);
        cardTemplateNav = findViewById(R.id.card_template_nav);
        rbTemplateSimple = findViewById(R.id.rb_template_simple);
        rbTemplateFab = findViewById(R.id.rb_template_fab);
        rbTemplateNav = findViewById(R.id.rb_template_nav);
        btnNextStep = findViewById(R.id.btn_next_step);

        btnNextStep.setOnClickListener(v -> showStep(1));

        // Step 2 views
        cardAppIcon = findViewById(R.id.card_app_icon);
        ivAppIcon = findViewById(R.id.iv_app_icon);
        tvPickIconLabel = findViewById(R.id.tv_pick_icon_label);
        cardAppIcon.setOnClickListener(v -> pickAppIcon());

        etAppName = findViewById(R.id.et_app_name);
        etPackageName = findViewById(R.id.et_package_name);
        etVersionName = findViewById(R.id.et_version_name);
        etVersionCode = findViewById(R.id.et_version_code);
        spMinSdk = findViewById(R.id.sp_min_sdk);
        spTargetSdk = findViewById(R.id.sp_target_sdk);

        btnPrevStep = findViewById(R.id.btn_prev_step);
        btnPrevStep.setOnClickListener(v -> showStep(0));

        btnCreateProject = findViewById(R.id.btn_create_project);
        btnCreateProject.setOnClickListener(v -> handleCreateProject());
    }

    private void showStep(int stepIndex) {
        flipperWizard.setDisplayedChild(stepIndex);
        if (stepIndex == 0) {
            tvHeaderTitle.setText("Create a New Project");
            tvStepIndicator.setText("Step 1 of 2");
        } else {
            tvHeaderTitle.setText("Configure Application");
            tvStepIndicator.setText("Step 2 of 2");
        }
    }

    private void setupTemplateSelection() {
        View.OnClickListener listener = v -> {
            if (v == cardTemplateSimple) {
                selectTemplate(ProjectTemplate.SIMPLE);
            } else if (v == cardTemplateFab) {
                selectTemplate(ProjectTemplate.FAB);
            } else if (v == cardTemplateNav) {
                selectTemplate(ProjectTemplate.NAVIGATION_DRAWER);
            }
        };

        cardTemplateSimple.setOnClickListener(listener);
        cardTemplateFab.setOnClickListener(listener);
        cardTemplateNav.setOnClickListener(listener);

        selectTemplate(ProjectTemplate.SIMPLE);
    }

    private void selectTemplate(ProjectTemplate template) {
        selectedTemplate = template;

        int activeColor = getResources().getColor(R.color.primary_accent);
        int inactiveColor = getResources().getColor(R.color.outline_border);

        // Reset all
        cardTemplateSimple.setStrokeColor(inactiveColor);
        cardTemplateSimple.setStrokeWidth(dpToPx(1));
        rbTemplateSimple.setChecked(false);

        cardTemplateFab.setStrokeColor(inactiveColor);
        cardTemplateFab.setStrokeWidth(dpToPx(1));
        rbTemplateFab.setChecked(false);

        cardTemplateNav.setStrokeColor(inactiveColor);
        cardTemplateNav.setStrokeWidth(dpToPx(1));
        rbTemplateNav.setChecked(false);

        // Highlight selected
        switch (template) {
            case SIMPLE:
                cardTemplateSimple.setStrokeColor(activeColor);
                cardTemplateSimple.setStrokeWidth(dpToPx(2));
                rbTemplateSimple.setChecked(true);
                break;
            case FAB:
                cardTemplateFab.setStrokeColor(activeColor);
                cardTemplateFab.setStrokeWidth(dpToPx(2));
                rbTemplateFab.setChecked(true);
                break;
            case NAVIGATION_DRAWER:
                cardTemplateNav.setStrokeColor(activeColor);
                cardTemplateNav.setStrokeWidth(dpToPx(2));
                rbTemplateNav.setChecked(true);
                break;
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void setupAppNameAutoPackage() {
        etPackageName.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) userEditedPackageName = true;
        });

        etAppName.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (!userEditedPackageName) {
                    String clean = s.toString().trim().toLowerCase().replaceAll("[^a-z0-9]", "");
                    if (!clean.isEmpty()) {
                        etPackageName.setText("com.example." + clean);
                    } else {
                        etPackageName.setText("com.example.myapp");
                    }
                }
            }
        });
    }

    private void setupSpinners() {
        String[] minSdks = {
                "21 (Android 5.0 Lollipop)",
                "22 (Android 5.1 Lollipop MR1)",
                "24 (Android 7.0 Nougat)",
                "26 (Android 8.0 Oreo)",
                "28 (Android 9.0 Pie)",
                "30 (Android 11.0)",
                "33 (Android 13.0)"
        };
        ArrayAdapter<String> minAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, minSdks);
        spMinSdk.setAdapter(minAdapter);
        spMinSdk.setSelection(1); // Default API 22

        String[] targetSdks = {
                "28 (Android 9.0)",
                "30 (Android 11.0)",
                "31 (Android 12.0)",
                "33 (Android 13.0)",
                "34 (Android 14.0)"
        };
        ArrayAdapter<String> targetAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, targetSdks);
        spTargetSdk.setAdapter(targetAdapter);
        spTargetSdk.setSelection(4); // Default API 34
    }

    private void pickAppIcon() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, "Select App Icon (PNG required)"), REQUEST_PICK_ICON);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_ICON && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                if (is == null) {
                    Toast.makeText(this, "Cannot open selected image", Toast.LENGTH_SHORT).show();
                    return;
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, read);
                }
                byte[] bytes = baos.toByteArray();

                // Strict PNG Header Validation (0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)
                if (!isPngHeader(bytes)) {
                    Toast.makeText(this, "Actual PNG format required! Other image formats are not accepted.", Toast.LENGTH_LONG).show();
                    return;
                }

                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bmp != null) {
                    pickedIconBytes = bytes;
                    ivAppIcon.setImageBitmap(bmp);
                    tvPickIconLabel.setText("PNG Icon Selected");
                    Toast.makeText(this, "Valid PNG icon loaded", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Could not decode PNG image", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "Failed loading icon: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean isPngHeader(byte[] data) {
        if (data == null || data.length < 8) return false;
        return (data[0] == (byte) 0x89 &&
                data[1] == (byte) 0x50 && // P
                data[2] == (byte) 0x4E && // N
                data[3] == (byte) 0x47 && // G
                data[4] == (byte) 0x0D &&
                data[5] == (byte) 0x0A &&
                data[6] == (byte) 0x1A &&
                data[7] == (byte) 0x0A);
    }

    private void handleCreateProject() {
        String appName = etAppName.getText().toString().trim();
        String pkg = etPackageName.getText().toString().trim();
        String verName = etVersionName.getText().toString().trim();
        String verCodeStr = etVersionCode.getText().toString().trim();

        if (appName.isEmpty()) {
            etAppName.setError("Application name is required");
            etAppName.requestFocus();
            return;
        }
        if (pkg.isEmpty() || !pkg.contains(".")) {
            etPackageName.setError("Valid package name required (e.g. com.example.app)");
            etPackageName.requestFocus();
            return;
        }
        if (verName.isEmpty()) verName = "1.0";
        int verCode = 1;
        try {
            if (!verCodeStr.isEmpty()) verCode = Integer.parseInt(verCodeStr);
        } catch (NumberFormatException ignored) {}

        int minSdk = parseSdkNumber((String) spMinSdk.getSelectedItem(), 22);
        int targetSdk = parseSdkNumber((String) spTargetSdk.getSelectedItem(), 34);

        File projectsDir = new File(Environment.getExternalStorageDirectory(), "BUILD STUDIO/projects");
        if (!projectsDir.exists()) projectsDir.mkdirs();
        File projectRoot = new File(projectsDir, appName);

        if (projectRoot.exists()) {
            Toast.makeText(this, "Project '" + appName + "' already exists! Choose another name.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            buildProjectHierarchy(projectRoot, appName, pkg, verName, verCode, minSdk, targetSdk, selectedTemplate);

            // Copy custom PNG icon if provided
            if (pickedIconBytes != null) {
                saveIconToProject(projectRoot, pickedIconBytes);
            }

            Toast.makeText(this, "Project created: " + appName, Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, CodeEditorActivity.class);
            intent.putExtra("project_path", projectRoot.getAbsolutePath());
            intent.putExtra("project_name", appName);
            intent.putExtra("package_name", pkg);
            startActivity(intent);
            overridePendingTransition(R.anim.animate_slide_left_enter, R.anim.animate_slide_left_exit);
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "Error creating project: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    private void saveIconToProject(File root, byte[] iconBytes) {
        try {
            File[] targets = new File[] {
                    new File(root, "app/src/main/res/mipmap-xhdpi/ic_launcher.png"),
                    new File(root, "app/src/main/res/drawable-xhdpi/app_icon.png"),
                    new File(root, "src/main/res/mipmap-xhdpi/ic_launcher.png"),
                    new File(root, "src/main/res/drawable-xhdpi/app_icon.png")
            };
            for (File target : targets) {
                target.getParentFile().mkdirs();
                try (FileOutputStream fos = new FileOutputStream(target)) {
                    fos.write(iconBytes);
                }
            }
        } catch (Exception ignored) {}
    }

    private int parseSdkNumber(String selected, int fallback) {
        if (selected == null) return fallback;
        try {
            String numStr = selected.split(" ")[0].trim();
            return Integer.parseInt(numStr);
        } catch (Exception e) {
            return fallback;
        }
    }

    private void buildProjectHierarchy(File root, String appName, String pkg, String verName, int verCode,
                                       int minSdk, int targetSdk, ProjectTemplate template) throws IOException {
        File srcMain = new File(root, "src/main");
        File javaDir = new File(srcMain, "java/" + pkg.replace(".", "/"));
        File resDir = new File(srcMain, "res");
        File layoutDir = new File(resDir, "layout");
        File valuesDir = new File(resDir, "values");
        File mipmapDir = new File(resDir, "mipmap-xhdpi");
        File drawableDir = new File(resDir, "drawable-xhdpi");
        File outputBinDir = new File(root, "output/bin");

        javaDir.mkdirs();
        layoutDir.mkdirs();
        valuesDir.mkdirs();
        mipmapDir.mkdirs();
        drawableDir.mkdirs();
        outputBinDir.mkdirs();

        File appSrcMain = new File(root, "app/src/main");
        File appJavaDir = new File(appSrcMain, "java/" + pkg.replace(".", "/"));
        File appLayoutDir = new File(appSrcMain, "res/layout");
        File appValuesDir = new File(appSrcMain, "res/values");
        File appMipmapDir = new File(appSrcMain, "res/mipmap-xhdpi");
        File appDrawableDir = new File(appSrcMain, "res/drawable-xhdpi");
        appJavaDir.mkdirs();
        appLayoutDir.mkdirs();
        appValuesDir.mkdirs();
        appMipmapDir.mkdirs();
        appDrawableDir.mkdirs();
        new File(root, "app/build/bin").mkdirs();

        // 1. AndroidManifest.xml
        String manifestContent = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "    package=\"" + pkg + "\"\n" +
                "    android:versionCode=\"" + verCode + "\"\n" +
                "    android:versionName=\"" + verName + "\">\n\n" +
                "    <uses-sdk android:minSdkVersion=\"" + minSdk + "\" android:targetSdkVersion=\"" + targetSdk + "\" />\n\n" +
                "    <application\n" +
                "        android:allowBackup=\"true\"\n" +
                "        android:icon=\"@mipmap/ic_launcher\"\n" +
                "        android:label=\"" + appName + "\"\n" +
                "        android:theme=\"@style/AppTheme\">\n\n" +
                "        <activity\n" +
                "            android:name=\".MainActivity\"\n" +
                "            android:exported=\"true\">\n" +
                "            <intent-filter>\n" +
                "                <action android:name=\"android.intent.action.MAIN\" />\n" +
                "                <category android:name=\"android.intent.category.LAUNCHER\" />\n" +
                "            </intent-filter>\n" +
                "        </activity>\n" +
                "    </application>\n" +
                "</manifest>\n";

        writeFile(new File(srcMain, "AndroidManifest.xml"), manifestContent);
        writeFile(new File(appSrcMain, "AndroidManifest.xml"), manifestContent);

        // 2. MainActivity.java & activity_main.xml per Template
        String javaContent;
        String layoutContent;

        switch (template) {
            case FAB:
                javaContent = "package " + pkg + ";\n\n" +
                        "import android.os.Bundle;\n" +
                        "import android.widget.Toast;\n" +
                        "import androidx.appcompat.app.AppCompatActivity;\n" +
                        "import com.google.android.material.floatingactionbutton.FloatingActionButton;\n\n" +
                        "public class MainActivity extends AppCompatActivity {\n\n" +
                        "    @Override\n" +
                        "    protected void onCreate(Bundle savedInstanceState) {\n" +
                        "        super.onCreate(savedInstanceState);\n" +
                        "        setContentView(R.layout.activity_main);\n\n" +
                        "        FloatingActionButton fab = findViewById(R.id.fab);\n" +
                        "        if (fab != null) {\n" +
                        "            fab.setOnClickListener(v -> {\n" +
                        "                Toast.makeText(MainActivity.this, \"Floating Action Button Clicked!\", Toast.LENGTH_SHORT).show();\n" +
                        "            });\n" +
                        "        }\n" +
                        "    }\n" +
                        "}\n";

                layoutContent = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<androidx.coordinatorlayout.widget.CoordinatorLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                        "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                        "    android:layout_width=\"match_parent\"\n" +
                        "    android:layout_height=\"match_parent\"\n" +
                        "    android:background=\"#0D1117\">\n\n" +
                        "    <LinearLayout\n" +
                        "        android:layout_width=\"match_parent\"\n" +
                        "        android:layout_height=\"match_parent\"\n" +
                        "        android:gravity=\"center\"\n" +
                        "        android:orientation=\"vertical\"\n" +
                        "        android:padding=\"24dp\">\n\n" +
                        "        <TextView\n" +
                        "            android:id=\"@+id/tv_greeting\"\n" +
                        "            android:layout_width=\"wrap_content\"\n" +
                        "            android:layout_height=\"wrap_content\"\n" +
                        "            android:text=\"Hello from " + appName + "!\"\n" +
                        "            android:textColor=\"#FFFFFF\"\n" +
                        "            android:textSize=\"22sp\"\n" +
                        "            android:textStyle=\"bold\" />\n\n" +
                        "        <TextView\n" +
                        "            android:layout_width=\"wrap_content\"\n" +
                        "            android:layout_height=\"wrap_content\"\n" +
                        "            android:layout_marginTop=\"8dp\"\n" +
                        "            android:text=\"Tap the Floating Action Button below\"\n" +
                        "            android:textColor=\"#8B949E\"\n" +
                        "            android:textSize=\"14sp\" />\n" +
                        "    </LinearLayout>\n\n" +
                        "    <com.google.android.material.floatingactionbutton.FloatingActionButton\n" +
                        "        android:id=\"@+id/fab\"\n" +
                        "        android:layout_width=\"wrap_content\"\n" +
                        "        android:layout_height=\"wrap_content\"\n" +
                        "        android:layout_gravity=\"bottom|end\"\n" +
                        "        android:layout_margin=\"24dp\"\n" +
                        "        android:contentDescription=\"Action\"\n" +
                        "        android:src=\"@android:drawable/ic_input_add\"\n" +
                        "        app:backgroundTint=\"#2979FF\"\n" +
                        "        app:tint=\"#FFFFFF\" />\n" +
                        "</androidx.coordinatorlayout.widget.CoordinatorLayout>\n";
                break;

            case NAVIGATION_DRAWER:
                javaContent = "package " + pkg + ";\n\n" +
                        "import android.os.Bundle;\n" +
                        "import android.widget.TextView;\n" +
                        "import android.widget.Toast;\n" +
                        "import androidx.appcompat.app.ActionBarDrawerToggle;\n" +
                        "import androidx.appcompat.app.AppCompatActivity;\n" +
                        "import androidx.appcompat.widget.Toolbar;\n" +
                        "import androidx.core.view.GravityCompat;\n" +
                        "import androidx.drawerlayout.widget.DrawerLayout;\n" +
                        "import com.google.android.material.navigation.NavigationView;\n\n" +
                        "public class MainActivity extends AppCompatActivity {\n\n" +
                        "    private DrawerLayout drawerLayout;\n\n" +
                        "    @Override\n" +
                        "    protected void onCreate(Bundle savedInstanceState) {\n" +
                        "        super.onCreate(savedInstanceState);\n" +
                        "        setContentView(R.layout.activity_main);\n\n" +
                        "        Toolbar toolbar = findViewById(R.id.toolbar);\n" +
                        "        setSupportActionBar(toolbar);\n\n" +
                        "        drawerLayout = findViewById(R.id.drawer_layout);\n" +
                        "        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(\n" +
                        "                this, drawerLayout, toolbar, R.string.open_drawer, R.string.close_drawer);\n" +
                        "        drawerLayout.addDrawerListener(toggle);\n" +
                        "        toggle.syncState();\n\n" +
                        "        NavigationView navigationView = findViewById(R.id.nav_view);\n" +
                        "        if (navigationView != null) {\n" +
                        "            navigationView.setNavigationItemSelectedListener(item -> {\n" +
                        "                Toast.makeText(this, \"Selected: \" + item.getTitle(), Toast.LENGTH_SHORT).show();\n" +
                        "                drawerLayout.closeDrawer(GravityCompat.START);\n" +
                        "                return true;\n" +
                        "            });\n" +
                        "        }\n" +
                        "    }\n" +
                        "}\n";

                layoutContent = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<androidx.drawerlayout.widget.DrawerLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                        "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                        "    android:id=\"@+id/drawer_layout\"\n" +
                        "    android:layout_width=\"match_parent\"\n" +
                        "    android:layout_height=\"match_parent\"\n" +
                        "    android:background=\"#0D1117\">\n\n" +
                        "    <LinearLayout\n" +
                        "        android:layout_width=\"match_parent\"\n" +
                        "        android:layout_height=\"match_parent\"\n" +
                        "        android:orientation=\"vertical\">\n\n" +
                        "        <androidx.appcompat.widget.Toolbar\n" +
                        "            android:id=\"@+id/toolbar\"\n" +
                        "            android:layout_width=\"match_parent\"\n" +
                        "            android:layout_height=\"?attr/actionBarSize\"\n" +
                        "            android:background=\"#121820\"\n" +
                        "            app:title=\"" + appName + "\"\n" +
                        "            app:titleTextColor=\"#FFFFFF\" />\n\n" +
                        "        <LinearLayout\n" +
                        "            android:layout_width=\"match_parent\"\n" +
                        "            android:layout_height=\"match_parent\"\n" +
                        "            android:gravity=\"center\"\n" +
                        "            android:orientation=\"vertical\"\n" +
                        "            android:padding=\"24dp\">\n\n" +
                        "            <TextView\n" +
                        "                android:id=\"@+id/tv_greeting\"\n" +
                        "                android:layout_width=\"wrap_content\"\n" +
                        "                android:layout_height=\"wrap_content\"\n" +
                        "                android:text=\"Welcome to " + appName + "!\"\n" +
                        "                android:textColor=\"#FFFFFF\"\n" +
                        "                android:textSize=\"20sp\"\n" +
                        "                android:textStyle=\"bold\" />\n\n" +
                        "            <TextView\n" +
                        "                android:layout_width=\"wrap_content\"\n" +
                        "                android:layout_height=\"wrap_content\"\n" +
                        "                android:layout_marginTop=\"8dp\"\n" +
                        "                android:text=\"Swipe from left to open drawer\"\n" +
                        "                android:textColor=\"#8B949E\"\n" +
                        "                android:textSize=\"14sp\" />\n" +
                        "        </LinearLayout>\n" +
                        "    </LinearLayout>\n\n" +
                        "    <com.google.android.material.navigation.NavigationView\n" +
                        "        android:id=\"@+id/nav_view\"\n" +
                        "        android:layout_width=\"wrap_content\"\n" +
                        "        android:layout_height=\"match_parent\"\n" +
                        "        android:layout_gravity=\"start\"\n" +
                        "        android:background=\"#1A222D\"\n" +
                        "        app:itemTextColor=\"#FFFFFF\" />\n" +
                        "</androidx.drawerlayout.widget.DrawerLayout>\n";
                break;

            case SIMPLE:
            default:
                javaContent = "package " + pkg + ";\n\n" +
                        "import android.os.Bundle;\n" +
                        "import android.widget.TextView;\n" +
                        "import android.widget.Toast;\n" +
                        "import androidx.appcompat.app.AppCompatActivity;\n\n" +
                        "public class MainActivity extends AppCompatActivity {\n\n" +
                        "    private TextView tvGreeting;\n\n" +
                        "    @Override\n" +
                        "    protected void onCreate(Bundle savedInstanceState) {\n" +
                        "        super.onCreate(savedInstanceState);\n" +
                        "        setContentView(R.layout.activity_main);\n\n" +
                        "        tvGreeting = findViewById(R.id.tv_greeting);\n" +
                        "        Toast.makeText(this, \"Welcome to " + appName + "!\", Toast.LENGTH_SHORT).show();\n" +
                        "    }\n" +
                        "}\n";

                layoutContent = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                        "    android:layout_width=\"match_parent\"\n" +
                        "    android:layout_height=\"match_parent\"\n" +
                        "    android:background=\"#0D1117\"\n" +
                        "    android:gravity=\"center\"\n" +
                        "    android:orientation=\"vertical\"\n" +
                        "    android:padding=\"24dp\">\n\n" +
                        "    <TextView\n" +
                        "        android:id=\"@+id/tv_greeting\"\n" +
                        "        android:layout_width=\"wrap_content\"\n" +
                        "        android:layout_height=\"wrap_content\"\n" +
                        "        android:text=\"Hello from " + appName + "!\"\n" +
                        "        android:textColor=\"#FFFFFF\"\n" +
                        "        android:textSize=\"22sp\"\n" +
                        "        android:textStyle=\"bold\" />\n\n" +
                        "    <TextView\n" +
                        "        android:layout_width=\"wrap_content\"\n" +
                        "        android:layout_height=\"wrap_content\"\n" +
                        "        android:layout_marginTop=\"10dp\"\n" +
                        "        android:text=\"Built with Build Studio on Mobile\"\n" +
                        "        android:textColor=\"#8B949E\"\n" +
                        "        android:textSize=\"14sp\" />\n" +
                        "</LinearLayout>\n";
                break;
        }

        writeFile(new File(javaDir, "MainActivity.java"), javaContent);
        writeFile(new File(appJavaDir, "MainActivity.java"), javaContent);

        writeFile(new File(layoutDir, "activity_main.xml"), layoutContent);
        writeFile(new File(appLayoutDir, "activity_main.xml"), layoutContent);
        writeFile(new File(layoutDir, "main.xml"), layoutContent);
        writeFile(new File(appLayoutDir, "main.xml"), layoutContent);

        // 3. strings.xml
        String stringsContent = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<resources>\n" +
                "    <string name=\"app_name\">" + appName + "</string>\n" +
                "    <string name=\"open_drawer\">Open</string>\n" +
                "    <string name=\"close_drawer\">Close</string>\n" +
                "</resources>\n";
        writeFile(new File(valuesDir, "strings.xml"), stringsContent);
        writeFile(new File(appValuesDir, "strings.xml"), stringsContent);

        // 4. colors.xml
        String colorsContent = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<resources>\n" +
                "    <color name=\"colorPrimary\">#2979FF</color>\n" +
                "    <color name=\"colorPrimaryDark\">#1565C0</color>\n" +
                "    <color name=\"colorAccent\">#2979FF</color>\n" +
                "    <color name=\"textColorPrimary\">#FFFFFF</color>\n" +
                "</resources>\n";
        writeFile(new File(valuesDir, "colors.xml"), colorsContent);
        writeFile(new File(appValuesDir, "colors.xml"), colorsContent);

        // 5. styles.xml
        String stylesContent = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<resources>\n" +
                "    <style name=\"AppTheme\" parent=\"Theme.AppCompat.DayNight.NoActionBar\">\n" +
                "        <item name=\"colorPrimary\">@color/colorPrimary</item>\n" +
                "        <item name=\"colorPrimaryDark\">@color/colorPrimaryDark</item>\n" +
                "        <item name=\"colorAccent\">@color/colorAccent</item>\n" +
                "        <item name=\"android:windowBackground\">#0D1117</item>\n" +
                "    </style>\n" +
                "</resources>\n";
        writeFile(new File(valuesDir, "styles.xml"), stylesContent);
        writeFile(new File(appValuesDir, "styles.xml"), stylesContent);

        // Save project metadata
        Project project = new Project(appName, pkg, root.getAbsolutePath());
        project.setMinSdk(minSdk);
        project.setTargetSdk(targetSdk);
        project.setVersionName(verName);
        project.setVersionCode(verCode);
        project.saveConfig();
    }

    private void writeFile(File target, String content) throws IOException {
        target.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(target)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }
}
