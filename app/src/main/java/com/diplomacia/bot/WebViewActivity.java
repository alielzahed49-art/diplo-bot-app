package com.yourdiplomicabot;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class WebViewActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private Button btnStart;
    private String botUrl;
    private int slot;
    private boolean tokenSaved = false;

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

        btnStart.setOnClickListener(v -> startControlledLogin());

        setupWebView();
        tvStatus.setText("اضغط الزرار عشان يفتح تسجيل Google جوا التطبيق");
    }

    private void startControlledLogin() {
        tvStatus.setText("جاري فتح صفحة التسجيل...");
        // هنفتح صفحة الموقع اللي بتعمل OAuth بطريقة مراقبة
        String loginUrl = "https://diplomacia-saas.onrender.com/auth/google/full/" + slot;
        webView.loadUrl(loginUrl);
    }

    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36");

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String url) {
                progressBar.setVisibility(View.GONE);
                if (url.contains("settings") || url.contains("token")) {
                    tvStatus.setText("✅ تم التسجيل والتوكن اتحفظ في الموقع!");
                    new Handler(Looper.getMainLooper()).postDelayed(() -> finish(), 2500);
                }
            }
        });
    }
}
