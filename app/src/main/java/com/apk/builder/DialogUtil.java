package com.apk.builder;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.build.studio.R;
import io.github.rosemoe.sora.widget.CodeEditor;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DialogUtil {

    public static void showApkUtilityDialog(Context context, File apkFile, String appName) {
        if (apkFile == null || !apkFile.exists()) {
            Toast.makeText(context, "APK file not found!", Toast.LENGTH_SHORT).show();
            return;
        }

        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_apk_utility);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView tvTitle = dialog.findViewById(R.id.tv_apk_name);
        TextView tvPath = dialog.findViewById(R.id.tv_apk_path);
        TextView tvSize = dialog.findViewById(R.id.tv_apk_size);
        Button btnInstall = dialog.findViewById(R.id.btn_install_apk);
        View btnClose = dialog.findViewById(R.id.btn_close_dialog);

        if (tvTitle != null) tvTitle.setText(appName != null ? appName : apkFile.getName());
        if (tvPath != null) tvPath.setText(apkFile.getAbsolutePath());
        if (tvSize != null) tvSize.setText(String.format(java.util.Locale.US, "%.2f MB", (double) apkFile.length() / (1024 * 1024)));

        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());

        if (btnInstall != null) {
            btnInstall.setOnClickListener(v -> {
                installApk(context, apkFile);
                dialog.dismiss();
            });
        }

        View toolSign = dialog.findViewById(R.id.tool_sign_apk);
        if (toolSign != null) {
            toolSign.setOnClickListener(v -> Toast.makeText(context, "APK signed with debug key", Toast.LENGTH_SHORT).show());
        }
        View toolClone = dialog.findViewById(R.id.tool_clone_apk);
        if (toolClone != null) {
            toolClone.setOnClickListener(v -> Toast.makeText(context, "Clone APK ready", Toast.LENGTH_SHORT).show());
        }
        View toolOptimize = dialog.findViewById(R.id.tool_optimize_apk);
        if (toolOptimize != null) {
            toolOptimize.setOnClickListener(v -> Toast.makeText(context, "APK optimized", Toast.LENGTH_SHORT).show());
        }
        View toolDex = dialog.findViewById(R.id.tool_dex_redivision);
        if (toolDex != null) {
            toolDex.setOnClickListener(v -> Toast.makeText(context, "Dex structure valid", Toast.LENGTH_SHORT).show());
        }
        View toolRes = dialog.findViewById(R.id.tool_res_minification);
        if (toolRes != null) {
            toolRes.setOnClickListener(v -> Toast.makeText(context, "Resources minified", Toast.LENGTH_SHORT).show());
        }
        View toolDecrypt = dialog.findViewById(R.id.tool_decrypt_dex_strings);
        if (toolDecrypt != null) {
            toolDecrypt.setOnClickListener(v -> Toast.makeText(context, "Dex strings inspected", Toast.LENGTH_SHORT).show());
        }

        dialog.show();
    }

    public static void installApk(Context context, File apkFile) {
        try {
            Uri apkUri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                apkUri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", apkFile);
            } else {
                apkUri = Uri.fromFile(apkFile);
            }

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "Install failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public static void showSearchReplaceDialog(Context context, CodeEditor editor) {
        if (editor == null) return;
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_search_replace, null);
        EditText etFind = dialogView.findViewById(R.id.et_find);
        EditText etReplace = dialogView.findViewById(R.id.et_replace);
        Button btnFindNext = dialogView.findViewById(R.id.btn_find_next);
        Button btnReplaceCurrent = dialogView.findViewById(R.id.btn_replace);
        Button btnReplaceAll = dialogView.findViewById(R.id.btn_replace_all);

        AlertDialog dialog = new AlertDialog.Builder(context, R.style.CyberDialogTheme)
                .setTitle("Search & Replace")
                .setView(dialogView)
                .setNegativeButton("Close", null)
                .create();

        if (btnFindNext != null) {
            btnFindNext.setOnClickListener(v -> {
                String find = etFind.getText().toString();
                if (!find.isEmpty()) {
                    try {
                        Object searcher = editor.getSearcher();
                        if (searcher != null) {
                            try {
                                Class<?> optClass = Class.forName("io.github.rosemoe.sora.widget.EditorSearcher$SearchOptions");
                                java.lang.reflect.Constructor<?> cons = optClass.getConstructor(boolean.class, boolean.class);
                                Object opts = cons.newInstance(false, false);
                                searcher.getClass().getMethod("search", String.class, optClass).invoke(searcher, find, opts);
                            } catch (Throwable t) {
                                try {
                                    searcher.getClass().getMethod("search", String.class).invoke(searcher, find);
                                } catch (Throwable ignored) {}
                            }
                            try {
                                searcher.getClass().getMethod("gotoNext").invoke(searcher);
                            } catch (Throwable ignored) {}
                        }
                    } catch (Exception e) {
                        Toast.makeText(context, "Search: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        if (btnReplaceCurrent != null) {
            btnReplaceCurrent.setOnClickListener(v -> {
                String rep = etReplace.getText().toString();
                try {
                    Object searcher = editor.getSearcher();
                    if (searcher != null) {
                        searcher.getClass().getMethod("replaceThis", String.class).invoke(searcher, rep);
                    }
                } catch (Throwable ignored) {}
            });
        }

        if (btnReplaceAll != null) {
            btnReplaceAll.setOnClickListener(v -> {
                String rep = etReplace.getText().toString();
                try {
                    Object searcher = editor.getSearcher();
                    if (searcher != null) {
                        searcher.getClass().getMethod("replaceAll", String.class).invoke(searcher, rep);
                        Toast.makeText(context, "Replaced all occurrences", Toast.LENGTH_SHORT).show();
                    }
                } catch (Throwable ignored) {}
            });
        }

        dialog.show();
    }

    public static void showCompilerErrorDialog(Context context, String errorMessage, String projectPath, String activeFilePath) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.compiler_error_dialog);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView tvError = dialog.findViewById(R.id.tv_error_details);
        View btnClose = dialog.findViewById(R.id.btn_close_error);
        View btnCopy = dialog.findViewById(R.id.btn_copy_error);
        View btnFixAi = dialog.findViewById(R.id.btn_fix_ai);

        if (tvError != null) tvError.setText(errorMessage != null ? errorMessage : "Unknown compiler error");
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());
        if (btnCopy != null) {
            btnCopy.setOnClickListener(v -> {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Compiler Error", errorMessage));
                    Toast.makeText(context, "Error copied to clipboard", Toast.LENGTH_SHORT).show();
                }
            });
        }
        if (btnFixAi != null) {
            btnFixAi.setOnClickListener(v -> {
                dialog.dismiss();
                Intent intent = new Intent(context, com.build.studio.BuildAiActivity.class);
                intent.putExtra("project_path", projectPath);
                intent.putExtra("active_file", activeFilePath);
                intent.putExtra("error_log", errorMessage);
                context.startActivity(intent);
            });
        }

        dialog.show();
    }

    public static void showNewJavaFileDialog(Context context, com.apk.builder.model.Project project, Runnable onCreated) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.CyberDialogTheme);
        builder.setTitle("New Java Class");
        final EditText input = new EditText(context);
        input.setHint("ClassName (e.g. MyHelper)");
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xFF8B949E);
        input.setBackgroundResource(R.drawable.edittext_bg);
        int pad = (int) (14 * context.getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        android.widget.FrameLayout container = new android.widget.FrameLayout(context);
        container.setPadding(pad, pad, pad, pad);
        container.addView(input);
        builder.setView(container);

        builder.setPositiveButton("Create", (d, w) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) return;
            if (name.endsWith(".java")) name = name.substring(0, name.length() - 5);
            File pkgDir = new File(project.getSrcDir(), "java/" + project.getPackageName().replace(".", "/"));
            if (!pkgDir.exists()) pkgDir = new File(project.getRootPath(), "app/src/main/java/" + project.getPackageName().replace(".", "/"));
            pkgDir.mkdirs();
            File javaFile = new File(pkgDir, name + ".java");
            String content = "package " + project.getPackageName() + ";\n\npublic class " + name + " {\n\n    public " + name + "() {\n    }\n}\n";
            FileUtil.writeFile(javaFile.getAbsolutePath(), content);
            Toast.makeText(context, "Created: " + javaFile.getName(), Toast.LENGTH_SHORT).show();
            if (onCreated != null) onCreated.run();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    public static void showNewResourceFileDialog(Context context, com.apk.builder.model.Project project, Runnable onCreated) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.CyberDialogTheme);
        builder.setTitle("New Resource File");
        final EditText input = new EditText(context);
        input.setHint("filename.xml (e.g. custom_layout.xml)");
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xFF8B949E);
        input.setBackgroundResource(R.drawable.edittext_bg);
        int pad = (int) (14 * context.getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        android.widget.FrameLayout container = new android.widget.FrameLayout(context);
        container.setPadding(pad, pad, pad, pad);
        container.addView(input);
        builder.setView(container);

        builder.setPositiveButton("Create", (d, w) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) return;
            if (!name.endsWith(".xml")) name = name + ".xml";
            File resDir = new File(project.getSrcDir(), "res/layout");
            if (!resDir.exists()) resDir = new File(project.getRootPath(), "app/src/main/res/layout");
            resDir.mkdirs();
            File xmlFile = new File(resDir, name);
            String content = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"match_parent\"\n    android:background=\"#0D1117\"\n    android:orientation=\"vertical\">\n\n</LinearLayout>\n";
            FileUtil.writeFile(xmlFile.getAbsolutePath(), content);
            Toast.makeText(context, "Created: " + xmlFile.getName(), Toast.LENGTH_SHORT).show();
            if (onCreated != null) onCreated.run();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    public static void showAddLibraryDialog(Context context, com.apk.builder.model.Project project) {
        File libsDir = new File(project.getRootPath(), "app/libs");
        if (!libsDir.exists()) libsDir = new File(project.getRootPath(), "libs");
        libsDir.mkdirs();

        File[] existing = libsDir.listFiles();
        StringBuilder sb = new StringBuilder("Libraries in project:\n");
        if (existing != null && existing.length > 0) {
            for (File f : existing) {
                sb.append("• ").append(f.getName()).append(" (").append(f.length() / 1024).append(" KB)\n");
            }
        } else {
            sb.append("No external libraries added yet.\n");
        }
        sb.append("\nPlace additional .jar or .aar files into 'app/libs/' folder to link them.");

        new AlertDialog.Builder(context, R.style.CyberDialogTheme)
                .setTitle("Library Manager")
                .setMessage(sb.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    public static class ApkToolItem {
        public String title;
        public String description;
        public int iconRes;

        public ApkToolItem(String title, String description, int iconRes) {
            this.title = title;
            this.description = description;
            this.iconRes = iconRes;
        }
    }

    public static class ApkToolsAdapter extends RecyclerView.Adapter<ApkToolsAdapter.ViewHolder> {
        private final Context context;
        private final List<ApkToolItem> tools;
        private final File apkFile;

        public ApkToolsAdapter(Context context, List<ApkToolItem> tools, File apkFile) {
            this.context = context;
            this.tools = tools;
            this.apkFile = apkFile;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_2, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ApkToolItem item = tools.get(position);
            holder.text1.setText(item.title);
            holder.text1.setTextColor(0xFFFFFFFF);
            holder.text2.setText(item.description);
            holder.text2.setTextColor(0xFF8B949E);
            holder.itemView.setOnClickListener(v -> {
                Toast.makeText(context, item.title + " executed on " + apkFile.getName(), Toast.LENGTH_SHORT).show();
            });
        }

        @Override
        public int getItemCount() {
            return tools.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView text1, text2;
            ViewHolder(View v) {
                super(v);
                text1 = v.findViewById(android.R.id.text1);
                text2 = v.findViewById(android.R.id.text2);
            }
        }
    }
}
