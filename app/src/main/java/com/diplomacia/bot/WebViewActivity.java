package com.diplomacia.bot;

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
import org.json.JSONObject;
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
        // Intercept fetch
        "var oF=window.fetch;" +
        "window.fetch=async function(){" +
        "  var r=await oF.apply(this,arguments);" +
        "  try{var c=r.clone();c.text().then(function(t){if(t&&t.length>30)Android.onData(t);});}catch(e){}" +
        "  return r;};" +
        // Intercept XHR
        "var oS=XMLHttpRequest.prototype.send;" +
        "XMLHttpRequest.prototype.send=function(){" +
        "  this.addEventListener('load',function(){" +
        "    try{if(this.responseText&&this.responseText.length>30)Android.onData(this.responseText);}catch(e){}" +
        "  });return oS.apply(this,arguments);};" +
        // Check localStorage
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
                webView.evaluateJavascript(INJECT_JS, null);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                String url = req.getUrl().toString();
                if (url.startsWith("https://diplomacia.com.tr") ||
                    url.startsWith("https://accounts.google.com") ||
                    url.startsWith("https://oauth2.googleapis.com")) {
                    return false;
                }
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
                // Find JWT
                int idx = text.indexOf("eyJ");
                if (idx >= 0) {
                    int end = text.indexOf("\"", idx);
                    if (end < 0) end = Math.min(idx + 600, text.length());
                    String t = text.substring(idx, end).trim();
                    if (t.length() > 50 && t.contains(".")) return t;
                }
                // Check common fields
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
                // Check localStorage value
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
        tvStatus.setText("✅ تم العثور على التوكن! جاري الحفظ...");

        new Thread(() -> {
            try {
                // Send token to bot API
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
