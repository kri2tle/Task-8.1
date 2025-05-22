package com.example.chatbox;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.MediaType;
import okhttp3.Response;
import org.json.JSONObject;
import org.json.JSONArray;

public class ChatService {
    private static final String TAG = "ChatService";
    private static final String API_KEY = "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";
    private static final String API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private final OkHttpClient client;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final List<String> conversationHistory;

    public ChatService() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.conversationHistory = new ArrayList<>();
    }

    public interface ChatCallback {
        void onResponse(String response);
        void onError(String error);
    }

    public void sendMessage(String userInput, ChatCallback callback) {
        executor.execute(() -> {
            try {
                // Build the conversation as a list of messages
                JSONArray messages = new JSONArray();
                for (String msg : conversationHistory) {
                    JSONObject messageObj = new JSONObject();
                    messageObj.put("role", msg.startsWith("User:") ? "user" : "assistant");
                    messageObj.put("content", msg.replaceFirst("^(User:|Bot:)", "").trim());
                    messages.put(messageObj);
                }
                // Add the new user message
                JSONObject userMessage = new JSONObject();
                userMessage.put("role", "user");
                userMessage.put("content", userInput);
                messages.put(userMessage);

                // Create request body for OpenRouter
                JSONObject requestBody = new JSONObject();
                requestBody.put("model", "meta-llama/llama-3.3-8b-instruct:free");
                requestBody.put("messages", messages);

                String requestBodyString = requestBody.toString();

                Request request = new Request.Builder()
                        .url(API_URL)
                        .addHeader("Authorization", "Bearer " + API_KEY)
                        .addHeader("Content-Type", "application/json")
                        .post(RequestBody.create(
                                requestBodyString,
                                MediaType.parse("application/json")
                        ))
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    String responseBody = response.body() != null ? response.body().string() : null;

                    if (!response.isSuccessful()) {
                        String errorDetail = responseBody != null ? responseBody : "";
                        final String finalErrorMessage = "HTTP error: " + response.code() + " - " + errorDetail;
                        mainHandler.post(() -> callback.onError(finalErrorMessage));
                        return;
                    }

                    if (responseBody == null) {
                        mainHandler.post(() -> callback.onError("Empty response from server"));
                        return;
                    }

                    try {
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        JSONArray choices = jsonResponse.getJSONArray("choices");
                        if (choices.length() == 0) {
                            mainHandler.post(() -> callback.onError("No choices in response"));
                            return;
                        }
                        JSONObject firstChoice = choices.getJSONObject(0);
                        JSONObject message = firstChoice.getJSONObject("message");
                        String botReply = message.getString("content").trim();
                        conversationHistory.add("Bot: " + botReply);
                        mainHandler.post(() -> callback.onResponse(botReply));
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing response", e);
                        mainHandler.post(() -> callback.onError("Error parsing response: " + e.getMessage()));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in sendMessage", e);
                mainHandler.post(() -> callback.onError("Exception: " + e.getMessage()));
            }
        });
    }

    public void clearConversation() {
        conversationHistory.clear();
    }
} 