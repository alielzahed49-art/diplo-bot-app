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
    private Button btnStart, btnExtract, btnForceScan;
    private String botUrl;
    private int slot;
    private boolean tokenSaved = false;

    private static final String INJECT_JS = 
        "(function(){" +
        "  if (window.__tokenExtractorRunning) return;" +
        "  window.__tokenExtractorRunning = true;" +
        "  function findTokens() {" +
        "    let candidates = [];" +
        "    // Check localStorage" +
        "    for (let i = 0; i < localStorage.length; i++) {" +
        "      let key = localStorage.key(i);" +
        "      let val = localStorage.getItem(key);" +
        "      if (val && val.length > 30) {" +
        "        let k = key.toLowerCase();" +
        "        if (val.startsWith('eyJ') || val.includes('Bearer') || " +
        "            k.includes('token') || k.includes('auth') || k.includes('jwt') || k.includes('session') || " +
        "            val.length > 80) {" +
        "          candidates.push({key: key, value: val});" +
        "        }" +
        "      }" +
        "    }" +
        "    // Check sessionStorage" +
        "    for (let i = 0; i < sessionStorage.length; i++) {" +
        "      let key = sessionStorage.key(i);" +
        "      let val = sessionStorage.getItem(key);" +
        "      if (val && val.length > 30) {" +
        "        let k = key.toLowerCase();" +
        "        if (val.startsWith('eyJ') || val.includes('Bearer') || " +
        "            k.includes('token') || k.includes('auth') || k.includes('jwt')) {" +
        "          candidates.push({key: key, value: val});" +
        "        }" +
        "      }" +
        "    }" +
        "    if (candidates.length > 0) {" +
        "      // Pick the longest/most promising" +
        "      candidates.sort((a, b) => b.value.length - a.value.length);" +
        "      let best = candidates[0];" +
        "      Android.onData(best.value + '||KEY:' + best.key);" +
        "    }" +
        "  }" +
        "  findTokens();" +
        "  setInterval(findTokens, 2500);" +
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
        btnForceScan = findViewById(R.id.btn_force_scan);

        btnStart.setOnClickListener(v -> startGame());
        btnExtract.setOnClickListener(v -> extractTokenManually());
        btnForceScan.setOnClickListener(v -> forceScan());

        setupWebView();
        tvStatus.setText("اضغط 'ابدأ' عشان يفتح اللعبة\nالتوكن هيتسحب تلقائي لما تسجل دخول");
    }

    private void startGame() {
        tvStatus.setText("جاري فتح اللعبة...");
        webView.loadUrl("https://diplomacia.com.tr");
    }

    private void extractTokenManually() {
        tvStatus.setText("جاري البحث عن التوكن...");
        webView.evaluateJavascript(INJECT_JS, null);
    }

    private void forceScan() {
        tvStatus.setText("جاري الفحص القسري...");
        webView.evaluateJavascript(INJECT_JS, null);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!tokenSaved) tvStatus.setText("لم يتم العثور على توكن بعد. جرب تسجيل الدخول في اللعبة");
        }, 3000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (webView != null) webView.evaluateJavascript(INJECT_JS, null);
        }, 2000);
    }

    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 13) AppleKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36");

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
                if (url.contains("diplomacia") || url.contains("game") || url.contains("play")) {
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
            if (token != null && token.length() > 20) {
                tokenSaved = true;
                final String finalToken = token;
                runOnUiThread(() -> {
                    tvStatus.setText("✅ تم سحب التوكن! جاري الإرسال للبوت...\n" + finalToken.substring(0, Math.min(30, finalToken.length())) + "...");
                });
                sendTokenToBot(finalToken);
            }
        }
    }

    private String extractToken(String text) {
        if (text == null) return null;
        text = text.trim();

        // If it contains ||KEY: it came from improved JS
        if (text.contains("||KEY:")) {
            String[] parts = text.split("\\|\|KEY:");
            if (parts.length > 0) {
                String val = parts[0].trim();
                if (val.startsWith("eyJ") && val.length() > 50) return val;
                if (val.length() > 40) return val;
            }
        }

        if (text.startsWith("eyJ") && text.length() > 50) return text;
        if (text.contains("Bearer ")) {
            int idx = text.indexOf("Bearer ") + 7;
            String t = text.substring(idx).trim().split(" ")[0];
            if (t.length() > 20) return t;
        }
        // Try to find JWT-like in the string
        if (text.contains("eyJ")) {
            int start = text.indexOf("eyJ");
            int end = text.indexOf(".", start + 10);
            if (end > start) {
                String possible = text.substring(start, Math.min(end + 100, text.length()));
                if (possible.length() > 50) return possible.split("\n")[0].trim();
            }
        }
        if (text.length() > 60 && (text.contains("token") || text.contains("auth"))) {
            return text;
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
                String sessionTok = getIntent().getStringExtra("session_token");
                if (sessionTok != null && !sessionTok.isEmpty()) {
                    conn.setRequestProperty("Cookie", "session=" + sessionTok);
                }

                String body = "{\"token\":\"" + token.replace("\"", "\\\"") + "\"}";
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes("UTF-8"));
                }

                int code = conn.getResponseCode();
                runOnUiThread(() -> {
                    if (code == 200 || code == 201) {
                        tvStatus.setText("✅ تم الحفظ بنجاح في البوت!\nالتوكن: " + token.substring(0, Math.min(25, token.length())) + "...");
                        new Handler().postDelayed(() -> finish(), 2500);
                    } else {
                        tvStatus.setText("❌ فشل الإرسال (كود: " + code + ")");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> tvStatus.setText("❌ خطأ في الإرسال: " + e.getMessage()));
            }
        }).start();
    }
}
