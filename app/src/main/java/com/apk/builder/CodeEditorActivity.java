package com.apk.builder;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.apk.builder.model.Project;
import com.build.studio.R;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.tabs.TabLayout;
import com.tyron.compiler.CompilerAsyncTask;
import com.tyron.compiler.CompilerResult;
import io.github.rosemoe.sora.langs.java.JavaLanguage;
import io.github.rosemoe.sora.widget.CodeEditor;
import java.io.File;
import java.util.*;

public class CodeEditorActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private CodeEditor codeEditor;
    private TabLayout tabLayout;
    private TextView tvTitle, tvCursorPos, tvSheetTitle, tvSheetLog;
    private ScrollView svSheetLog;
    private BottomSheetBehavior<View> bottomSheetBehavior;
    private RecyclerView rvFileTree;

    private SymbolLayout symbolLayout;
    private LinearLayout pullDownSymbolBar;
    private ImageView btnToggleSymbolBar;
    private boolean isSymbolBarVisible = true;

    private Project currentProject;
    private File activeFile;
    private final List<File> openTabs = new ArrayList<>();
    private final Map<String, String> fileContentCache = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_code_editor);
        overridePendingTransition(R.anim.animate_slide_left_enter, R.anim.animate_slide_left_exit);

        String path = getIntent().getStringExtra("project_path");
        String name = getIntent().getStringExtra("project_name");
        String pkg = getIntent().getStringExtra("package_name");

        if (path == null) {
            Toast.makeText(this, "No project path specified", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        currentProject = new Project(name != null ? name : "Project", pkg != null ? pkg : "com.example", path);

        initViews();
        setupEditor();
        setupPullDownSymbolBar();
        setupBottomSheet();
        setupTree();

        openDefaultFile();
    }

    private void initViews() {
        drawerLayout = findViewById(R.id._drawer);
        codeEditor = findViewById(R.id.code_editor);
        tabLayout = findViewById(R.id.tab_layout);
        tvTitle = findViewById(R.id.tv_title);
        tvCursorPos = findViewById(R.id.tv_cursor_pos);
        rvFileTree = findViewById(R.id.rv_file_tree);

        tvTitle.setText(currentProject.getName());

        findViewById(R.id.btn_drawer).setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        findViewById(R.id.btn_undo).setOnClickListener(v -> { if (codeEditor.canUndo()) codeEditor.undo(); });
        findViewById(R.id.btn_redo).setOnClickListener(v -> { if (codeEditor.canRedo()) codeEditor.redo(); });

        View btnSearch = findViewById(R.id.btn_search);
        if (btnSearch != null) {
            btnSearch.setOnClickListener(v -> DialogUtil.showSearchReplaceDialog(this, codeEditor));
        }

        View btnSettings = findViewById(R.id.btn_settings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> {
                Intent intent = new Intent(this, SettingActivity.class);
                intent.putExtra("project_path", currentProject.getRootPath());
                intent.putExtra("project_name", currentProject.getName());
                intent.putExtra("package_name", currentProject.getPackageName());
                startActivity(intent);
            });
        }

        findViewById(R.id.btn_run).setOnClickListener(v -> runBuild());
        findViewById(R.id.btn_more).setOnClickListener(this::showPopupMenu);

        findViewById(R.id.btn_action_save).setOnClickListener(v -> saveCurrentFile());
        findViewById(R.id.btn_action_select_all).setOnClickListener(v -> codeEditor.selectAll());
        findViewById(R.id.btn_action_copy).setOnClickListener(v -> codeEditor.copyText());
        findViewById(R.id.btn_action_paste).setOnClickListener(v -> codeEditor.pasteText());

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int pos = tab.getPosition();
                if (pos >= 0 && pos < openTabs.size()) {
                    switchToFile(openTabs.get(pos));
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupEditor() {
        codeEditor.setEditorLanguage(new JavaLanguage());
        codeEditor.setTextSize(14f);

        codeEditor.post(() -> {
            codeEditor.subscribeEvent(io.github.rosemoe.sora.event.SelectionChangeEvent.class, (event, unsubscribe) -> {
                int line = event.getLeft().line + 1;
                int col = event.getLeft().column + 1;
                tvCursorPos.setText(String.format(Locale.getDefault(), "Ln %d, Col %d", line, col));
            });
        });
    }

    private void setupPullDownSymbolBar() {
        pullDownSymbolBar = findViewById(R.id.pull_down_symbol_bar);
        btnToggleSymbolBar = findViewById(R.id.btn_toggle_symbol_bar);
        LinearLayout llSymbolsContainer = findViewById(R.id.ll_symbols_container);

        if (llSymbolsContainer != null) {
            symbolLayout = new SymbolLayout(this);
            symbolLayout.setTargetEditor(codeEditor);
            llSymbolsContainer.removeAllViews();
            llSymbolsContainer.addView(symbolLayout);
        }

        if (btnToggleSymbolBar != null) {
            btnToggleSymbolBar.setOnClickListener(v -> toggleSymbolBar());
        }
    }

    private void toggleSymbolBar() {
        if (pullDownSymbolBar == null) return;
        isSymbolBarVisible = !isSymbolBarVisible;

        if (isSymbolBarVisible) {
            pullDownSymbolBar.setVisibility(View.VISIBLE);
            pullDownSymbolBar.setTranslationY(-pullDownSymbolBar.getHeight());
            pullDownSymbolBar.animate()
                    .translationY(0)
                    .setDuration(280)
                    .setInterpolator(new OvershootInterpolator(1.1f))
                    .start();
            if (btnToggleSymbolBar != null) {
                btnToggleSymbolBar.animate().rotation(0).setDuration(250).start();
            }
        } else {
            pullDownSymbolBar.animate()
                    .translationY(-pullDownSymbolBar.getHeight())
                    .setDuration(220)
                    .setInterpolator(new AccelerateInterpolator())
                    .withEndAction(() -> pullDownSymbolBar.setVisibility(View.GONE))
                    .start();
            if (btnToggleSymbolBar != null) {
                btnToggleSymbolBar.animate().rotation(180).setDuration(250).start();
            }
        }
    }

    private void setupBottomSheet() {
        View bottomSheet = findViewById(R.id.bottom_sheet_panel);
        if (bottomSheet != null) {
            bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        }

        tvSheetTitle = findViewById(R.id.tv_sheet_title);
        tvSheetLog = findViewById(R.id.tv_sheet_log);
        svSheetLog = findViewById(R.id.sv_sheet_log);

        View btnClose = findViewById(R.id.btn_sheet_close);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> {
                if (bottomSheetBehavior != null) {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                }
            });
        }

        View btnCopy = findViewById(R.id.btn_copy_terminal_log);
        if (btnCopy != null) {
            btnCopy.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null && tvSheetLog != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("Build Log", tvSheetLog.getText()));
                    Toast.makeText(this, "Log copied to clipboard", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void setupTree() {
        rvFileTree.setLayoutManager(new LinearLayoutManager(this));
        File rootDir = new File(currentProject.getRootPath());
        FileTreeNode rootNode = buildTree(rootDir);
        List<FileTreeNode> flatList = new ArrayList<>();
        flattenTree(rootNode, flatList);

        rvFileTree.setAdapter(new TreeAdapter(flatList));
    }

    private void openDefaultFile() {
        File mainActivity = findMainActivity(currentProject.getSrcDir());
        if (mainActivity != null && mainActivity.exists()) {
            openFile(mainActivity);
        } else if (currentProject.getManifestFile().exists()) {
            openFile(currentProject.getManifestFile());
        }
    }

    private File findMainActivity(File dir) {
        if (!dir.exists()) return null;
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File f : files) {
            if (f.isDirectory()) {
                File res = findMainActivity(f);
                if (res != null) return res;
            } else if (f.getName().equals("MainActivity.java")) {
                return f;
            }
        }
        return null;
    }

    public void openFile(File file) {
        if (file.isDirectory()) return;

        if (!openTabs.contains(file)) {
            openTabs.add(file);
            TabLayout.Tab tab = tabLayout.newTab();
            tab.setText(file.getName());
            tabLayout.addTab(tab);
            tab.select();
        } else {
            int idx = openTabs.indexOf(file);
            TabLayout.Tab tab = tabLayout.getTabAt(idx);
            if (tab != null) tab.select();
        }
        switchToFile(file);
        drawerLayout.closeDrawer(GravityCompat.START);
    }

    private void switchToFile(File file) {
        if (activeFile != null) {
            fileContentCache.put(activeFile.getAbsolutePath(), codeEditor.getText().toString());
        }

        activeFile = file;
        String content = fileContentCache.get(file.getAbsolutePath());
        if (content == null) {
            content = FileUtil.readFile(file.getAbsolutePath());
            fileContentCache.put(file.getAbsolutePath(), content);
        }

        codeEditor.setText(content);

        if (file.getName().endsWith(".xml")) {
            // Sora editor XML mode
        } else {
            codeEditor.setEditorLanguage(new JavaLanguage());
        }
    }

    private void saveCurrentFile() {
        if (activeFile != null) {
            String content = codeEditor.getText().toString();
            FileUtil.writeFile(activeFile.getAbsolutePath(), content);
            fileContentCache.put(activeFile.getAbsolutePath(), content);
            Toast.makeText(this, "Saved: " + activeFile.getName(), Toast.LENGTH_SHORT).show();
        }
    }

    private void runBuild() {
        saveCurrentFile();

        if (bottomSheetBehavior != null) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
        if (tvSheetTitle != null) tvSheetTitle.setText("Building APK...");
        if (tvSheetLog != null) tvSheetLog.setText("");

        CompilerAsyncTask task = new CompilerAsyncTask(this, currentProject, new CompilerAsyncTask.CompilerCallback() {
            @Override
            public void onProgress(String message, int step, int totalSteps) {
                runOnUiThread(() -> {
                    if (tvSheetLog != null) tvSheetLog.append(message + "\n");
                    if (svSheetLog != null) svSheetLog.post(() -> svSheetLog.fullScroll(View.FOCUS_DOWN));
                });
            }

            @Override
            public void onComplete(CompilerResult result) {
                onCompleted(result);
            }

            @Override
            public void onCompleted(CompilerResult result) {
                runOnUiThread(() -> {
                    if (result.isSuccess()) {
                        if (tvSheetTitle != null) tvSheetTitle.setText("Build Succeeded!");
                        if (tvSheetLog != null) tvSheetLog.append("\n=== BUILD SUCCESSFUL ===\nOutput: " + result.getOutputApk().getAbsolutePath() + "\n");
                        DialogUtil.showApkUtilityDialog(CodeEditorActivity.this, result.getOutputApk(), currentProject.getName());
                    } else {
                        if (tvSheetTitle != null) tvSheetTitle.setText("Build Failed");
                        if (tvSheetLog != null) tvSheetLog.append("\n=== BUILD FAILED ===\n" + result.getErrorMessage() + "\n");
                    }
                    if (svSheetLog != null) svSheetLog.post(() -> svSheetLog.fullScroll(View.FOCUS_DOWN));
                });
            }
        });

        task.execute();
    }

    private void showPopupMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("Build Settings");
        popup.getMenu().add("Close Tab");
        popup.getMenu().add("Reload File");
        popup.setOnMenuItemClickListener(item -> {
            if ("Build Settings".equals(item.getTitle())) {
                Intent intent = new Intent(this, SettingActivity.class);
                intent.putExtra("project_path", currentProject.getRootPath());
                intent.putExtra("project_name", currentProject.getName());
                intent.putExtra("package_name", currentProject.getPackageName());
                startActivity(intent);
            } else if ("Close Tab".equals(item.getTitle())) {
                closeCurrentTab();
            } else if ("Reload File".equals(item.getTitle())) {
                if (activeFile != null) {
                    fileContentCache.remove(activeFile.getAbsolutePath());
                    switchToFile(activeFile);
                }
            }
            return true;
        });
        popup.show();
    }

    private void closeCurrentTab() {
        if (activeFile == null || openTabs.isEmpty()) return;
        int idx = openTabs.indexOf(activeFile);
        if (idx >= 0) {
            openTabs.remove(idx);
            tabLayout.removeTabAt(idx);
            if (!openTabs.isEmpty()) {
                int nextIdx = Math.max(0, idx - 1);
                tabLayout.getTabAt(nextIdx).select();
                switchToFile(openTabs.get(nextIdx));
            } else {
                activeFile = null;
                codeEditor.setText("");
            }
        }
    }

    private FileTreeNode buildTree(File file) {
        FileTreeNode node = new FileTreeNode(file);
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                Arrays.sort(children, (a, b) -> {
                    if (a.isDirectory() && !b.isDirectory()) return -1;
                    if (!a.isDirectory() && b.isDirectory()) return 1;
                    return a.getName().compareToIgnoreCase(b.getName());
                });
                for (File child : children) {
                    if (!child.getName().startsWith(".")) {
                        node.children.add(buildTree(child));
                    }
                }
            }
        }
        return node;
    }

    private void flattenTree(FileTreeNode node, List<FileTreeNode> list) {
        list.add(node);
        if (node.isExpanded) {
            for (FileTreeNode child : node.children) {
                flattenTree(child, list);
            }
        }
    }

    public static class FileTreeNode {
        public File file;
        public boolean isExpanded = true;
        public List<FileTreeNode> children = new ArrayList<>();
        public int depth = 0;

        public FileTreeNode(File file) {
            this.file = file;
        }
    }

    private class TreeAdapter extends RecyclerView.Adapter<TreeAdapter.TreeViewHolder> {
        private final List<FileTreeNode> nodes;

        public TreeAdapter(List<FileTreeNode> nodes) {
            this.nodes = nodes;
        }

        @NonNull
        @Override
        public TreeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(CodeEditorActivity.this).inflate(
                    viewType == 0 ? R.layout.item_file_tree_dir : R.layout.item_file_tree_file,
                    parent,
                    false
            );
            return new TreeViewHolder(v);
        }

        @Override
        public int getItemViewType(int position) {
            return nodes.get(position).file.isDirectory() ? 0 : 1;
        }

        @Override
        public void onBindViewHolder(@NonNull TreeViewHolder holder, int position) {
            FileTreeNode node = nodes.get(position);
            holder.tvName.setText(node.file.getName());

            holder.itemView.setPadding(node.depth * LayoutToolKit.dpToPx(CodeEditorActivity.this, 16), 4, 8, 4);

            holder.itemView.setOnClickListener(v -> {
                if (node.file.isDirectory()) {
                    node.isExpanded = !node.isExpanded;
                    setupTree();
                } else {
                    openFile(node.file);
                }
            });
        }

        @Override
        public int getItemCount() {
            return nodes.size();
        }

        class TreeViewHolder extends RecyclerView.ViewHolder {
            TextView tvName;
            TreeViewHolder(View v) {
                super(v);
                TextView dir = v.findViewById(R.id.tv_dir_name);
                TextView file = v.findViewById(R.id.tv_file_name);
                tvName = dir != null ? dir : file;
            }
        }
    }
}
