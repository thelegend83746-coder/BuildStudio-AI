package com.apk.builder;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.apk.builder.model.Project;
import com.build.studio.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends AppCompatActivity {

    private RecyclerView rvProjects;
    private View emptyStateView;
    private ProjectAdapter adapter;
    private final List<Project> projectList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rvProjects = findViewById(R.id.rv_projects);
        emptyStateView = findViewById(R.id.ll_empty_state);
        FloatingActionButton fabCreate = findViewById(R.id.fab_create_project);

        rvProjects.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProjectAdapter();
        rvProjects.setAdapter(adapter);

        fabCreate.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, CreateProjectActivity.class);
            startActivity(intent);
            overridePendingTransition(R.anim.animate_slide_left_enter, R.anim.animate_slide_left_exit);
        });

        View btnSettings = findViewById(R.id.btn_app_settings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, SettingActivity.class);
                startActivity(intent);
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProjects();
    }

    private void loadProjects() {
        projectList.clear();
        File projectsDir = new File(Environment.getExternalStorageDirectory(), "BUILD STUDIO/projects");
        if (!projectsDir.exists()) {
            projectsDir.mkdirs();
        }

        File[] files = projectsDir.listFiles();
        if (files != null) {
            Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (File dir : files) {
                if (dir.isDirectory()) {
                    Project p = Project.loadFromDirectory(dir);
                    if (p != null) {
                        projectList.add(p);
                    }
                }
            }
        }

        adapter.notifyDataSetChanged();
        if (emptyStateView != null) {
            emptyStateView.setVisibility(projectList.isEmpty() ? View.VISIBLE : View.GONE);
        }
        rvProjects.setVisibility(projectList.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private class ProjectAdapter extends RecyclerView.Adapter<ProjectAdapter.ProjectViewHolder> {

        @NonNull
        @Override
        public ProjectViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_project_card, parent, false);
            return new ProjectViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ProjectViewHolder holder, int position) {
            Project project = projectList.get(position);
            holder.tvAppName.setText(project.getName());
            holder.tvPackageName.setText(project.getPackageName());
            holder.tvSdkBadge.setText(String.format("SDK %d-%d", project.getMinSdk(), project.getTargetSdk()));

            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault());
            holder.tvTimestamp.setText(sdf.format(new Date(project.getLastModified())));

            if (project.getIconPath() != null && new File(project.getIconPath()).exists()) {
                Bitmap bmp = BitmapFactory.decodeFile(project.getIconPath());
                if (bmp != null) holder.ivAppIcon.setImageBitmap(bmp);
                else holder.ivAppIcon.setImageResource(R.drawable.ic_launcher);
            } else {
                holder.ivAppIcon.setImageResource(R.drawable.ic_launcher);
            }

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, CodeEditorActivity.class);
                intent.putExtra("project_path", project.getRootPath());
                intent.putExtra("project_name", project.getName());
                intent.putExtra("package_name", project.getPackageName());
                startActivity(intent);
                overridePendingTransition(R.anim.animate_slide_left_enter, R.anim.animate_slide_left_exit);
            });

            holder.itemView.setOnLongClickListener(v -> {
                PopupMenu popup = new PopupMenu(MainActivity.this, v);
                popup.getMenu().add("Open in Editor");
                popup.getMenu().add("Build Settings");
                popup.getMenu().add("Delete Project");
                popup.setOnMenuItemClickListener(item -> {
                    if ("Open in Editor".equals(item.getTitle())) {
                        holder.itemView.performClick();
                    } else if ("Build Settings".equals(item.getTitle())) {
                        Intent intent = new Intent(MainActivity.this, SettingActivity.class);
                        intent.putExtra("project_path", project.getRootPath());
                        intent.putExtra("project_name", project.getName());
                        intent.putExtra("package_name", project.getPackageName());
                        startActivity(intent);
                    } else if ("Delete Project".equals(item.getTitle())) {
                        confirmDeleteProject(project);
                    }
                    return true;
                });
                popup.show();
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return projectList.size();
        }

        class ProjectViewHolder extends RecyclerView.ViewHolder {
            ImageView ivAppIcon;
            TextView tvAppName, tvPackageName, tvSdkBadge, tvTimestamp;

            ProjectViewHolder(View v) {
                super(v);
                ivAppIcon = v.findViewById(R.id.iv_project_icon);
                tvAppName = v.findViewById(R.id.tv_project_name);
                tvPackageName = v.findViewById(R.id.tv_project_package);
                tvSdkBadge = v.findViewById(R.id.tv_project_sdk_range);
                tvTimestamp = v.findViewById(R.id.tv_project_timestamp);
            }
        }
    }

    private void confirmDeleteProject(Project project) {
        new AlertDialog.Builder(this, R.style.CyberDialogTheme)
                .setTitle("Delete Project")
                .setMessage("Are you sure you want to permanently delete '" + project.getName() + "'?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    FileUtil.deleteRecursive(new File(project.getRootPath()));
                    loadProjects();
                    Toast.makeText(this, "Project deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
