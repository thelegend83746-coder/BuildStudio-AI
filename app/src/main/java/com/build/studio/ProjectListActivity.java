package com.build.studio;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.apk.builder.model.Project;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ProjectListActivity extends AppCompatActivity {

    private RecyclerView rvProjects;
    private final List<Project> list = new ArrayList<>();
    private ProjectAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Clean list initialization

        rvProjects = findViewById(R.id.rv_projects);
        if (rvProjects != null) {
            rvProjects.setLayoutManager(new LinearLayoutManager(this));
            adapter = new ProjectAdapter();
            rvProjects.setAdapter(adapter);
        }

        loadProjects();
    }

    private void loadProjects() {
        list.clear();
        File dir = new File(Environment.getExternalStorageDirectory(), "BUILD STUDIO/projects");
        if (!dir.exists()) dir.mkdirs();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory() && (new File(f, "app").exists() || new File(f, "src/main").exists())) {
                    list.add(new Project(f.getName(), "com.example." + f.getName().toLowerCase(), f.getAbsolutePath()));
                }
            }
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private class ProjectAdapter extends RecyclerView.Adapter<ProjectAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_project_card, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Project p = list.get(position);
            holder.tvName.setText(p.getName());
            holder.tvPackage.setText(p.getPackageName());
            holder.tvSdk.setText("SDK " + p.getMinSdk() + " - " + p.getTargetSdk());

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(ProjectListActivity.this, CodeEditorActivity.class);
                intent.putExtra("project_path", p.getRootPath());
                intent.putExtra("project_name", p.getName());
                intent.putExtra("package_name", p.getPackageName());
                startActivity(intent);
                finish();
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvPackage, tvSdk;
            ViewHolder(View v) {
                super(v);
                tvName = v.findViewById(R.id.tv_project_name);
                tvPackage = v.findViewById(R.id.tv_project_package);
                tvSdk = v.findViewById(R.id.tv_project_sdk_range);
            }
        }
    }
}
