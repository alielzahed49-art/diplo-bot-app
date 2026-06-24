package com.yourdiplomicabot;

import android.net.Uri;
import android.webkit.CookieManager;
import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;
import java.net.*;
import java.io.*;

public class WebViewActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private Button btnStart, btnExtract;
    private String botUrl;
    private int slot;
    private boolean tokenSaved = false;

    private static final String INJECT_JS = 
        "(function(){" +
        "if(window.__d)return;window.__d=true;" +
        "var keys = Object.keys(localStorage);" +
        "for(var i=0;i<keys.length;i++){" +
        "  var k=keys[i]; var v=localStorage.getItem(k);" +
        "  if(v && v.length>40 && (v.startsWith('eyJ') || v.includes('Bearer') || k.toLowerCase().includes('token'))){ Android.onData(v); }" +
        "}" +
        "})();";

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webview);

        botUrl = getIntent().getStringExtra("bot_url");
        slot = getIntent().getIntExtra("slot", 1);

        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progress);
        tvStatus = findViewById(R.id.tv_status);
        btnStart = findViewById(R.id.btn_start);
        btnExtract = findViewById(R.id.btn_extract);

        btnStart.setOnClickListener(v -> startGame());
        btnExtract.setOnClickListener(v -> extractTokenManually());

        setupWebView();
        tvStatus.setText("اضغط 'ابدأ' عشان يفتح اللعبة");
    }

    private void startGame() {
        tvStatus.setText("جاري فتح اللعبة...");
        webView.loadUrl("https://diplomacia.com.tr");
    }

    private void extractTokenManually() {
        tvStatus.setText("جاري استخراج التوكن...");
        webView.evaluateJavascript(INJECT_JS, null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (webView != null) webView.evaluateJavascript(INJECT_JS, null);
        }, 5000);
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
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                String url = req.getUrl().toString();
                if (url.startsWith("https://accounts.google.com") || url.startsWith("https://oauth2.googleapis.com")) {
                    CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().build();
                    customTabsIntent.launchUrl(WebViewActivity.this, req.getUrl());
                    return true;
                }
                return false;
            }

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
            String token = extractToken(text);
            if (token != null) {
                tokenSaved = true;
                runOnUiThread(() -> tvStatus.setText("✅ التوكن اتاخد! جاري الإرسال..."));
                sendTokenToBot(token);
            }
        }
    }

    private String extractToken(String text) {
        if (text.trim().startsWith("eyJ") && text.length() > 50) return text.trim();
        if (text.contains("Bearer ")) {
            int idx = text.indexOf("Bearer ") + 7;
            return text.substring(idx).trim().split(" ")[0];
        }
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
                conn.setRequestProperty("Cookie", "session=" + getIntent().getStringExtra("session_token"));

                String body = "{\"token\":\"" + token + "\"}";
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes("UTF-8"));
                }

                int code = conn.getResponseCode();
                runOnUiThread(() -> {
                    if (code == 200) {
                        tvStatus.setText("✅ تم الحفظ! التطبيق هيقفل.");
                        new Handler().postDelayed(() -> finish(), 2000);
                    } else {
                        tvStatus.setText("❌ فشل الإرسال");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> tvStatus.setText("❌ خطأ"));
            }
        }).start();
    }
}
