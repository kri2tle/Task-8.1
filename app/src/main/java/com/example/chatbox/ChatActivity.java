package com.example.chatbox;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import android.util.Log;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.Button;

public class ChatActivity extends AppCompatActivity {
    private List<Message> messageList = new ArrayList<>();
    private ChatAdapter chatAdapter;
    private ChatService chatService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // Initialize chat service
        chatService = new ChatService();

        String username = getIntent().getStringExtra("username");
        TextView welcomeUser = findViewById(R.id.welcomeUser);
        welcomeUser.setText("Welcome " + username + "!");

        RecyclerView chatRecyclerView = findViewById(R.id.chatRecyclerView);
        chatAdapter = new ChatAdapter(messageList);
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        chatRecyclerView.setAdapter(chatAdapter);

        // Add bot greeting message
        messageList.add(new Message("Hello! I'm your AI assistant. How can I help you today?", false));
        chatAdapter.notifyItemInserted(messageList.size() - 1);

        EditText messageEditText = findViewById(R.id.messageEditText);
        ImageButton sendButton = findViewById(R.id.sendButton);

        sendButton.setOnClickListener(v -> {
            String message = messageEditText.getText().toString().trim();
            if (!message.isEmpty()) {
                // Add user message to chat
                messageList.add(new Message(message, true));
                chatAdapter.notifyItemInserted(messageList.size() - 1);
                chatRecyclerView.scrollToPosition(messageList.size() - 1);
                messageEditText.setText("");

                // Show typing indicator
                messageList.add(new Message("...", false));
                int typingIndex = messageList.size() - 1;
                chatAdapter.notifyItemInserted(typingIndex);
                chatRecyclerView.scrollToPosition(typingIndex);

                // Get response from chat service
                chatService.sendMessage(message, new ChatService.ChatCallback() {
                    @Override
                    public void onResponse(String response) {
                        runOnUiThread(() -> {
                            // Remove typing indicator
                            messageList.remove(typingIndex);
                            chatAdapter.notifyItemRemoved(typingIndex);

                            // Add bot response
                            messageList.add(new Message(response, false));
                            chatAdapter.notifyItemInserted(messageList.size() - 1);
                            chatRecyclerView.scrollToPosition(messageList.size() - 1);
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            // Remove typing indicator
                            messageList.remove(typingIndex);
                            chatAdapter.notifyItemRemoved(typingIndex);

                            // Show error message
                            messageList.add(new Message("Sorry, I encountered an error: " + error, false));
                            chatAdapter.notifyItemInserted(messageList.size() - 1);
                            chatRecyclerView.scrollToPosition(messageList.size() - 1);
                            Log.e("ChatService", "Error: " + error);
                        });
                    }
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (chatService != null) {
            chatService.clearConversation();
        }
    }
}
