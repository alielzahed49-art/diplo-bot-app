package com.yourdiplomicabot;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
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

        etUrl      = findViewById(R.id.et_url);
        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        btnLogin   = findViewById(R.id.btn_login);
        tvError    = findViewById(R.id.tv_error);
        pbLoading  = findViewById(R.id.pb_loading);

        // Pre-fill saved URL
        SharedPreferences prefs = getSharedPreferences("diplo", MODE_PRIVATE);
        String savedUrl = prefs.getString("bot_url", "https://diplomacia-saas.onrender.com");
        etUrl.setText(savedUrl);

        // If already logged in, skip to slot chooser
        String savedToken = prefs.getString("session_token", "");
        if (!savedToken.isEmpty()) {
            openSlotChooser(prefs.getString("bot_url", savedUrl), savedToken);
            return;
        }

        btnLogin.setOnClickListener(v -> doLogin());
    }

    private void doLogin() {
        String url      = etUrl.getText().toString().trim().replaceAll("/$", "");
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (url.isEmpty() || username.isEmpty() || password.isEmpty()) {
            tvError.setText("⚠️ اكمل كل الحقول");
            tvError.setVisibility(View.VISIBLE);
            return;
        }

        tvError.setVisibility(View.GONE);
        pbLoading.setVisibility(View.VISIBLE);
        btnLogin.setEnabled(false);

        new Thread(() -> {
            try {
                // Login to bot
                URL loginUrl = new URL(url + "/login");
                HttpURLConnection conn = (HttpURLConnection) loginUrl.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                // Send form data
                String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setRequestProperty("Content-Length", String.valueOf(body.length()));
                conn.setInstanceFollowRedirects(false);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes("UTF-8"));
                }

                int code = conn.getResponseCode();
                // Get session cookie
                String cookies = conn.getHeaderField("Set-Cookie");
                String sessionToken = "";
                if (cookies != null) {
                    for (String c : cookies.split(";")) {
                        if (c.trim().startsWith("session=")) {
                            sessionToken = c.trim().substring(8);
                            break;
                        }
                    }
                }

                // Check if login succeeded (redirect to / means success)
                String location = conn.getHeaderField("Location");
                boolean success = (code == 302 && location != null && !location.contains("login")) ||
                                  code == 200;

                if (success && !sessionToken.isEmpty()) {
                    // Save session
                    SharedPreferences prefs = getSharedPreferences("diplo", MODE_PRIVATE);
                    prefs.edit().putString("bot_url", url)
                                .putString("session_token", sessionToken)
                                .putString("username", username)
                                .apply();

                    final String finalToken = sessionToken;
                    runOnUiThread(() -> openSlotChooser(url, finalToken));
                } else {
                    runOnUiThread(() -> {
                        pbLoading.setVisibility(View.GONE);
                        btnLogin.setEnabled(true);
                        tvError.setText("❌ خطأ في تسجيل الدخول — تأكد من الاسم وكلمة السر");
                        tvError.setVisibility(View.VISIBLE);
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pbLoading.setVisibility(View.GONE);
                    btnLogin.setEnabled(true);
                    tvError.setText("❌ خطأ في الاتصال: " + e.getMessage());
                    tvError.setVisibility(View.VISIBLE);
                });
            }
        }).start();
    }

    private void openSlotChooser(String url, String token) {
        Intent i = new Intent(this, SlotActivity.class);
        i.putExtra("bot_url", url);
        i.putExtra("session_token", token);
        startActivity(i);
        finish();
    }
}
