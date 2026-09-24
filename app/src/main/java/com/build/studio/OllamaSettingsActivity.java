package com.build.studio;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class OllamaSettingsActivity extends AppCompatActivity {

    private EditText etEndpoint, etApiKey, etModelCustom, etSystemPrompt;
    private Spinner spModels;
    private TextView tvStatus;
    private final List<String> modelList = new ArrayList<>();
    private ArrayAdapter<String> modelAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ollama_settings);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        etEndpoint = findViewById(R.id.et_endpoint);
        etApiKey = findViewById(R.id.et_api_key);
        etModelCustom = findViewById(R.id.et_model_custom);
        etSystemPrompt = findViewById(R.id.et_system_prompt);
        spModels = findViewById(R.id.sp_models);
        tvStatus = findViewById(R.id.tv_connection_status);

        modelList.add("qwen2.5-coder:latest");
        modelList.add("qwen2.5-coder:7b");
        modelList.add("glm-4");
        modelList.add("deepseek-coder");
        modelList.add("codellama");
        modelList.add("llama3");

        modelAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, modelList);
        spModels.setAdapter(modelAdapter);

        loadPreferences();

        findViewById(R.id.btn_test_connection).setOnClickListener(v -> testConnection());
        findViewById(R.id.btn_save_settings).setOnClickListener(v -> savePreferences());
    }

    private void loadPreferences() {
        SharedPreferences sp = getSharedPreferences("build_ai_prefs", Context.MODE_PRIVATE);
        etEndpoint.setText(sp.getString("endpoint", "http://10.0.2.2:11434"));
        etApiKey.setText(sp.getString("api_key", ""));
        String model = sp.getString("model", "qwen2.5-coder:latest");
        etModelCustom.setText(model);

        int pos = modelList.indexOf(model);
        if (pos >= 0) {
            spModels.setSelection(pos);
        }

        etSystemPrompt.setText(sp.getString("system_prompt",
                "You are Build AI, an expert Android developer and coding assistant inside the Build Studio app. " +
                "You can create files, folders, write Java and XML code, fix compilation errors, and answer questions.\n" +
                "IMPORTANT XML RULES: Every XML file you generate must start exactly with the declaration: <?xml version=\"1.0\" encoding=\"utf-8\"?>.\n" +
                "When proposing code changes, format them as:\n" +
                "FILE: <relative_path>\n" +
                "ACTION: MODIFY or CREATE\n" +
                "DESCRIPTION: <brief_summary>\n" +
                "```java or ```xml\n" +
                "<full_file_code>\n" +
                "```"));
    }

    private void savePreferences() {
        SharedPreferences sp = getSharedPreferences("build_ai_prefs", Context.MODE_PRIVATE);
        String customModel = etModelCustom.getText().toString().trim();
        String activeModel = customModel.isEmpty() ? spModels.getSelectedItem().toString() : customModel;

        sp.edit()
                .putString("endpoint", etEndpoint.getText().toString().trim())
                .putString("api_key", etApiKey.getText().toString().trim())
                .putString("model", activeModel)
                .putString("system_prompt", etSystemPrompt.getText().toString().trim())
                .apply();

        Toast.makeText(this, "Settings saved successfully", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void testConnection() {
        tvStatus.setText("Testing connection...");
        String endpoint = etEndpoint.getText().toString().trim();
        String apiKey = etApiKey.getText().toString().trim();

        String url = endpoint.endsWith("/") ? endpoint + "api/tags" : endpoint + "/api/tags";

        OkHttpClient client = new OkHttpClient();
        Request.Builder reqBuilder = new Request.Builder().url(url).get();
        if (!apiKey.isEmpty()) {
            reqBuilder.addHeader("Authorization", "Bearer " + apiKey);
        }

        client.newCall(reqBuilder.build()).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> tvStatus.setText("Connection failed: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String resp = response.body() != null ? response.body().string() : "";
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        tvStatus.setText("Connected successfully!");
                        try {
                            JSONObject json = new JSONObject(resp);
                            if (json.has("models")) {
                                JSONArray models = json.getJSONArray("models");
                                modelList.clear();
                                for (int i = 0; i < models.length(); i++) {
                                    modelList.add(models.getJSONObject(i).getString("name"));
                                }
                                modelAdapter.notifyDataSetChanged();
                            }
                        } catch (Exception ignored) {}
                    } else {
                        tvStatus.setText("Error HTTP " + response.code());
                    }
                });
            }
        });
    }
}
