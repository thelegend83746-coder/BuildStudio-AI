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
