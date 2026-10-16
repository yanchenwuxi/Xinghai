package com.zaka.xinghai.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.zaka.xinghai.R;
import com.zaka.xinghai.util.U;

/**
 * 内置浏览器：给解析/线路页用。
 * 弹窗广告与 intent:// 外跳全掐掉，避免点一下就跳到应用商店。
 */
public class WebActivity extends Activity {

    private WebView web;
    private ProgressBar progress;

    public static void go(Context c, String url, String title) {
        Intent i = new Intent(c, WebActivity.class);
        i.putExtra("url", url);
        i.putExtra("title", title);
        c.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        U.init(getResources());
        setContentView(R.layout.activity_web);
        web = (WebView) findViewById(R.id.web_view);
        progress = (ProgressBar) findViewById(R.id.web_progress);
        ((TextView) findViewById(R.id.web_title)).setText(
                U.empty(getIntent().getStringExtra("title")) ? "网页" : getIntent().getStringExtra("title"));
        findViewById(R.id.web_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (web.canGoBack()) web.goBack();
                else finish();
            }
        });

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setSupportZoom(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setUserAgentString(desktopUa());
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);
        CookieManager.getInstance().setAcceptCookie(true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                return handle(url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                return handle(req.getUrl().toString());
            }

            /** 广告弹窗 / 外跳一律拦在本页里 */
            private boolean handle(String url) {
                if (U.empty(url)) return true;
                if (url.startsWith("intent://") || url.startsWith("market://")
                        || url.startsWith("weixin://") || url.startsWith("alipays://")
                        || url.startsWith("tbopen://")) {
                    return true;
                }
                if (url.startsWith("http")) return false;
                return true;
            }

            @Override
            public void onPageStarted(WebView v, String url, Bitmap favicon) {
                progress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                progress.setVisibility(View.GONE);
                // 把页面里 window.open 的壳也摁掉
                v.evaluateJavascript(
                        "(function(){window.open=function(){return null;};"
                                + "document.querySelectorAll('a[target=_blank]').forEach(function(a){a.target='_self';});})();",
                        null);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView v, String url, String msg, JsResult r) {
                r.confirm();
                return true;
            }
        });

        String url = getIntent().getStringExtra("url");
        if (!U.empty(url)) web.loadUrl(url);
    }

    private String desktopUa() {
        return "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110 Mobile Safari/537.36";
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        try {
            if (web != null) {
                web.stopLoading();
                web.destroy();
            }
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }
}
