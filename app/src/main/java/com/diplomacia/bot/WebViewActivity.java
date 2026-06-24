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

    private static final String INJECT_JS = 
        "(function(){" +
        "if(window.__d)return;window.__d=true;" +
        "var keys = Object.keys(localStorage); for(var i=0;i<keys.length;i++){var k=keys[i];var v=localStorage.getItem(k);if(v&&v.length>30)Android.onData(v);}" +
        "var token = localStorage.getItem('token') || localStorage.getItem('accessToken') || localStorage.getItem('jwt');" +
        "if(token) Android.onData(token); else Android.onData('NO_TOKEN_FOUND');" +
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

    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36");

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new JsBridge(), "Android");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String url) {
                progressBar.setVisibility(View.GONE);
                if (url.contains("diplomacia")) {
                    webView.evaluateJavascript(INJECT_JS, null);
                }
            }
        });
    }

    private class JsBridge {
        @JavascriptInterface
        public void onData(String text) {
            if (tokenSaved) return;
            runOnUiThread(() -> tvStatus.setText("Received: " + text.substring(0, Math.min(50, text.length())) + "..."));
            if (text.contains("NO_TOKEN_FOUND")) return;
            String token = extractToken(text);
            if (token != null) {
                tokenSaved = true;
                sendTokenToBot(token);
            }
        }
    }

    private String extractToken(String text) {
        if (text.trim().startsWith("eyJ") && text.length() > 50) return text.trim();
        return null;
    }

    private void sendTokenToBot(String token) {
        runOnUiThread(() -> tvStatus.setText("✅ Sending to site..."));
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
                runOnUiThread(() -> tvStatus.setText(code == 200 ? "✅ Token saved on site!" : "❌ Failed (" + code + ")"));
            } catch (Exception e) {
                runOnUiThread(() -> tvStatus.setText("❌ Error sending: " + e.getMessage()));
            }
        }).start();
    }
}
