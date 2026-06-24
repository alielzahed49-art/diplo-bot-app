package com.yourdiplomicabot;

import android.webkit.CookieManager;
import java.net.URL;
import java.net.HttpURLConnection;
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
import org.json.JSONObject;
import java.io.*;

public class WebViewActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private String botUrl, sessionToken;
    private int slot;
    private boolean tokenSaved = false;
    private boolean openedCustomTab = false;

    // JS يحقن في diplomacia بس (مش في Google)
    private static final String INJECT_JS =
        "(function(){" +
        "if(window.__d)return;window.__d=true;" +
        "var oF=window.fetch;" +
        "window.fetch=async function(){" +
        "  var r=await oF.apply(this,arguments);" +
        "  try{var c=r.clone();c.text().then(function(t){if(t&&t.length>30)Android.onData(t);});}catch(e){}" +
        "  return r;};" +
        "var oS=XMLHttpRequest.prototype.send;" +
        "XMLHttpRequest.prototype.send=function(){" +
        "  this.addEventListener('load',function(){" +
        "    try{if(this.responseText&&this.responseText.length>30)Android.onData(this.responseText);}catch(e){}" +
        "  });return oS.apply(this,arguments);};" +
        "try{" +
        "  Object.keys(localStorage).forEach(function(k){" +
        "    var v=localStorage.getItem(k);" +
        "    if(v&&v.length>30)Android.onData(JSON.stringify({k:k,v:v}));" +
        "  });" +
        "}catch(e){}" +
        "})();";

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webview);

        botUrl       = getIntent().getStringExtra("bot_url");
        sessionToken = getIntent().getStringExtra("session_token");
        slot         = getIntent().getIntExtra("slot", 1);

        webView     = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progress);
        tvStatus    = findViewById(R.id.tv_status);

        TextView tvTitle = findViewById(R.id.tv_title);
        tvTitle.setText("استخراج توكن — حساب " + slot);

        tvStatus.setText("سجل دخولك بـ Google 👇");

        setupWebView();
        webView.loadUrl("https://diplomacia.com.tr");
    }

    @Override
    protected void onResume() {
        super.onResume();
        // لما اليوزر يرجع بعد Custom Tab، نجيب الكوكيز
        if (openedCustomTab) {
            openedCustomTab = false;
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                // نحقن JS في الصفحة الحالية عشان نمسك التوكن
                webView.evaluateJavascript(INJECT_JS, null);
                // ونجيب الكوكيز كمان
                checkCookiesForToken();
            }, 1500);
        }
    }

    private void checkCookiesForToken() {
        String cookies = CookieManager.getInstance().getCookie("https://diplomacia.com.tr");
        if (cookies != null) {
            // دور على JWT في الكوكيز
            for (String part : cookies.split(";")) {
                String val = part.trim();
                int eqIdx = val.indexOf('=');
                if (eqIdx > 0) val = val.substring(eqIdx + 1).trim();
                if (val.startsWith("eyJ") && val.length() > 50 && val.contains(".")) {
                    if (!tokenSaved) {
                        tokenSaved = true;
                        sendTokenToBot(val);
                        return;
                    }
                }
            }
        }
        // لو مش في الكوكيز، إعد تحميل الصفحة عشان نمسك التوكن من الـ JS
        webView.reload();
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
                // نحقن JS بس في diplomacia مش في Google
                if (url.contains("diplomacia.com.tr")) {
                    webView.evaluateJavascript(INJECT_JS, null);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                String url = req.getUrl().toString();
                
                // لو Google OAuth — افتح في Chrome Custom Tab
                if (url.startsWith("https://accounts.google.com") ||
                    url.startsWith("https://oauth2.googleapis.com")) {
                    openedCustomTab = true;
                    tvStatus.setText("سجل دخولك بـ Google في Chrome 👇");
                    CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().build();
                    customTabsIntent.launchUrl(WebViewActivity.this, req.getUrl());
                    return true;
                }
                
                // diplomacia يفضل في WebView
                if (url.startsWith("https://diplomacia.com.tr")) {
                    return false;
                }
                
                // أي حاجة تانية افتحها في المتصفح الخارجي
                startActivity(new Intent(Intent.ACTION_VIEW, req.getUrl()));
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int p) {
                progressBar.setProgress(p);
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
                new Handler(Looper.getMainLooper()).post(() -> sendTokenToBot(token));
            }
        }
    }

    private String extractToken(String text) {
        try {
            if (text.trim().startsWith("{") || text.trim().startsWith("[")) {
                int idx = text.indexOf("eyJ");
                if (idx >= 0) {
                    int end = text.indexOf("\"", idx);
                    if (end < 0) end = Math.min(idx + 600, text.length());
                    String t = text.substring(idx, end).trim();
                    if (t.length() > 50 && t.contains(".")) return t;
                }
                String[] fields = {"\"token\":\"","\"accessToken\":\"","\"access_token\":\"","\"jwt\":\""};
                for (String f : fields) {
                    int i = text.indexOf(f);
                    if (i >= 0) {
                        int start = i + f.length();
                        int end = text.indexOf("\"", start);
                        if (end > start) {
                            String t = text.substring(start, end);
                            if (t.length() > 50) return t;
                        }
                    }
                }
                try {
                    JSONObject obj = new JSONObject(text);
                    String v = obj.optString("v", "");
                    if (v.startsWith("eyJ") && v.length() > 50) return v;
                    String k = obj.optString("k", "");
                    if (k.toLowerCase().contains("token") || k.toLowerCase().contains("auth")) {
                        if (v.length() > 50) return v;
                    }
                } catch (Exception ignored) {}
            } else if (text.trim().startsWith("eyJ") && text.length() > 50) {
                return text.trim().replaceAll("\"", "").trim();
            }
        } catch (Exception e) { /* ignore */ }
        return null;
    }

    private void sendTokenToBot(String token) {
        runOnUiThread(() -> tvStatus.setText("✅ تم العثور على التوكن! جاري الحفظ..."));

        new Thread(() -> {
            try {
                URL url = new URL(botUrl + "/api/config/" + slot);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Cookie", "session=" + sessionToken);

                String body = "{\"token\":\"" + token + "\"}";
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes("UTF-8"));
                }

                int code = conn.getResponseCode();
                boolean ok = (code == 200 || code == 201);

                runOnUiThread(() -> {
                    if (ok) {
                        showSuccessDialog(token);
                    } else {
                        tokenSaved = false;
                        tvStatus.setText("❌ فشل الحفظ (كود: " + code + ") — حاول تاني");
                    }
                });
            } catch (Exception e) {
                tokenSaved = false;
                runOnUiThread(() -> tvStatus.setText("❌ خطأ: " + e.getMessage()));
            }
        }).start();
    }

    private void showSuccessDialog(String token) {
        tvStatus.setText("✅ تم حفظ التوكن في حساب " + slot + " بنجاح!");
        new AlertDialog.Builder(this)
            .setTitle("✅ تم!")
            .setMessage("تم استخراج وحفظ التوكن في حساب " + slot + " بنجاح!\n\nدلوقتي تقدر تشغّل البوت من الموقع.")
            .setPositiveButton("🌐 افتح البوت", (d, w) -> {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(botUrl)));
                finish();
            })
            .setNegativeButton("🔙 رجوع", (d, w) -> {
                startActivity(new Intent(this, SlotActivity.class)
                    .putExtra("bot_url", botUrl)
                    .putExtra("session_token", sessionToken));
                finish();
            })
            .setCancelable(false)
            .show();
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
