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
                java.net.URL loginUrl = new java.net.URL(url + "/login");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) loginUrl.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setInstanceFollowRedirects(false);

                String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
                byte[] bodyBytes = body.getBytes("UTF-8");
                conn.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));

                try (java.io.OutputStream os = conn.getOutputStream()) {
                    os.write(bodyBytes);
                }

                int code = conn.getResponseCode();

                // Read response body
                java.io.InputStream is = (code >= 200 && code < 400) 
                    ? conn.getInputStream() 
                    : conn.getErrorStream();
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(is, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                String responseText = sb.toString();

                // Extract session cookie
                String sessionToken = "";
                for (String header : conn.getHeaderFields().getOrDefault("Set-Cookie", 
                        new java.util.ArrayList<>())) {
                    if (header.startsWith("session=")) {
                        sessionToken = header.split(";")[0].substring(8);
                        break;
                    }
                }

                // Parse JSON response
                boolean ok = responseText.contains("\"ok\":true") || responseText.contains("\"ok\": true");

                if (ok && !sessionToken.isEmpty()) {
                    SharedPreferences prefs = getSharedPreferences("diplo", MODE_PRIVATE);
                    prefs.edit().putString("bot_url", url)
                                .putString("session_token", sessionToken)
                                .putString("username", username)
                                .apply();

                    final String finalToken = sessionToken;
                    runOnUiThread(() -> openSlotChooser(url, finalToken));
                } else if (ok && sessionToken.isEmpty()) {
                    // Try to get cookies another way
                    runOnUiThread(() -> {
                        pbLoading.setVisibility(View.GONE);
                        btnLogin.setEnabled(true);
                        tvError.setText("❌ فشل استخراج الـ session — جرب تاني");
                        tvError.setVisibility(View.VISIBLE);
                    });
                } else {
                    String errMsg = "❌ خطأ في تسجيل الدخول";
                    if (responseText.contains("error")) {
                        try {
                            int start = responseText.indexOf("\"error\":\"") + 9;
                            int end = responseText.indexOf("\"", start);
                            if (start > 9 && end > start)
                                errMsg = "❌ " + responseText.substring(start, end);
                        } catch (Exception ignored) {}
                    }
                    final String finalErr = errMsg;
                    runOnUiThread(() -> {
                        pbLoading.setVisibility(View.GONE);
                        btnLogin.setEnabled(true);
                        tvError.setText(finalErr);
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
