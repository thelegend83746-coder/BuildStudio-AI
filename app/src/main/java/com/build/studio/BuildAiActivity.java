package com.build.studio;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.apk.builder.FileUtil;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BuildAiActivity extends AppCompatActivity {

    public static class FileAction {
        public String filePath;
        public String actionType; // CREATE, MODIFY
        public String description;
        public String code;
        public boolean applied;

        public FileAction(String filePath, String actionType, String description, String code) {
            this.filePath = filePath;
            this.actionType = actionType;
            this.description = description;
            this.code = code;
            this.applied = false;
        }
    }

    public static class ChatMessage {
        public static final int TYPE_USER = 1;
        public static final int TYPE_ASSISTANT = 2;

        public int type;
        public String text;
        public String plan;
        public List<FileAction> actions = new ArrayList<>();

        public ChatMessage(int type, String text) {
            this.type = type;
            this.text = text;
        }
    }

    private RecyclerView rvChat;
    private EditText etMessage;
    private View layoutThinking;
    private TextView tvThinkingStatus, tvActiveModel;
    private ChatAdapter chatAdapter;
    private final List<ChatMessage> messages = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final OkHttpClient httpClient = new OkHttpClient();
    private Call currentCall;

    private String projectPath;
    private String activeFilePath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.build_ai);

        projectPath = getIntent().getStringExtra("project_path");
        activeFilePath = getIntent().getStringExtra("active_file");
        String errorLog = getIntent().getStringExtra("error_log");

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_ai_settings).setOnClickListener(v -> startActivity(new Intent(this, OllamaSettingsActivity.class)));

        rvChat = findViewById(R.id.rv_chat);
        etMessage = findViewById(R.id.et_message);
        layoutThinking = findViewById(R.id.layout_thinking);
        tvThinkingStatus = findViewById(R.id.tv_thinking_status);
        tvActiveModel = findViewById(R.id.tv_active_model);

        rvChat.setLayoutManager(new LinearLayoutManager(this));
        chatAdapter = new ChatAdapter();
        rvChat.setAdapter(chatAdapter);

        findViewById(R.id.btn_send).setOnClickListener(v -> sendMessage(etMessage.getText().toString().trim()));
        findViewById(R.id.btn_stop).setOnClickListener(v -> {
            if (currentCall != null) {
                currentCall.cancel();
            }
            layoutThinking.setVisibility(View.GONE);
        });

        // Welcome message
        ChatMessage welcome = new ChatMessage(ChatMessage.TYPE_ASSISTANT,
                "Hello! I am Build AI. Ask me to write code, create layouts, add activities, fix compilation issues or explain anything about your project.");
        messages.add(welcome);
        chatAdapter.notifyItemInserted(0);

        if (errorLog != null && !errorLog.isEmpty()) {
            sendMessage("The compiler encountered errors while building the project:\n```\n" + errorLog + "\n```\nPlease diagnose the issue and generate the fix.");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences sp = getSharedPreferences("build_ai_prefs", Context.MODE_PRIVATE);
        String model = sp.getString("model", "qwen2.5-coder:latest");
        tvActiveModel.setText(model);
    }

    private void sendMessage(String text) {
        if (text.isEmpty()) return;
        etMessage.setText("");

        ChatMessage userMsg = new ChatMessage(ChatMessage.TYPE_USER, text);
        messages.add(userMsg);
        int pos = messages.size() - 1;
        chatAdapter.notifyItemInserted(pos);
        rvChat.scrollToPosition(pos);

        requestAiResponse(text);
    }

    private void requestAiResponse(String prompt) {
        layoutThinking.setVisibility(View.VISIBLE);
        tvThinkingStatus.setText("Build AI is thinking...");

        SharedPreferences sp = getSharedPreferences("build_ai_prefs", Context.MODE_PRIVATE);
        String endpoint = sp.getString("endpoint", "http://10.0.2.2:11434");
        String apiKey = sp.getString("api_key", "");
        String model = sp.getString("model", "qwen2.5-coder:latest");
        String systemPrompt = sp.getString("system_prompt",
                "You are Build AI, an expert Android developer and coding assistant inside the Build Studio app. " +
                "You can create files, folders, write Java and XML code, fix compilation errors, and answer questions.\n" +
                "IMPORTANT XML RULES: Every XML file you generate must start exactly with the declaration: <?xml version=\"1.0\" encoding=\"utf-8\"?>.\n" +
                "When proposing code changes, format them as:\n" +
                "FILE: <relative_path_from_project_root>\n" +
                "ACTION: MODIFY or CREATE\n" +
                "DESCRIPTION: <brief_summary>\n" +
                "```java or ```xml\n" +
                "<full_file_code>\n" +
                "```");

        try {
            JSONObject root = new JSONObject();
            root.put("model", model);
            root.put("stream", false);

            JSONArray messagesArr = new JSONArray();

            JSONObject sys = new JSONObject();
            sys.put("role", "system");
            sys.put("content", systemPrompt);
            messagesArr.put(sys);

            for (ChatMessage m : messages) {
                JSONObject msg = new JSONObject();
                msg.put("role", m.type == ChatMessage.TYPE_USER ? "user" : "assistant");
                msg.put("content", m.text);
                messagesArr.put(msg);
            }

            root.put("messages", messagesArr);

            String url = endpoint.endsWith("/") ? endpoint : endpoint + "/";
            if (!url.contains("/chat/completions") && !url.contains("/api/chat")) {
                if (url.contains(":11434")) {
                    url += "api/chat";
                } else {
                    url += "v1/chat/completions";
                }
            }

            RequestBody body = RequestBody.create(root.toString(), MediaType.parse("application/json"));
            Request.Builder reqBuilder = new Request.Builder().url(url).post(body);

            if (!apiKey.isEmpty()) {
                reqBuilder.addHeader("Authorization", "Bearer " + apiKey);
            }

            currentCall = httpClient.newCall(reqBuilder.build());
            currentCall.enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    mainHandler.post(() -> {
                        layoutThinking.setVisibility(View.GONE);
                        addAssistantResponse("Network request failed: " + e.getMessage() + "\nPlease verify endpoint and API key in Settings.");
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    String respStr = response.body() != null ? response.body().string() : "";
                    mainHandler.post(() -> {
                        layoutThinking.setVisibility(View.GONE);
                        if (!response.isSuccessful()) {
                            addAssistantResponse("API Error (" + response.code() + "):\n" + respStr);
                            return;
                        }

                        try {
                            JSONObject json = new JSONObject(respStr);
                            String content = "";
                            if (json.has("message")) {
                                content = json.getJSONObject("message").optString("content", "");
                            } else if (json.has("choices")) {
                                JSONArray choices = json.getJSONArray("choices");
                                if (choices.length() > 0) {
                                    content = choices.getJSONObject(0).getJSONObject("message").optString("content", "");
                                }
                            }
                            if (content.isEmpty()) content = respStr;
                            addAssistantResponse(content);
                        } catch (Exception e) {
                            addAssistantResponse("Response parsing error: " + e.getMessage() + "\nRaw: " + respStr);
                        }
                    });
                }
            });

        } catch (Exception e) {
            layoutThinking.setVisibility(View.GONE);
            addAssistantResponse("Error building request: " + e.getMessage());
        }
    }

    private void addAssistantResponse(String text) {
        ChatMessage assistantMsg = new ChatMessage(ChatMessage.TYPE_ASSISTANT, text);
        parseActions(assistantMsg);

        messages.add(assistantMsg);
        int pos = messages.size() - 1;
        chatAdapter.notifyItemInserted(pos);
        rvChat.scrollToPosition(pos);
    }

    private void parseActions(ChatMessage msg) {
        // Pattern looking for FILE: ... ACTION: ... ```...```
        Pattern pattern = Pattern.compile("FILE:\\s*([^\\n]+)\\s*ACTION:\\s*([^\\n]+)\\s*(?:DESCRIPTION:\\s*([^\\n]+))?\\s*```[a-zA-Z]*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(msg.text);

        while (matcher.find()) {
            String file = matcher.group(1).trim();
            String action = matcher.group(2).trim();
            String desc = matcher.group(3) != null ? matcher.group(3).trim() : "Proposed changes";
            String code = matcher.group(4).trim();

            msg.actions.add(new FileAction(file, action, desc, code));
        }

        // Check for plan card
        if (msg.text.contains("PLAN:") || msg.text.contains("ANALYSIS:")) {
            msg.plan = "Build AI analyzed your request and prepared the code changes below.";
        }
    }

    private void applyAction(FileAction action) {
        if (projectPath == null) {
            Toast.makeText(this, "No active project context", Toast.LENGTH_SHORT).show();
            return;
        }

        File targetFile = new File(projectPath, action.filePath);
        boolean success = FileUtil.writeFile(targetFile.getAbsolutePath(), action.code);
        if (success) {
            action.applied = true;
            chatAdapter.notifyDataSetChanged();
            Toast.makeText(this, "Applied: " + action.filePath, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Failed to write " + action.filePath, Toast.LENGTH_SHORT).show();
        }
    }

    private class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @Override
        public int getItemViewType(int position) {
            return messages.get(position).type;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == ChatMessage.TYPE_USER) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.chat_item_user, parent, false);
                return new UserViewHolder(v);
            } else {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.chat_item_assistant, parent, false);
                return new AssistantViewHolder(v);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ChatMessage msg = messages.get(position);
            if (holder instanceof UserViewHolder) {
                ((UserViewHolder) holder).tvText.setText(msg.text);
            } else if (holder instanceof AssistantViewHolder) {
                AssistantViewHolder avh = (AssistantViewHolder) holder;
                avh.tvText.setText(msg.text);

                // Render Action cards dynamically
                avh.container.removeAllViews();
                avh.container.addView(avh.tvText);

                if (msg.plan != null) {
                    View planCard = LayoutInflater.from(BuildAiActivity.this).inflate(R.layout.chat_plan_card, avh.container, false);
                    ((TextView) planCard.findViewById(R.id.tv_plan_body)).setText(msg.plan);
                    avh.container.addView(planCard);
                }

                for (FileAction action : msg.actions) {
                    View actionCard = LayoutInflater.from(BuildAiActivity.this).inflate(R.layout.chat_action_card, avh.container, false);
                    TextView tvType = actionCard.findViewById(R.id.tv_action_type);
                    TextView tvPath = actionCard.findViewById(R.id.tv_file_path);
                    TextView tvDesc = actionCard.findViewById(R.id.tv_action_desc);
                    TextView tvCode = actionCard.findViewById(R.id.tv_code_preview);
                    Button btnApply = actionCard.findViewById(R.id.btn_apply_action);

                    tvType.setText(action.actionType);
                    tvPath.setText(action.filePath);
                    tvDesc.setText(action.description);
                    tvCode.setText(action.code);

                    if (action.applied) {
                        btnApply.setText("Applied ✓");
                        btnApply.setEnabled(false);
                    } else {
                        btnApply.setText("Apply Change");
                        btnApply.setEnabled(true);
                        btnApply.setOnClickListener(v -> applyAction(action));
                    }
                    avh.container.addView(actionCard);
                }
            }
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        class UserViewHolder extends RecyclerView.ViewHolder {
            TextView tvText;
            UserViewHolder(View v) {
                super(v);
                tvText = v.findViewById(R.id.tv_user_text);
            }
        }

        class AssistantViewHolder extends RecyclerView.ViewHolder {
            TextView tvText;
            LinearLayout container;
            AssistantViewHolder(View v) {
                super(v);
                container = (LinearLayout) ((androidx.cardview.widget.CardView) v).getChildAt(0);
                tvText = v.findViewById(R.id.tv_assistant_text);
            }
        }
    }
}
