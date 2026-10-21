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
import android.content.SharedPreferences;
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
import android.text.InputType;

public class MainActivity extends Activity {
    private static final int PURPLE = Color.rgb(139, 92, 246);
    private static final int PURPLE_DARK = Color.rgb(109, 40, 217);
    private static final int PURPLE_PALE = Color.rgb(196, 181, 253);
    private static final int DARK = Color.rgb(23, 21, 31);
    private static final int CARD = Color.rgb(36, 32, 51);
    private static final String HOME_URL = "https://richup.io/";
    private static final String PREFS = "richup_mod_settings";

    private WebView webView;
    private FrameLayout root;
    private View controlPanel;
    private TextView panelToggle;
    private boolean panelExpanded = true;
    private boolean keepAwake = true;
    private boolean autoMode = false;
    private boolean autoBuy = false;
    private boolean fastMode = false;
    private TextView autoButton;

    private final String[] botNames = {"Bot 1", "Bot 2", "Bot 3"};
    private final int[] botColors = {Color.RED, Color.BLUE, Color.GREEN};
    private final boolean[] botEnabled = {false, false, false};
    private final int[] availableBotColors = {
        Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW,
        Color.MAGENTA, Color.CYAN, Color.WHITE,
        Color.rgb(255, 128, 0), Color.rgb(255, 105, 180),
        Color.rgb(139, 92, 246)
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        applyImmersiveMode();
        loadPreferences();

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

        // Rounded, compact control panel pinned to the right side.
        LinearLayout panelLayout = new LinearLayout(this);
        panelLayout.setOrientation(LinearLayout.VERTICAL);
        panelLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        panelLayout.setPadding(dp(5), dp(5), dp(5), dp(5));
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(0xE617151F);
        panelBg.setCornerRadius(dp(22));
        panelBg.setStroke(dp(1), PURPLE);
        panelLayout.setBackground(panelBg);

        TextView home = makeButton("⌂ Home");
        TextView reload = makeButton("↻ Reload");
        TextView awake = makeButton(keepAwake ? "☾ Awake ON" : "☾ Awake OFF");
        autoButton = makeButton(autoMode ? "▶ Auto: ON" : "▶ Auto Play");
        TextView joinButton = makeButton("↗ Join Room");

        home.setOnClickListener(v -> {
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
                awake.setText("☾ Awake ON");
            } else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                awake.setText("☾ Awake OFF");
            }
            savePreferences();
        });
        autoButton.setOnClickListener(v -> showAutoSettings());
        joinButton.setOnClickListener(v -> showJoinRoomDialog());

        panelLayout.addView(home);
        panelLayout.addView(reload);
        panelLayout.addView(awake);
        panelLayout.addView(autoButton);
        panelLayout.addView(joinButton);

        HorizontalScrollView unusedScroller = new HorizontalScrollView(this);
        unusedScroller.setVisibility(View.GONE);

        controlPanel = panelLayout;
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        panelParams.rightMargin = dp(8);
        root.addView(controlPanel, panelParams);

        panelToggle = makeButton("›");
        panelToggle.setTextSize(18);
        panelToggle.setMinWidth(dp(34));
        panelToggle.setOnClickListener(v -> {
            panelExpanded = !panelExpanded;
            controlPanel.setVisibility(panelExpanded ? View.VISIBLE : View.GONE);
            panelToggle.setText(panelExpanded ? "‹" : "›");
        });
        FrameLayout.LayoutParams toggleParams = new FrameLayout.LayoutParams(
            dp(38), dp(48), Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        toggleParams.rightMargin = panelExpanded ? dp(116) : dp(8);
        root.addView(panelToggle, toggleParams);
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
                if (url != null &&
                    (url.startsWith("https://richup.io") || url.startsWith("https://www.richup.io"))) {
                    installAutoModeScript();
                }
            }

            private boolean routeUrl(Uri uri) {
                String scheme = uri.getScheme();
                String host = uri.getHost();

                // Keep Richup pages inside the app.
                if ("https".equalsIgnoreCase(scheme) && host != null &&
                    (host.equals("richup.io") || host.endsWith(".richup.io"))) {
                    return false;
                }

                // Preserve the working login behavior: keep HTTP/HTTPS navigation in this WebView.
                if ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) {
                    webView.loadUrl(uri.toString());
                    return true;
                }
                return true;
            }
        });

        if (savedInstanceState == null) {
            webView.loadUrl(HOME_URL);
        } else if (webView.restoreState(savedInstanceState) == null) {
            webView.loadUrl(HOME_URL);
        }
    }

    private void installAutoModeScript() {
        String script =
            "(function(){" +
            "if(window.__richupAutoInstalled)return;window.__richupAutoInstalled=true;" +
            "window.__richupAutoTimer=null;window.__richupAutoLastAction=0;" +
            "window.richupAutoSetMode=function(enabled,buy,fast){" +
            "if(window.__richupAutoTimer){clearInterval(window.__richupAutoTimer);window.__richupAutoTimer=null;}" +
            "window.__richupAutoBuy=!!buy;window.__richupAutoFast=!!fast;if(!enabled)return;" +
            "window.__richupAutoTimer=setInterval(function(){" +
            "var now=Date.now();var gap=window.__richupAutoFast?450:1200;" +
            "if(now-window.__richupAutoLastAction<gap)return;" +
            "var buttons=Array.from(document.querySelectorAll('button,[role=button]'));" +
            "var label=function(b){return (b.innerText||b.textContent||b.getAttribute('aria-label')||'').trim().replace(/\\s+/g,' ');};" +
            "var visible=function(b){return !b.disabled&&b.getAttribute('aria-disabled')!=='true'&&b.getClientRects().length>0&&getComputedStyle(b).visibility!=='hidden';};" +
            "var find=function(re){return buttons.find(function(b){return visible(b)&&re.test(label(b));});};" +
            "var click=function(b){if(!b)return false;b.click();window.__richupAutoLastAction=Date.now();return true;};" +
            "var again=find(/roll\\s*again|roll again/i);if(again){click(again);return;}" +
            "var buy=find(/^buy(?:\\s|$)|buy property|purchase property/i);" +
            "if(buy&&window.__richupAutoBuy){click(buy);return;}" +
            "var roll=find(/^roll the dice$|^roll dice$|^roll$/i);if(roll){click(roll);return;}" +
            "var end=find(/^end turn$/i);if(end&&now-window.__richupAutoLastAction>=(window.__richupAutoFast?450:1800)){click(end);return;}" +
            "var auction=find(/^auction$/i);if(auction&&now-window.__richupAutoLastAction>=(window.__richupAutoFast?450:1800)){click(auction);return;}" +
            "},window.__richupAutoFast?150:300);};" +
            "})();";

        if (webView != null) {
            webView.evaluateJavascript(script, value -> {
                if (webView != null) {
                    webView.evaluateJavascript(
                        "if(window.richupAutoSetMode)window.richupAutoSetMode(" +
                        autoMode + "," + autoBuy + "," + fastMode + ");", null);
                }
            });
        }
    }

    private void setAutoModeInPage() {
        if (webView != null) {
            webView.evaluateJavascript(
                "if(window.richupAutoSetMode)window.richupAutoSetMode(" +
                autoMode + "," + autoBuy + "," + fastMode + ");", null);
        }
    }

    private void showAutoSettings() {
        LinearLayout layout = dialogLayout();
        TextView heading = makeHeading("Auto Play Settings");
        layout.addView(heading);

        CheckBox playCheck = makeCheckBox("Auto Play", autoMode);
        layout.addView(playCheck);
        layout.addView(makeDescription(
            "Automatically rolls the dice, takes Roll Again when available, and ends the turn when no extra roll remains."));

        CheckBox buyCheck = makeCheckBox("Auto Buy", autoBuy);
        layout.addView(buyCheck);
        layout.addView(makeDescription(
            "Buys available properties when enabled and affordable; skips purchases the game does not allow."));

        CheckBox fastCheck = makeCheckBox("⚡ Fast Mode", fastMode);
        layout.addView(fastCheck);
        layout.addView(makeDescription(
            "Reduces the delay between automatic actions. Roll and Roll Again are attempted much faster (about 450 ms between clicks instead of 1.2 seconds). Actions can still be limited by game animations and loading."));

        TextView warning = makeDescription(
            "⚠ Ban risk: rapid automation may violate Richup's rules and could lead to account restrictions or a ban. Use at your own risk.");
        warning.setTextColor(Color.rgb(255, 140, 140));
        warning.setPadding(dp(12), dp(5), dp(8), dp(10));
        layout.addView(warning);

        TextView bots = makeButton("🪙 Auto Earn Coins (Bots Playing)");
        bots.setTextSize(13);
        bots.setPadding(dp(12), dp(12), dp(12), dp(12));
        bots.setOnClickListener(v -> showBotSettings());
        layout.addView(bots);

        addFooter(layout);
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setView(layout)
            .setPositiveButton("Save", (d, which) -> {
                autoMode = playCheck.isChecked();
                autoBuy = buyCheck.isChecked();
                fastMode = fastCheck.isChecked();
                savePreferences();
                updateAutoButtonLabel();
                if (autoMode) installAutoModeScript(); else setAutoModeInPage();
                Toast.makeText(this, autoMode ? "Auto Play enabled" : "Auto Play stopped",
                    Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .create();
        styleDialog(dialog);
        dialog.show();
        styleDialogButtons(dialog);
    }

    private void showBotSettings() {
        LinearLayout layout = dialogLayout();
        layout.addView(makeHeading("Auto Earn Coins"));

        TextView note = makeDescription(
            "Set up to three bot profiles. Names, colors, and toggles are saved in this app. These settings do not create extra Richup players by themselves; real bots require a supported way to join and play as separate accounts.");
        note.setPadding(dp(4), dp(8), dp(4), dp(12));
        layout.addView(note);

        EditText[] names = new EditText[3];
        CheckBox[] enabledChecks = new CheckBox[3];
        TextView[] colorButtons = new TextView[3];

        for (int i = 0; i < 3; i++) {
            final int index = i;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12), dp(8), dp(12), dp(10));
            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setColor(CARD);
            cardBg.setCornerRadius(dp(16));
            cardBg.setStroke(dp(1), 0x665B4B80);
            card.setBackground(cardBg);

            TextView title = makeHeading("Bot Slot " + (i + 1));
            title.setTextSize(15);
            title.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            card.addView(title);

            EditText name = new EditText(this);
            name.setSingleLine(true);
            name.setText(botNames[i]);
            name.setHint("Player name");
            name.setTextColor(Color.WHITE);
            name.setHintTextColor(0xFF9B95A8);
            name.setTextSize(14);
            name.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            name.setPadding(dp(12), dp(8), dp(12), dp(8));
            GradientDrawable inputBg = new GradientDrawable();
            inputBg.setColor(0xFF1C1927);
            inputBg.setCornerRadius(dp(12));
            inputBg.setStroke(dp(1), 0xFF5C4B82);
            name.setBackground(inputBg);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            nameParams.topMargin = dp(5);
            card.addView(name, nameParams);
            names[i] = name;

            CheckBox enabled = makeCheckBox("Enable this bot profile", botEnabled[i]);
            card.addView(enabled);
            enabledChecks[i] = enabled;

            TextView color = makeButton("●  Change Color");
            color.setTextColor(botColors[i]);
            color.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            color.setPadding(dp(12), dp(8), dp(12), dp(8));
            color.setOnClickListener(v -> {
                int current = 0;
                for (int j = 0; j < availableBotColors.length; j++) {
                    if (availableBotColors[j] == botColors[index]) {
                        current = j;
                        break;
                    }
                }
                botColors[index] = availableBotColors[(current + 1) % availableBotColors.length];
                color.setTextColor(botColors[index]);
            });
            card.addView(color);
            colorButtons[i] = color;

            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cardParams.bottomMargin = dp(10);
            layout.addView(card, cardParams);
        }

        addFooter(layout);
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setView(layout)
            .setPositiveButton("Save", (d, which) -> {
                for (int i = 0; i < 3; i++) {
                    String entered = names[i].getText().toString().trim();
                    botNames[i] = entered.isEmpty() ? "Bot " + (i + 1) : entered;
                    botEnabled[i] = enabledChecks[i].isChecked();
                }
                savePreferences();
                Toast.makeText(this, "Bot profiles saved", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .create();
        styleDialog(dialog);
        dialog.show();
        styleDialogButtons(dialog);
    }

    private void showJoinRoomDialog() {
        LinearLayout layout = dialogLayout();
        layout.addView(makeHeading("Join a Richup Room"));

        EditText linkInput = new EditText(this);
        linkInput.setSingleLine(true);
        linkInput.setHint("Enter room code (e.g. xd9a3)");
        linkInput.setHintTextColor(0xFF9B95A8);
        linkInput.setTextColor(Color.WHITE);
        linkInput.setTextSize(14);
        linkInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        linkInput.setSelectAllOnFocus(true);
        linkInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(CARD);
        inputBg.setCornerRadius(dp(12));
        inputBg.setStroke(dp(1), PURPLE);
        linkInput.setBackground(inputBg);
        layout.addView(linkInput);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setView(layout)
            .setPositiveButton("Join Room", null)
            .setNegativeButton("Cancel", null)
            .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String code = linkInput.getText().toString().trim();
                if (!code.matches("[A-Za-z0-9]+")) {
                    linkInput.setError("Enter the room code only (letters and numbers)");
                    return;
                }
                stopAutoMode();
                webView.loadUrl("https://richup.io/room/" + code);
                dialog.dismiss();
            });
        });
        dialog.show();
        styleDialogButtons(dialog);
    }

    private LinearLayout dialogLayout() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(12), dp(20), dp(12));
        layout.setBackgroundColor(DARK);
        return layout;
    }

    private TextView makeHeading(String text) {
        TextView heading = new TextView(this);
        heading.setText(text);
        heading.setTextColor(PURPLE_PALE);
        heading.setTextSize(20);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heading.setGravity(Gravity.CENTER);
        heading.setPadding(0, dp(4), 0, dp(12));
        return heading;
    }

    private CheckBox makeCheckBox(String text, boolean checked) {
        CheckBox box = new CheckBox(this);
        box.setText(text);
        box.setTextColor(Color.WHITE);
        box.setTextSize(14);
        box.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        box.setButtonTintList(ColorStateList.valueOf(PURPLE));
        box.setChecked(checked);
        return box;
    }

    private void addFooter(LinearLayout layout) {
        TextView footer = new TextView(this);
        footer.setText("edited by abood");
        footer.setTextColor(PURPLE_PALE);
        footer.setTextSize(12);
        footer.setGravity(Gravity.CENTER);
        footer.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
        footer.setPadding(0, dp(14), 0, dp(2));
        layout.addView(footer);
    }

    private void styleDialog(AlertDialog dialog) {
        dialog.setOnShowListener(d -> styleDialogButtons(dialog));
    }

    private void styleDialogButtons(AlertDialog dialog) {
        if (dialog.getWindow() != null) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(DARK);
            bg.setCornerRadius(dp(22));
            dialog.getWindow().setBackgroundDrawable(bg);
        }
        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(PURPLE_PALE);
        if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(PURPLE_PALE);
    }

    private void updateAutoButtonLabel() {
        if (autoButton != null) autoButton.setText(autoMode ? "▶ Auto: ON" : "▶ Auto Play");
    }

    private void stopAutoMode() {
        autoMode = false;
        updateAutoButtonLabel();
        setAutoModeInPage();
    }

    private TextView makeDescription(String text) {
        TextView description = new TextView(this);
        description.setText(text);
        description.setTextColor(0xFFB9B3C9);
        description.setTextSize(12);
        description.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        description.setPadding(dp(12), 0, dp(4), dp(10));
        return description;
    }

    private TextView makeButton(String label) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(12);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        button.setMinWidth(dp(92));
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(0xFF282239);
        normal.setCornerRadius(dp(16));
        normal.setStroke(dp(1), PURPLE);
        RippleDrawable ripple = new RippleDrawable(
            ColorStateList.valueOf(0x668B5CF6), normal, null);
        button.setBackground(ripple);
        button.setClickable(true);
        button.setFocusable(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(4);
        button.setLayoutParams(params);
        return button;
    }

    private void loadPreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        autoMode = prefs.getBoolean("autoMode", false);
        autoBuy = prefs.getBoolean("autoBuy", false);
        fastMode = prefs.getBoolean("fastMode", false);
        keepAwake = prefs.getBoolean("keepAwake", true);
        for (int i = 0; i < 3; i++) {
            botNames[i] = prefs.getString("botName" + i, "Bot " + (i + 1));
            botColors[i] = prefs.getInt("botColor" + i, availableBotColors[i]);
            botEnabled[i] = prefs.getBoolean("botEnabled" + i, false);
        }
    }

    private void savePreferences() {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        editor.putBoolean("autoMode", autoMode);
        editor.putBoolean("autoBuy", autoBuy);
        editor.putBoolean("fastMode", fastMode);
        editor.putBoolean("keepAwake", keepAwake);
        for (int i = 0; i < 3; i++) {
            editor.putString("botName" + i, botNames[i]);
            editor.putInt("botColor" + i, botColors[i]);
            editor.putBoolean("botEnabled" + i, botEnabled[i]);
        }
        editor.apply();
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
        if (webView != null) {
            webView.onResume();
            webView.resumeTimers();
            if (autoMode) installAutoModeScript();
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
        if (webView != null && webView.canGoBack()) webView.goBack();
        else {
            stopAutoMode();
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        savePreferences();
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
