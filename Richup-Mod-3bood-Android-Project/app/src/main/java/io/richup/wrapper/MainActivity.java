package io.richup.wrapper;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.view.Gravity;
import android.view.WindowManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.CheckBox;
import android.widget.Toast;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.net.Uri;
import android.content.Intent;
import android.text.InputType;

import androidx.webkit.WebViewAssetLoader;

public class MainActivity extends Activity {
    private static final int PURPLE = Color.rgb(139, 92, 246);
    private static final int PURPLE_DARK = Color.rgb(109, 40, 217);
    private static final int PURPLE_PALE = Color.rgb(196, 181, 253);
    private static final int DARK = Color.rgb(23, 21, 31);
    private static final String HOME_URL = "https://richup.io/";
    private static final String TEST_URL = "https://appassets.androidplatform.net/assets/testing.html";

    private WebView webView;
    private FrameLayout root;
    private WebViewAssetLoader assetLoader;
    private boolean keepAwake = true;
    private boolean testingMode = false;
    private boolean autoMode = false;
    private boolean autoBuy = false;
    private TextView autoButton;

    private void installAutoModeScript() {
        String script =
            "(function(){" +
            "if(window.__richupAutoInstalled)return;window.__richupAutoInstalled=true;" +
            "window.__richupAutoTimer=null;window.__richupAutoLastAction=0;" +
            "window.__richupAutoSetBuy=window.__richupAutoSetBuy||function(v){window.__richupAutoBuy=!!v;};" +
            "window.richupAutoSetMode=function(enabled,buy){" +
            "if(window.__richupAutoTimer){clearInterval(window.__richupAutoTimer);window.__richupAutoTimer=null;}" +
            "window.__richupAutoBuy=!!buy;if(!enabled)return;" +
            "window.__richupAutoTimer=setInterval(function(){" +
            "var now=Date.now();if(now-window.__richupAutoLastAction<1200)return;" +
            "var buttons=Array.from(document.querySelectorAll('button,[role=button]'));" +
            "var label=function(b){return (b.innerText||b.textContent||b.getAttribute('aria-label')||'').trim().replace(/\\s+/g,' ');};" +
            "var visible=function(b){return !b.disabled&&b.getAttribute('aria-disabled')!=='true'&&b.getClientRects().length>0&&getComputedStyle(b).visibility!=='hidden';};" +
            "var find=function(re){return buttons.find(function(b){return visible(b)&&re.test(label(b));});};" +
            "var click=function(b){if(!b)return false;b.click();window.__richupAutoLastAction=Date.now();return true;};" +
            // Re-roll takes priority so consecutive doubles / extra rolls are handled.
            "var again=find(/roll\\s*again|roll again/i);if(again){click(again);return;}" +
            // Buy only when the game exposes an enabled purchase button; disabled buttons are skipped.
            "var buy=find(/^buy(?:\\s|$)|buy property|purchase property/i);" +
            "if(buy&&window.__richupAutoBuy){click(buy);return;}" +
            "var roll=find(/^roll the dice$|^roll dice$|^roll$/i);if(roll){click(roll);return;}" +
            "if(now-window.__richupAutoLastAction<1800)return;" +
            "var end=find(/^end turn$/i);if(end){click(end);return;}" +
            // Auction is a fallback only when End turn is not present, matching the requested game variant.
            "var auction=find(/^auction$/i);if(auction){click(auction);return;}" +
            "},500);};" +
            "})();";

        if (webView != null) {
            webView.evaluateJavascript(script, value -> {
                if (webView != null) {
                    webView.evaluateJavascript(
                        "if(window.richupAutoSetMode)window.richupAutoSetMode(" + autoMode + "," + autoBuy + ");",
                        null
                    );
                }
            });
        }
    }

    private void setAutoModeInPage() {
        if (webView != null) {
            webView.evaluateJavascript(
                "if(window.richupAutoSetMode)window.richupAutoSetMode(" + autoMode + "," + autoBuy + ");",
                null
            );
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);

        assetLoader = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
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
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(dp(5), dp(5), dp(5), dp(5));
        GradientDrawable panel = new GradientDrawable();
        panel.setColor(0xE617151F);
        panel.setCornerRadius(dp(22));
        panel.setStroke(dp(1), PURPLE);
        controls.setBackground(panel);

        TextView home = makeButton("⌂ Home");
        TextView reload = makeButton("↻ Reload");
        TextView awake = makeButton("☾ Awake: ON");
        TextView testing = makeButton("⚙ Testing");
        autoButton = makeButton("▶ Auto Play");
        TextView joinButton = makeButton("↗ Join Room");

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
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                awake.setText("☾ Awake: ON");
            } else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                awake.setText("☾ Awake: OFF");
            }
        });
        testing.setOnClickListener(v -> {
            testingMode = true;
            stopAutoMode();
            webView.loadUrl(TEST_URL);
        });
        autoButton.setOnClickListener(v -> showAutoSettings());
        joinButton.setOnClickListener(v -> showJoinRoomDialog());

        controls.addView(home);
        controls.addView(reload);
        controls.addView(awake);
        controls.addView(testing);
        controls.addView(autoButton);
        controls.addView(joinButton);

        HorizontalScrollView controlScroller = new HorizontalScrollView(this);
        controlScroller.setHorizontalScrollBarEnabled(false);
        controlScroller.setFillViewport(false);
        controlScroller.setClipToPadding(false);
        controlScroller.setBackgroundColor(Color.TRANSPARENT);
        controlScroller.addView(controls, new HorizontalScrollView.LayoutParams(
            HorizontalScrollView.LayoutParams.WRAP_CONTENT, HorizontalScrollView.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams controlsParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        controlsParams.leftMargin = dp(8);
        controlsParams.rightMargin = dp(8);
        controlsParams.bottomMargin = dp(12);
        root.addView(controlScroller, controlsParams);
        setContentView(root);

        if (keepAwake) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

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
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setDefaultTextEncodingName("UTF-8");
        settings.setJavaScriptCanOpenWindowsAutomatically(false);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return routeUrl(request.getUrl());
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return routeUrl(Uri.parse(url));
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url != null && (url.startsWith("https://richup.io") || url.startsWith("https://www.richup.io"))) {
                    installAutoModeScript();
                }
            }
            private boolean routeUrl(Uri uri) {
                String scheme = uri.getScheme();
                String host = uri.getHost();
                if ("https".equalsIgnoreCase(scheme) && host != null &&
                    (host.equals("richup.io") || host.endsWith(".richup.io"))) {
                    testingMode = false;
                    return false;
                }
                if ("https".equalsIgnoreCase(scheme) && "appassets.androidplatform.net".equals(host)) return false;
                if ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                    catch (Exception ignored) { }
                }
                return true;
            }
        });

        if (savedInstanceState == null) {
            webView.loadUrl(HOME_URL);
        } else {
            testingMode = savedInstanceState.getBoolean("testingMode", false);
            if (webView.restoreState(savedInstanceState) == null) webView.loadUrl(testingMode ? TEST_URL : HOME_URL);
        }
    }

    private void showAutoSettings() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(22), dp(8), dp(22), dp(8));
        layout.setBackgroundColor(DARK);

        TextView heading = new TextView(this);
        heading.setText("Auto Play Settings");
        heading.setTextColor(PURPLE_PALE);
        heading.setTextSize(20);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setGravity(Gravity.CENTER);
        heading.setPadding(0, dp(4), 0, dp(12));
        layout.addView(heading);

        CheckBox playCheck = new CheckBox(this);
        playCheck.setText("Auto Play");
        playCheck.setTextColor(Color.WHITE);
        playCheck.setButtonTintList(ColorStateList.valueOf(PURPLE));
        playCheck.setChecked(autoMode);
        layout.addView(playCheck);

        CheckBox buyCheck = new CheckBox(this);
        buyCheck.setText("Auto Buy");
        buyCheck.setTextColor(Color.WHITE);
        buyCheck.setButtonTintList(ColorStateList.valueOf(PURPLE));
        buyCheck.setChecked(autoBuy);
        layout.addView(buyCheck);

        TextView footer = new TextView(this);
        footer.setText("edited by abood");
        footer.setTextColor(PURPLE_PALE);
        footer.setTextSize(12);
        footer.setGravity(Gravity.CENTER);
        footer.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
        footer.setPadding(0, dp(18), 0, dp(2));
        layout.addView(footer);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setView(layout)
            .setPositiveButton("Save", (d, which) -> {
                autoMode = playCheck.isChecked();
                autoBuy = buyCheck.isChecked();
                updateAutoButtonLabel();
                if (autoMode) installAutoModeScript(); else setAutoModeInPage();
                Toast.makeText(this, autoMode ? "Auto Play enabled" : "Auto Play stopped", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(PURPLE_PALE);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(PURPLE_PALE);
        });
        dialog.show();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    }

    private void showJoinRoomDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(22), dp(8), dp(22), dp(4));
        layout.setBackgroundColor(DARK);
        TextView heading = new TextView(this);
        heading.setText("Join a Richup Room");
        heading.setTextColor(PURPLE_PALE);
        heading.setTextSize(20);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setGravity(Gravity.CENTER);
        heading.setPadding(0, dp(4), 0, dp(12));
        layout.addView(heading);

        EditText linkInput = new EditText(this);
        linkInput.setSingleLine(true);
        linkInput.setHint("https://richup.io/room/s54jb");
        linkInput.setHintTextColor(0xFF9B95A8);
        linkInput.setTextColor(Color.WHITE);
        linkInput.setTextSize(14);
        linkInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        linkInput.setSelectAllOnFocus(true);
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(0xFF242033);
        inputBg.setCornerRadius(dp(10));
        inputBg.setStroke(dp(1), PURPLE);
        linkInput.setBackground(inputBg);
        linkInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        layout.addView(linkInput);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setView(layout)
            .setPositiveButton("Join Room", null)
            .setNegativeButton("Cancel", null)
            .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(PURPLE_PALE);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(PURPLE_PALE);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String raw = linkInput.getText().toString().trim();
                Uri uri = Uri.parse(raw);
                String host = uri.getHost();
                boolean valid = "https".equalsIgnoreCase(uri.getScheme()) && host != null &&
                    (host.equalsIgnoreCase("richup.io") || host.equalsIgnoreCase("www.richup.io")) &&
                    uri.getPath() != null && uri.getPath().startsWith("/room/") && uri.getPath().length() > 6;
                if (!valid) {
                    linkInput.setError("Paste a valid https://richup.io/room/... link");
                    return;
                }
                stopAutoMode();
                testingMode = false;
                webView.loadUrl(uri.toString());
                dialog.dismiss();
            });
        });
        dialog.show();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    }

    private void updateAutoButtonLabel() {
        if (autoButton != null) autoButton.setText(autoMode ? "▶ Auto: ON" : "▶ Auto Play");
    }

    private void stopAutoMode() {
        autoMode = false;
        updateAutoButtonLabel();
        setAutoModeInPage();
    }

    private TextView makeButton(String label) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(11);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(9), dp(9), dp(9), dp(9));
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(0xFF282239);
        normal.setCornerRadius(dp(15));
        normal.setStroke(dp(1), PURPLE);
        RippleDrawable ripple = new RippleDrawable(
            ColorStateList.valueOf(0x668B5CF6), normal, null);
        button.setBackground(ripple);
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void applyImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersiveMode();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) { webView.onResume(); webView.resumeTimers(); }
    }

    @Override
    protected void onPause() {
        stopAutoMode();
        if (webView != null) { webView.onPause(); webView.pauseTimers(); }
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else if (testingMode) { testingMode = false; stopAutoMode(); webView.loadUrl(HOME_URL); }
        else { stopAutoMode(); super.onBackPressed(); }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("testingMode", testingMode);
        if (webView != null) webView.saveState(outState);
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
