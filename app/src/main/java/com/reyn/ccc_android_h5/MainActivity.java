package com.reyn.ccc_android_h5;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {
    // ---------------------------------------------- 配置开始 ----------------------------------------------
    /**
     * 应用标识
     */
    private static final String Tag = "cccAndroidH5";
    /**
     * 横屏或竖屏
     */
    private static final Boolean Portrait = true;
    /**
     * 目标网址
     */
    private static final String TargetUrl = "http://192.168.1.172:7456/";
    /**
     * 注入脚本
     */
    private static final String[] JSInjections = {
            // 示例
            // "javascript:function override(){ document.getElementsByTagName('meta')['viewport'].content='width=1000px,initial-scale=0.5,minimum-scale=0.2;'}",
            // "javascript:override();"
    };

    /**
     * 资源扩展名对应的资源类型
     */
    private static final Map<String, String> ResExtension = new HashMap<>();

    /**
     * 包含这段字符串的资源网址将被拦截
     *
     * @important 这段字符串将被用作分隔符，此分隔符之后的资源名称将被用于查询APK内部资源
     */
    private static final String Interception = "/game-xxx/";

    /**
     * APK 内部目录
     *
     * @important 用于拦截资源时优先使用内部资源
     */
    private static final String NativeDirectory = "native/";
    /**
     * UA
     */
    private static final String UA = "Mozilla/5.0 (android; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/65.0.3325.109 Safari/537.36";
    /**
     * 调试模式
     */
    private static Boolean DebugMode = false;
    // ---------------------------------------------- 配置结束 ----------------------------------------------
    /**
     * 网页视图
     */
    private WebView _webView;

    /**
     * 是否调试包
     *
     * @param context 活动
     */
    public static void initialize(Context context) {
        try {
            ApplicationInfo info = context.getApplicationInfo();
            DebugMode = (info.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        } catch (Exception e) {
            DebugMode = false;
        }

        // 资源扩展名映射到 mime 类型【这里包含了大部分ccc会用到的资源类型，如有需要请开发者自由扩展】
        ResExtension.put(".bin", "application/octet-stream");
        ResExtension.put(".atlas", "text/plain");
        ResExtension.put(".fnt", "text/plain");
        ResExtension.put(".jpg", "image/jpg");
        ResExtension.put(".jpeg", "image/jpg");
        ResExtension.put(".js", "text/javascript");
        ResExtension.put(".json", "application/json");
        ResExtension.put(".mp3", "audio/mpeg");
        ResExtension.put(".png", "image/png");
        ResExtension.put(".txt", "text/plain");
    }

    /**
     * 输出日志
     *
     * @param msg 消息
     */
    public void dump(String msg) {
        if (DebugMode) {
            Log.d(Tag, msg);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initialize(this);

        dump("App Created");

        // 锁定屏幕方向
        setRequestedOrientation(Portrait ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        // 窗口参数【全屏|防止息屏】
        Window win = getWindow();
        win.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        WindowManager.LayoutParams wlp = win.getAttributes();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            wlp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        win.setAttributes(wlp);

        // 创建 webview
        setContentView(R.layout.activity_main);
        _webView = findViewById(R.id.activity_main_webview);
        _webView.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            view.setOnApplyWindowInsetsListener(null);
            return windowInsets;
        });

        // 设置 webview
        WebSettings settings = _webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString(UA);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setBlockNetworkImage(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        WebView.setWebContentsDebuggingEnabled(false);
        CookieManager.getInstance().setAcceptThirdPartyCookies(_webView, true);
        _webView.requestFocusFromTouch();
        _webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage cm) {
                dump("[webview] " + cm.message() + " -- from line: " + cm.lineNumber() + " of " + cm.sourceId());
                return super.onConsoleMessage(cm);
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                final String origin = request.getOrigin().toString();
                dump("[webview] permission requested: " + origin);
                runOnUiThread(() -> {
                    // 只允许同源请求权限
                    if (origin.equals(TargetUrl)) {
                        request.grant(request.getResources());
                    } else {
                        request.deny();
                    }
                });
            }
        });
        _webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                view.loadUrl(request.getUrl().toString());
                return super.shouldOverrideUrlLoading(view, request);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                for (String injection : JSInjections) {
                    view.loadUrl(injection);
                }
            }

            @SuppressLint("WebViewClientOnReceivedSslError")
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                if (handler != null) {
                    // 忽略安全整数
                    handler.proceed();
                }
            }

            @Nullable
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                // @important 资源拦截
                // 步骤1:判断拦截资源的条件，即判断url里的图片资源的文件名
                String url = request.getUrl().toString();
                if (url.contains(Interception)) {
                    // 查询内部文件
                    String[] filePaths = url.split(Interception);
                    String path = filePaths[filePaths.length - 1];
                    int suffixAt = path.lastIndexOf("?");
                    if (suffixAt > -1) {
                        path = NativeDirectory + path.substring(0, suffixAt);
                    } else {
                        path = NativeDirectory + path;
                    }
                    try {
                        InputStream input = getApplicationContext().getAssets().open(path);
                        String ext = path.substring(path.lastIndexOf(".")).toLowerCase();
                        if (ResExtension.containsKey(ext)) {
                            dump("[webview] 拦截资源,使用 APK 内部文件: " + path);
                            return new WebResourceResponse(ResExtension.get(ext), "utf-8", input);
                        }
                    } catch (IOException e) {
                        // dump("没有找到内部文件，继续走HTTP请求：" + url);
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }
        });
        _webView.loadUrl(TargetUrl);
    }

    @Override
    protected void onStart() {
        dump("App Started");
        super.onStart();
    }

    @Override
    public void onBackPressed() {
        if (_webView.canGoBack()) {
            // _webView.goBack();
            dump("[webview] 不允许返回");
        } else {
            super.onBackPressed();
        }
    }
}
