package com.yourdiplomicabot;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.io.*;
import java.net.*;
import org.json.JSONObject;

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

        new Handler().postDelayed(this::doLogin, 800);
    }

    private void doLogin() {
        String url = etUrl.getText().toString().trim().replaceAll("/$", "");
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
                URL loginUrl = new URL(url + "/login");
                HttpURLConnection conn = (HttpURLConnection) loginUrl.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("Content-Type", "application/json");

                String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes("UTF-8"));
                }

                int code = conn.getResponseCode();
                String responseBody = "";

                if (code == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();
                    responseBody = sb.toString();
                } else {
                    // Read error stream if available
                    try {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        reader.close();
                        responseBody = sb.toString();
                    } catch (Exception ignored) {}
                }

                final String finalResponse = responseBody;
                final int finalCode = code;

                runOnUiThread(() -> {
                    pbLoading.setVisibility(View.GONE);
                    btnLogin.setEnabled(true);

                    if (finalCode == 200 && !finalResponse.isEmpty()) {
                        try {
                            JSONObject json = new JSONObject(finalResponse);
                            String token = "";
                            if (json.has("token")) {
                                token = json.getString("token");
                            } else if (json.has("session_token")) {
                                token = json.getString("session_token");
                            } else if (json.has("access_token")) {
                                token = json.getString("access_token");
                            }

                            if (!token.isEmpty()) {
                                // Save token and url
                                SharedPreferences.Editor editor = getSharedPreferences("diplo", MODE_PRIVATE).edit();
                                editor.putString("session_token", token);
                                editor.putString("bot_url", url);
                                editor.apply();

                                openSlotChooser(url, token);
                            } else {
                                tvError.setText("❌ فشل تسجيل الدخول: " + finalResponse);
                                tvError.setVisibility(View.VISIBLE);
                            }
                        } catch (Exception e) {
                            // Not JSON or parsing error
                            tvError.setText("❌ خطأ في الرد من السيرفر: " + finalResponse);
                            tvError.setVisibility(View.VISIBLE);
                        }
                    } else {
                        String errorMsg = finalResponse.isEmpty() ? "خطأ في الاتصال (كود: " + finalCode + ")" : finalResponse;
                        tvError.setText("❌ " + errorMsg);
                        tvError.setVisibility(View.VISIBLE);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pbLoading.setVisibility(View.GONE);
                    btnLogin.setEnabled(true);
                    tvError.setText("❌ خطأ: " + e.getMessage());
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
