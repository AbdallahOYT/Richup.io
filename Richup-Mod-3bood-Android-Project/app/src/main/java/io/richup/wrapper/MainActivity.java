
package io.richup.wrapper;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.Gravity;
import android.view.WindowManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.net.Uri;
import android.content.Intent;

import androidx.webkit.WebViewAssetLoader;

public class MainActivity extends Activity {
    private WebView webView;
    private FrameLayout root;

    private boolean keepAwake = true;
    private boolean testingMode = false;
    private boolean autoMode = false;

    private TextView autoButton;

private void installAutoModeScript() {
    String script =
        "(function() {" +
        "  if (window.__richupAutoInstalled) return;" +
        "  window.__richupAutoInstalled = true;" +
        "  window.__richupAutoTimer = null;" +
        "  window.__richupAutoState = 'roll';" +
        "  window.__richupAutoLastAction = 0;" +

        "  window.richupAutoSetMode = function(enabled) {" +
        "    if (window.__richupAutoTimer) {" +
        "      clearInterval(window.__richupAutoTimer);" +
        "      window.__richupAutoTimer = null;" +
        "    }" +
        "    window.__richupAutoState = 'roll';" +
        "    if (!enabled) return;" +

        "    window.__richupAutoTimer = setInterval(function() {" +
        "      var buttons = Array.from(document.querySelectorAll('button'));" +
        "      var findButton = function(label) {" +
        "        return buttons.find(function(b) {" +
        "          return !b.disabled &&" +
        "            (b.innerText || b.textContent || '').trim() === label &&" +
        "            b.getClientRects().length > 0;" +
        "        });" +
        "      };" +
        "      var now = Date.now();" +

        "      if (window.__richupAutoState === 'roll') {" +
        "        var roll = findButton('Roll the dice');" +
        "        if (roll) {" +
        "          roll.click();" +
        "          window.__richupAutoState = 'end';" +
        "          window.__richupAutoLastAction = now;" +
        "        }" +
        "      } else if (" +
        "        window.__richupAutoState === 'end' &&" +
        "        now - window.__richupAutoLastAction >= 2200" +
        "      ) {" +
        "        var end = findButton('End turn');" +
        "        if (end) {" +
        "          end.click();" +
        "          window.__richupAutoState = 'roll';" +
        "          window.__richupAutoLastAction = now;" +
        "        }" +
        "      }" +
        "    }, 500);" +
        "  };" +
        "})();";

    if (webView != null) {
        webView.evaluateJavascript(script, value -> {
            if (webView != null) {
                webView.evaluateJavascript(
                    "if (window.richupAutoSetMode) " +
                    "window.richupAutoSetMode(" + autoMode + ");",
                    null
                );
            }
        });
    }
}

private void setAutoModeInPage() {
    if (webView != null) {
        webView.evaluateJavascript(
            "if (window.richupAutoSetMode) " +
            "window.richupAutoSetMode(" + autoMode + ");",
            null
        );
    }
}

    private static final String HOME_URL = "https://richup.io/";
    private static final String TEST_URL =
            "https://appassets.androidplatform.net/assets/testing.html";

    private WebViewAssetLoader assetLoader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        );

        assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler(
                        "/assets/",
                        new WebViewAssetLoader.AssetsPathHandler(this)
                )
                .build();

        applyImmersiveMode();

        root = new FrameLayout(this);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(15, 18, 20));
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);

        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(dp(5), dp(5), dp(5), dp(5));

        GradientDrawable panel = new GradientDrawable();
        panel.setColor(0xD91B2422);
        panel.setCornerRadius(dp(24));
        panel.setStroke(dp(1), 0xFF16D99A);
        controls.setBackground(panel);

        TextView home = makeButton("⌂ Home");
        TextView reload = makeButton("↻ Reload");
        TextView awake = makeButton("☾ Awake: ON");
        TextView testing = makeButton("⚙ Testing");
        autoButton = makeButton("▶ Auto: OFF");

        home.setOnClickListener(v -> {
            testingMode = false;
            stopAutoMode();
            webView.loadUrl(HOME_URL);
        });

        reload.setOnClickListener(v -> {
            stopAutoMode();
            webView.reload();
        });

        awake.setOnClickListener(v -> {
            keepAwake = !keepAwake;

            if (keepAwake) {
                getWindow().addFlags(
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                );
                awake.setText("☾ Awake: ON");
            } else {
                getWindow().clearFlags(
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                );
                awake.setText("☾ Awake: OFF");
            }
        });

        testing.setOnClickListener(v -> {
            testingMode = true;
            stopAutoMode();
            webView.loadUrl(TEST_URL);
        });

  
autoButton.setOnClickListener(v -> {
    autoMode = !autoMode;

    if (autoMode) {
        autoButton.setText("▶ Auto: ON");
        installAutoModeScript();

        Toast.makeText(
            MainActivity.this,
            "Auto Mode enabled",
            Toast.LENGTH_SHORT
        ).show();
    } else {
        autoButton.setText("▶ Auto: OFF");
        setAutoModeInPage();

        Toast.makeText(
            MainActivity.this,
            "Auto Mode stopped",
            Toast.LENGTH_SHORT
        ).show();
    }
});


        controls.addView(home);
        controls.addView(reload);
        controls.addView(awake);
        controls.addView(testing);
        controls.addView(autoButton);

        FrameLayout.LayoutParams controlsParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
                );

        controlsParams.bottomMargin = dp(12);
        root.addView(controls, controlsParams);

        setContentView(root);

        if (keepAwake) {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            );
        }

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(
                WebSettings.MIXED_CONTENT_NEVER_ALLOW
        );
        settings.setDefaultTextEncodingName("UTF-8");
        settings.setJavaScriptCanOpenWindowsAutomatically(false);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        webView.setWebChromeClient(new WebChromeClient());

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(
                    WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view, WebResourceRequest request) {
                return routeUrl(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view, String url) {
                return routeUrl(Uri.parse(url));
            }
@Override
public void onPageFinished(WebView view, String url) {
    super.onPageFinished(view, url);

    if (url != null && url.startsWith("https://richup.io")) {
        installAutoModeScript();
    }
}
            private boolean routeUrl(Uri uri) {
                String scheme = uri.getScheme();
                String host = uri.getHost();

                if ("https".equalsIgnoreCase(scheme)
                        && host != null
                        && (host.equals("richup.io")
                        || host.endsWith(".richup.io"))) {
                    testingMode = false;
                    return false;
                }

                if ("https".equalsIgnoreCase(scheme)
                        && "appassets.androidplatform.net".equals(host)) {
                    return false;
                }

                if ("https".equalsIgnoreCase(scheme)
                        || "http".equalsIgnoreCase(scheme)) {
                    try {
                        startActivity(new Intent(
                                Intent.ACTION_VIEW, uri
                        ));
                    } catch (Exception ignored) {
                        // No compatible browser is available.
                    }
                }

                return true;
            }
        });

        if (savedInstanceState == null) {
            webView.loadUrl(HOME_URL);
        } else {
            testingMode = savedInstanceState.getBoolean(
                    "testingMode", false
            );

            if (webView.restoreState(savedInstanceState) == null) {
                webView.loadUrl(testingMode ? TEST_URL : HOME_URL);
            }
        }
    }


private void stopAutoMode() {
    autoMode = false;

    if (autoButton != null) {
        autoButton.setText("▶ Auto: OFF");
    }

    setAutoModeInPage();
}

    private TextView makeButton(String label) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(11);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private int dp(int value) {
        return (int) (
                value * getResources().getDisplayMetrics().density + 0.5f
        );
    }

    private void applyImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (hasFocus) {
            applyImmersiveMode();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (webView != null) {
            webView.onResume();
            webView.resumeTimers();
        }
    }

    @Override
    protected void onPause() {
        stopAutoMode();

        if (webView != null) {
            webView.onPause();
            webView.pauseTimers();
        }

        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else if (testingMode) {
            testingMode = false;
            stopAutoMode();
            webView.loadUrl(HOME_URL);
        } else {
            stopAutoMode();
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("testingMode", testingMode);

        if (webView != null) {
            webView.saveState(outState);
        }

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.clearHistory();
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }
}
