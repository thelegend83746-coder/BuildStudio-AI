package com.apk.builder;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.*;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.apk.builder.model.Project;
import com.build.studio.R;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class CreateProjectActivity extends AppCompatActivity {

    private static final int REQUEST_PICK_ICON = 1002;

    private EditText etAppName, etPackageName, etVersionName, etVersionCode;
    private Spinner spMinSdk, spTargetSdk;
    private RadioGroup rgJavaVersion, rgDexer;
    private Switch swAppCompat, swMaterial;
    private ImageView ivAppIcon;
    private TextView tvPickIconLabel;
    private Button btnCreate;

    private Uri pickedIconUri = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_project);
        overridePendingTransition(R.anim.animate_slide_left_enter, R.anim.animate_slide_left_exit);

        initViews();
        setupSpinners();
    }

    private void initViews() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        etAppName = findViewById(R.id.et_app_name);
        etPackageName = findViewById(R.id.et_package_name);
        etVersionName = findViewById(R.id.et_version_name);
        etVersionCode = findViewById(R.id.et_version_code);

        spMinSdk = findViewById(R.id.sp_min_sdk);
        spTargetSdk = findViewById(R.id.sp_target_sdk);

        rgJavaVersion = findViewById(R.id.rg_java_version);
        rgDexer = findViewById(R.id.rg_dexer);

        swAppCompat = findViewById(R.id.sw_appcompat);
        swMaterial = findViewById(R.id.sw_material);

        ivAppIcon = findViewById(R.id.iv_project_icon_preview);
        tvPickIconLabel = findViewById(R.id.tv_pick_icon_hint);
        View cardPickIcon = findViewById(R.id.card_pick_icon);
        if (cardPickIcon != null) cardPickIcon.setOnClickListener(v -> pickAppIcon());

        btnCreate = findViewById(R.id.btn_create_project);
        btnCreate.setOnClickListener(v -> handleCreateProject());
    }

    private void setupSpinners() {
        String[] minSdks = {"21 (Android 5.0 Lollipop)", "22 (Android 5.1 Lollipop MR1)", "24 (Android 7.0 Nougat)", "26 (Android 8.0 Oreo)", "28 (Android 9.0 Pie)", "30 (Android 11.0)", "33 (Android 13.0)"};
        ArrayAdapter<String> minAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, minSdks);
        spMinSdk.setAdapter(minAdapter);
        spMinSdk.setSelection(1); // Default API 22

        String[] targetSdks = {"28 (Android 9.0)", "30 (Android 11.0)", "31 (Android 12.0)", "33 (Android 13.0)", "34 (Android 14.0)", "35 (Android 15.0)"};
        ArrayAdapter<String> targetAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, targetSdks);
        spTargetSdk.setAdapter(targetAdapter);
        spTargetSdk.setSelection(4); // Default API 34
    }

    private void pickAppIcon() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, "Select App Icon"), REQUEST_PICK_ICON);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_ICON && resultCode == RESULT_OK && data != null && data.getData() != null) {
            pickedIconUri = data.getData();
            try (InputStream is = getContentResolver().openInputStream(pickedIconUri)) {
                Bitmap bmp = BitmapFactory.decodeStream(is);
                if (bmp != null && ivAppIcon != null) {
                    ivAppIcon.setImageBitmap(bmp);
                    if (tvPickIconLabel != null) tvPickIconLabel.setText("Custom Icon Selected");
                }
            } catch (Exception e) {
                Toast.makeText(this, "Failed loading icon: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void handleCreateProject() {
        String appName = etAppName.getText().toString().trim();
        String pkg = etPackageName.getText().toString().trim();
        String verName = etVersionName.getText().toString().trim();
        String verCodeStr = etVersionCode.getText().toString().trim();

        if (appName.isEmpty()) {
            etAppName.setError("App name is required");
            return;
        }
        if (pkg.isEmpty() || !pkg.contains(".")) {
            etPackageName.setError("Valid package name required (e.g. com.example.app)");
            return;
        }
        if (verName.isEmpty()) verName = "1.0";
        int verCode = 1;
        try {
            if (!verCodeStr.isEmpty()) verCode = Integer.parseInt(verCodeStr);
        } catch (NumberFormatException ignored) {}

        int minSdk = parseSdkNumber((String) spMinSdk.getSelectedItem(), 22);
        int targetSdk = parseSdkNumber((String) spTargetSdk.getSelectedItem(), 34);

        String javaVersion = "1.8";
        int checkedJava = rgJavaVersion.getCheckedRadioButtonId();
        if (checkedJava == R.id.rb_java_6) javaVersion = "1.6";
        else if (checkedJava == R.id.rb_java_7) javaVersion = "1.7";

        String dexer = "D8";
        if (rgDexer.getCheckedRadioButtonId() == R.id.rb_dexer_dx) dexer = "Dx";

        boolean useAppCompat = swAppCompat.isChecked();
        boolean useMaterial = swMaterial.isChecked();

        File projectsDir = new File(Environment.getExternalStorageDirectory(), "BUILD STUDIO/projects");
        if (!projectsDir.exists()) projectsDir.mkdirs();
        File projectRoot = new File(projectsDir, appName);

        if (projectRoot.exists()) {
            Toast.makeText(this, "Project with this name already exists!", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            buildProjectHierarchy(projectRoot, appName, pkg, verName, verCode, minSdk, targetSdk, javaVersion, dexer, useAppCompat, useMaterial);

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
                                       int minSdk, int targetSdk, String javaVersion, String dexer,
                                       boolean useAppCompat, boolean useMaterial) throws IOException {
        File srcMain = new File(root, "src/main");
        File javaDir = new File(srcMain, "java/" + pkg.replace(".", "/"));
        File resDir = new File(srcMain, "res");
        File layoutDir = new File(resDir, "layout");
        File valuesDir = new File(resDir, "values");
        File mipmapDir = new File(resDir, "mipmap-xhdpi");
        File outputBinDir = new File(root, "output/bin");

        javaDir.mkdirs();
        layoutDir.mkdirs();
        valuesDir.mkdirs();
        mipmapDir.mkdirs();
        outputBinDir.mkdirs();

        File appSrcMain = new File(root, "app/src/main");
        File appJavaDir = new File(appSrcMain, "java/" + pkg.replace(".", "/"));
        File appLayoutDir = new File(appSrcMain, "res/layout");
        File appValuesDir = new File(appSrcMain, "res/values");
        File appMipmapDir = new File(appSrcMain, "res/mipmap-xhdpi");
        appJavaDir.mkdirs();
        appLayoutDir.mkdirs();
        appValuesDir.mkdirs();
        appMipmapDir.mkdirs();
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

        // 2. MainActivity.java (Matching the exact video template)
        String baseActivityClass = useAppCompat ? "androidx.appcompat.app.AppCompatActivity" : "android.app.Activity";
        String baseActivityName = useAppCompat ? "AppCompatActivity" : "Activity";

        String javaContent = "package " + pkg + ";\n\n" +
                "import android.os.Bundle;\n" +
                "import android.widget.TextView;\n" +
                "import android.widget.Toast;\n" +
                (useAppCompat ? "import androidx.appcompat.app.AppCompatActivity;\n\n" : "import android.app.Activity;\n\n") +
                "public class MainActivity extends " + baseActivityName + " {\n\n" +
                "    private TextView tvGreeting;\n\n" +
                "    @Override\n" +
                "    protected void onCreate(Bundle savedInstanceState) {\n" +
                "        super.onCreate(savedInstanceState);\n" +
                "        setContentView(R.layout.activity_main);\n\n" +
                "        tvGreeting = findViewById(R.id.tv_greeting);\n" +
                "        Toast.makeText(this, \"Welcome to " + appName + "!\", Toast.LENGTH_SHORT).show();\n" +
                "    }\n" +
                "}\n";

        writeFile(new File(javaDir, "MainActivity.java"), javaContent);
        writeFile(new File(appJavaDir, "MainActivity.java"), javaContent);

        // 3. activity_main.xml and main.xml
        String layoutContent = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "    android:layout_width=\"match_parent\"\n" +
                "    android:layout_height=\"match_parent\"\n" +
                "    android:gravity=\"center\"\n" +
                "    android:background=\"@color/background_color\"\n" +
                "    android:orientation=\"vertical\"\n" +
                "    android:padding=\"16dp\">\n\n" +
                "    <TextView\n" +
                "        android:id=\"@+id/tv_greeting\"\n" +
                "        android:layout_width=\"wrap_content\"\n" +
                "        android:layout_height=\"wrap_content\"\n" +
                "        android:text=\"@string/hello_message\"\n" +
                "        android:textColor=\"@color/colorPrimary\"\n" +
                "        android:textSize=\"22sp\"\n" +
                "        android:textStyle=\"bold\" />\n\n" +
                "    <TextView\n" +
                "        android:layout_width=\"wrap_content\"\n" +
                "        android:layout_height=\"wrap_content\"\n" +
                "        android:layout_marginTop=\"8dp\"\n" +
                "        android:text=\"Built directly on device with BUILD STUDIO\"\n" +
                "        android:textColor=\"#8B949E\"\n" +
                "        android:textSize=\"14sp\" />\n" +
                "</LinearLayout>\n";

        writeFile(new File(layoutDir, "activity_main.xml"), layoutContent);
        writeFile(new File(layoutDir, "main.xml"), layoutContent);
        writeFile(new File(appLayoutDir, "activity_main.xml"), layoutContent);
        writeFile(new File(appLayoutDir, "main.xml"), layoutContent);

        // 4. strings.xml
        String stringsContent = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<resources>\n" +
                "    <string name=\"app_name\">" + appName + "</string>\n" +
                "    <string name=\"hello_message\">Hello Bhai!</string>\n" +
                "</resources>\n";

        writeFile(new File(valuesDir, "strings.xml"), stringsContent);
        writeFile(new File(appValuesDir, "strings.xml"), stringsContent);

        // 5. colors.xml
        String colorsContent = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<resources>\n" +
                "    <color name=\"colorPrimary\">#2979FF</color>\n" +
                "    <color name=\"colorPrimaryDark\">#1565C0</color>\n" +
                "    <color name=\"colorAccent\">#00E676</color>\n" +
                "    <color name=\"background_color\">#0D1117</color>\n" +
                "</resources>\n";

        writeFile(new File(valuesDir, "colors.xml"), colorsContent);
        writeFile(new File(appValuesDir, "colors.xml"), colorsContent);

        // 6. themes.xml & styles.xml
        String themeParent = useMaterial ? "Theme.MaterialComponents.DayNight.NoActionBar" :
                (useAppCompat ? "Theme.AppCompat.Light.NoActionBar" : "@android:style/Theme.DeviceDefault.Light.NoActionBar");

        String themesContent = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<resources>\n" +
                "    <style name=\"AppTheme\" parent=\"" + themeParent + "\">\n" +
                "        <item name=\"android:colorPrimary\">@color/colorPrimary</item>\n" +
                "        <item name=\"android:colorPrimaryDark\">@color/colorPrimaryDark</item>\n" +
                "        <item name=\"android:colorAccent\">@color/colorAccent</item>\n" +
                "    </style>\n" +
                "</resources>\n";

        writeFile(new File(valuesDir, "themes.xml"), themesContent);
        writeFile(new File(valuesDir, "styles.xml"), themesContent);
        writeFile(new File(appValuesDir, "themes.xml"), themesContent);
        writeFile(new File(appValuesDir, "styles.xml"), themesContent);

        // 7. Icon handling
        File iconTarget1 = new File(mipmapDir, "ic_launcher.png");
        File iconTarget2 = new File(appMipmapDir, "ic_launcher.png");
        File drawableIcon = new File(root, "src/main/res/drawable-xhdpi/app_icon.png");
        drawableIcon.getParentFile().mkdirs();

        String savedIconPath = null;
        if (pickedIconUri != null) {
            try (InputStream is1 = getContentResolver().openInputStream(pickedIconUri);
                 FileOutputStream fos1 = new FileOutputStream(iconTarget1)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = is1.read(buf)) > 0) fos1.write(buf, 0, len);
            }
            FileUtil.copyAsset(new FileInputStream(iconTarget1), iconTarget2);
            FileUtil.copyAsset(new FileInputStream(iconTarget1), drawableIcon);
            savedIconPath = iconTarget1.getAbsolutePath();
        } else {
            try (InputStream is = getAssets().open("EmptyProject/app/src/main/res/drawable-xhdpi/app_icon.png")) {
                FileUtil.copyAsset(is, iconTarget1);
                FileUtil.copyAsset(new FileInputStream(iconTarget1), iconTarget2);
                FileUtil.copyAsset(new FileInputStream(iconTarget1), drawableIcon);
            } catch (Exception ignored) {}
        }

        // 8. Save project.json
        Project p = new Project(appName, pkg, root.getAbsolutePath());
        p.setVersionName(verName);
        p.setVersionCode(verCode);
        p.setMinSdk(minSdk);
        p.setTargetSdk(targetSdk);
        p.setJavaVersion(javaVersion);
        p.setDexer(dexer);
        p.setUseAppCompat(useAppCompat);
        p.setUseMaterial(useMaterial);
        p.setIconPath(savedIconPath);
        p.saveConfig();
    }

    private void writeFile(File f, String content) throws IOException {
        f.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }
}
