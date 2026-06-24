package com.yourdiplomicabot;

import android.net.Uri;
import android.webkit.CookieManager;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.*;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;
import java.net.*;
import java.io.*;

public class WebViewActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private String botUrl, sessionToken;
    private int slot;
    private boolean tokenSaved = false;
    private boolean openedCustomTab = false;

    private static final String INJECT_JS = 
        "(function(){" +
        "if(window.__d)return;window.__d=true;" +
        "var token = localStorage.getItem('token') || localStorage.getItem('accessToken') || localStorage.getItem('jwt');" +
        "if(token) Android.onData(token);" +
        "})();";

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webview);

        botUrl = getIntent().getStringExtra("bot_url");
        sessionToken = getIntent().getStringExtra("session_token");
        slot = getIntent().getIntExtra("slot", 1);

        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progress);
        tvStatus = findViewById(R.id.tv_status);

        setupWebView();
        webView.loadUrl("https://diplomacia.com.tr");
    }

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36");

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new JsBridge(), "Android");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView v, String url, Bitmap fav) {
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                progressBar.setVisibility(View.GONE);
                if (url.contains("diplomacia")) {
                    webView.evaluateJavascript(INJECT_JS, null);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                String url = req.getUrl().toString();
                
                if (url.startsWith("https://accounts.google.com") || url.startsWith("https://oauth2.googleapis.com")) {
                    openedCustomTab = true;
                    CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().build();
                    customTabsIntent.launchUrl(WebViewActivity.this, req.getUrl());
                    return true;
                }
                
                if (url.startsWith("https://diplomacia.com.tr")) {
                    return false;
                }
                
                startActivity(new Intent(Intent.ACTION_VIEW, req.getUrl()));
                return true;
            }
        });
    }

    private class JsBridge {
        @JavascriptInterface
        public void onData(String text) {
            if (tokenSaved || text == null || text.isEmpty()) return;
            String token = extractToken(text);
            if (token != null) {
                tokenSaved = true;
                sendTokenToBot(token);
            }
        }
    }

    private String extractToken(String text) {
        if (text.trim().startsWith("eyJ") && text.length() > 50) return text.trim();
        // Add more if needed
        return null;
    }

    private void sendTokenToBot(String token) {
        runOnUiThread(() -> tvStatus.setText("✅ تم استخراج التوكن!"));
        new Thread(() -> {
            try {
                URL url = new URL(botUrl + "/api/config/" + slot);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Cookie", "session=" + sessionToken);

                String body = "{\"token\":\"" + token + "\"}";
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes("UTF-8"));
                }

                int code = conn.getResponseCode();
                runOnUiThread(() -> tvStatus.setText(code == 200 ? "✅ تم الحفظ!" : "❌ فشل"));
            } catch (Exception e) {
                runOnUiThread(() -> tvStatus.setText("❌ خطأ"));
            }
        }).start();
    }
}
