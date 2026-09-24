package com.apk.builder.model;

import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public class Project {
    private String name;
    private String packageName;
    private String rootPath;
    private int minSdk = 26;
    private int targetSdk = 34;
    private int versionCode = 1;
    private String versionName = "1.0";
    private String javaVersion = "1.8";
    private String dexer = "D8";
    private boolean stringFog = false;
    private boolean r8Shrink = false;
    private boolean useAppCompat = true;
    private boolean useMaterial = true;
    private String iconPath = null;
    private long lastModified = System.currentTimeMillis();

    public Project(String name, String packageName, String rootPath) {
        this.name = name;
        this.packageName = packageName;
        this.rootPath = rootPath;
        loadConfig();
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }

    public String getRootPath() { return rootPath; }
    public void setRootPath(String rootPath) { this.rootPath = rootPath; }

    public int getMinSdk() { return minSdk; }
    public void setMinSdk(int minSdk) { this.minSdk = minSdk; }

    public int getTargetSdk() { return targetSdk; }
    public void setTargetSdk(int targetSdk) { this.targetSdk = targetSdk; }

    public int getVersionCode() { return versionCode; }
    public void setVersionCode(int versionCode) { this.versionCode = versionCode; }

    public String getVersionName() { return versionName; }
    public void setVersionName(String versionName) { this.versionName = versionName; }

    public String getJavaVersion() { return javaVersion; }
    public void setJavaVersion(String javaVersion) { this.javaVersion = javaVersion; }

    public String getDexer() { return dexer; }
    public void setDexer(String dexer) { this.dexer = dexer; }

    public boolean isStringFog() { return stringFog; }
    public void setStringFog(boolean stringFog) { this.stringFog = stringFog; }

    public boolean isR8Shrink() { return r8Shrink; }
    public void setR8Shrink(boolean r8Shrink) { this.r8Shrink = r8Shrink; }

    public boolean isUseAppCompat() { return useAppCompat; }
    public void setUseAppCompat(boolean useAppCompat) { this.useAppCompat = useAppCompat; }

    public boolean isUseMaterial() { return useMaterial; }
    public void setUseMaterial(boolean useMaterial) { this.useMaterial = useMaterial; }

    public String getIconPath() { return iconPath; }
    public void setIconPath(String iconPath) { this.iconPath = iconPath; }

    public long getLastModified() { return lastModified; }
    public void setLastModified(long lastModified) { this.lastModified = lastModified; }

    public File getSrcDir() {
        File dir1 = new File(rootPath, "app/src/main/java");
        if (dir1.exists()) return dir1;
        File dir2 = new File(rootPath, "src/main/java");
        if (dir2.exists()) return dir2;
        return dir1;
    }

    public File getResDir() {
        File dir1 = new File(rootPath, "app/src/main/res");
        if (dir1.exists()) return dir1;
        File dir2 = new File(rootPath, "src/main/res");
        if (dir2.exists()) return dir2;
        return dir1;
    }

    public File getManifestFile() {
        File f1 = new File(rootPath, "app/src/main/AndroidManifest.xml");
        if (f1.exists()) return f1;
        File f2 = new File(rootPath, "src/main/AndroidManifest.xml");
        if (f2.exists()) return f2;
        return f1;
    }

    public File getBuildDir() {
        File f1 = new File(rootPath, "app/build");
        if (f1.exists() || new File(rootPath, "app").exists()) return f1;
        return new File(rootPath, "build");
    }

    public File getBinDir() {
        File outDir = new File(rootPath, "output/bin");
        if (outDir.exists()) return outDir;
        File appBin = new File(getBuildDir(), "bin");
        if (appBin.exists()) return appBin;
        outDir.mkdirs();
        return outDir;
    }

    public void saveConfig() {
        try {
            File configFile = new File(rootPath, "project.json");
            JSONObject json = new JSONObject();
            json.put("name", name);
            json.put("packageName", packageName);
            json.put("minSdk", minSdk);
            json.put("targetSdk", targetSdk);
            json.put("versionCode", versionCode);
            json.put("versionName", versionName);
            json.put("javaVersion", javaVersion);
            json.put("dexer", dexer);
            json.put("stringFog", stringFog);
            json.put("r8Shrink", r8Shrink);
            json.put("useAppCompat", useAppCompat);
            json.put("useMaterial", useMaterial);
            if (iconPath != null) json.put("iconPath", iconPath);
            json.put("lastModified", System.currentTimeMillis());

            try (FileOutputStream fos = new FileOutputStream(configFile)) {
                fos.write(json.toString(2).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void loadConfig() {
        try {
            File configFile = new File(rootPath, "project.json");
            if (!configFile.exists()) return;
            byte[] b = new byte[(int) configFile.length()];
            try (FileInputStream fis = new FileInputStream(configFile)) {
                fis.read(b);
            }
            JSONObject json = new JSONObject(new String(b, StandardCharsets.UTF_8));
            if (json.has("name")) this.name = json.getString("name");
            if (json.has("packageName")) this.packageName = json.getString("packageName");
            if (json.has("minSdk")) this.minSdk = json.getInt("minSdk");
            if (json.has("targetSdk")) this.targetSdk = json.getInt("targetSdk");
            if (json.has("versionCode")) this.versionCode = json.getInt("versionCode");
            if (json.has("versionName")) this.versionName = json.getString("versionName");
            if (json.has("javaVersion")) this.javaVersion = json.getString("javaVersion");
            if (json.has("dexer")) this.dexer = json.getString("dexer");
            if (json.has("stringFog")) this.stringFog = json.getBoolean("stringFog");
            if (json.has("r8Shrink")) this.r8Shrink = json.getBoolean("r8Shrink");
            if (json.has("useAppCompat")) this.useAppCompat = json.getBoolean("useAppCompat");
            if (json.has("useMaterial")) this.useMaterial = json.getBoolean("useMaterial");
            if (json.has("iconPath")) this.iconPath = json.getString("iconPath");
            if (json.has("lastModified")) this.lastModified = json.getLong("lastModified");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
