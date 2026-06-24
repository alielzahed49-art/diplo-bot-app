package com.yourdiplomicabot;

import android.net.Uri;
import android.webkit.CookieManager;
import android.annotation.SuppressLint;
import android.content.Intent;
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
    private Button btnGoogleLogin, btnExtract, btnCheck;
    private String botUrl;
    private int slot;
    private boolean tokenSaved = false;

    private static final String INJECT_JS = 
        "(function(){" +
        "if(window.__d)return;window.__d=true;" +
        "var keys = Object.keys(localStorage);" +
        "for(var i=0;i<keys.length;i++){" +
        "  var k=keys[i]; var v=localStorage.getItem(k);" +
        "  if(v && v.length>40 && (v.startsWith('eyJ') || v.includes('Bearer') || k.toLowerCase().includes('token'))){" +
        "    Android.onData(v);" +
        "  }" +
        "}" +
        "if(!window.__found) Android.onData('NO_TOKEN');" +
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
        btnGoogleLogin = findViewById(R.id.btn_google_login);
        btnExtract = findViewById(R.id.btn_extract);
        btnCheck = findViewById(R.id.btn_check);

        btnGoogleLogin.setOnClickListener(v -> startGoogleLogin());
        btnExtract.setOnClickListener(v -> extractManually());
        btnCheck.setOnClickListener(v -> checkToken());

        setupWebView();
        tvStatus.setText("اضغط 'تسجيل بجوجل' (الطريقة الشغالة)");
    }

    private void startGoogleLogin() {
        String url = "https://diplomacia-saas.onrender.com/auth/google/full/" + slot;
        CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().build();
        customTabsIntent.launchUrl(this, Uri.parse(url));
        tvStatus.setText("جاري فتح Google login... بعد الـ login ارجع واضغط 'تحقق'");
    }

    private void extractManually() {
        tvStatus.setText("جاري استخراج التوكن...");
        webView.evaluateJavascript(INJECT_JS, null);
    }

    private void checkToken() {
        tvStatus.setText("✅ لو التوكن اتحفظ في الموقع → افتح الـ Settings في الموقع");
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
            }
        });
    }

    private class JsBridge {
        @JavascriptInterface
        public void onData(String text) {
            if (tokenSaved) return;
            if (text.contains("NO_TOKEN")) {
                runOnUiThread(() -> tvStatus.setText("❌ مفيش توكن (جرب الزرار تاني)"));
                return;
            }
            String token = extractToken(text);
            if (token != null) {
                tokenSaved = true;
                runOnUiThread(() -> tvStatus.setText("✅ التوكن: " + token.substring(0, 35) + "..."));
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
                runOnUiThread(() -> tvStatus.setText(code == 200 ? "✅ تم الحفظ في الموقع!" : "❌ فشل"));
            } catch (Exception e) {
                runOnUiThread(() -> tvStatus.setText("❌ خطأ"));
            }
        }).start();
    }
}
