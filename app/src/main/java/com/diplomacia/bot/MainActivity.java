package com.yourdiplomicabot;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.io.*;
import java.net.*;

public class MainActivity extends AppCompatActivity {

    private EditText etUrl, etUsername, etPassword;
    private Button btnLogin;
    private TextView tvError;
    private ProgressBar pbLoading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etUrl = findViewById(R.id.et_url);
        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);
        tvError = findViewById(R.id.tv_error);
        pbLoading = findViewById(R.id.pb_loading);

        SharedPreferences prefs = getSharedPreferences("diplo", MODE_PRIVATE);
        String savedUrl = prefs.getString("bot_url", "https://diplomacia-saas.onrender.com");
        etUrl.setText(savedUrl);

        etUsername.setText("12");
        etPassword.setText("12");

        String savedToken = prefs.getString("session_token", "");
        if (!savedToken.isEmpty()) {
            openSlotChooser(savedUrl, savedToken);
            return;
        }

        btnLogin.setOnClickListener(v -> doLogin());

        new Handler().postDelayed(this::doLogin, 1000);
    }

    private void doLogin() {
        // (انسخ الـ doLogin كامل من الملف القديم بتاعك)
        // ... 
    }

    private void openSlotChooser(String url, String token) {
        Intent i = new Intent(this, SlotActivity.class);
        i.putExtra("bot_url", url);
        i.putExtra("session_token", token);
        startActivity(i);
        finish();
    }
}
