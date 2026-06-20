package com.yourdiplomicabot;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class SlotActivity extends AppCompatActivity {

    private String botUrl, sessionToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_slot);

        botUrl       = getIntent().getStringExtra("bot_url");
        sessionToken = getIntent().getStringExtra("session_token");

        SharedPreferences prefs = getSharedPreferences("diplo", MODE_PRIVATE);
        String username = prefs.getString("username", "");

        TextView tvWelcome = findViewById(R.id.tv_welcome);
        tvWelcome.setText("أهلاً " + username + " 👋\nاختار الحساب اللي عايز تستخرج توكنه");

        Button btnSlot1 = findViewById(R.id.btn_slot1);
        Button btnSlot2 = findViewById(R.id.btn_slot2);
        Button btnLogout = findViewById(R.id.btn_logout);

        btnSlot1.setOnClickListener(v -> openWebView(1));
        btnSlot2.setOnClickListener(v -> openWebView(2));

        btnLogout.setOnClickListener(v -> {
            prefs.edit().remove("session_token").apply();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }

    private void openWebView(int slot) {
        Intent i = new Intent(this, WebViewActivity.class);
        i.putExtra("bot_url", botUrl);
        i.putExtra("session_token", sessionToken);
        i.putExtra("slot", slot);
        startActivity(i);
    }
}
