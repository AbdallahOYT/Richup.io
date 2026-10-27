
package io.richup.wrapper;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.text.InputType;
import android.widget.*;
import android.text.TextUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.regex.Pattern;

import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

public class MainActivity extends Activity {

    private static final int PURPLE = Color.rgb(139, 92, 246);
    private static final int PALE = Color.rgb(196, 181, 253);
    private static final int DARK = Color.rgb(23, 21, 31);
    private static final int CARD = Color.rgb(36, 32, 51);
    private static final String HOME = "https://richup.io/";
    private static final int COUNT = 4;

    private final WebView[] players = new WebView[COUNT];
    private final ArrayList<UserScript> userScripts = new ArrayList<>();

    private static class UserScript {
        String name;
        String code;
        String sourceId;
        boolean enabled;

        UserScript(String name, String code, String sourceId, boolean enabled) {
            this.name = name;
            this.code = code;
            this.sourceId = sourceId;
            this.enabled = enabled;
        }
    }
    private final boolean[] autoPlay = new boolean[COUNT];
    private final boolean[] autoBuy = new boolean[COUNT];
    private final boolean[] fastMode = new boolean[COUNT];
    private final String[] names = {
        "Main", "Player 1", "Player 2", "Player 3"
    };
    private final int[] playerColors = {
        Color.WHITE, Color.rgb(255, 90, 90),
        Color.rgb(90, 160, 255), Color.rgb(90, 220, 130)
    };

    private final int[] colorChoices = {
        Color.rgb(255, 90, 90),
        Color.rgb(90, 160, 255),
        Color.rgb(90, 220, 130),
        Color.YELLOW, Color.MAGENTA, Color.CYAN,
        Color.WHITE, Color.rgb(255, 128, 0),
        Color.rgb(255, 105, 180), PURPLE
    };

    private LinearLayout tabs;
    private FrameLayout browserHost;
    private View panel;
    private TextView panelToggle;
    private TextView autoButton;

    private int selected = 0;
    private boolean panelOpen = true;
    private boolean keepAwake = true;
    private boolean profilesSupported = false;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        );

        applyImmersive();
        prefs = getSharedPreferences("richup_mod_settings", MODE_PRIVATE);
        loadPreferences();
        loadUserScripts();

        profilesSupported = WebViewFeature.isFeatureSupported(
            WebViewFeature.MULTI_PROFILE
        );

        if (keepAwake) {
            getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            );
        }

        buildInterface();

        for (int i = 0; i < COUNT; i++) {
            createPlayer(i);
        }

        selectPlayer(0);

        if (!profilesSupported) {
            new AlertDialog.Builder(this)
                .setTitle("WebView profiles unavailable")
                .setMessage(
                    "Your installed Android System WebView does not " +
                    "support multi-profile mode. The four tabs will work, " +
                    "but their login cookies may be shared. Update Android " +
                    "System WebView from Google Play and try again."
                )
                .setPositiveButton("OK", null)
                .show();
        }
    }

    private void buildInterface() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(DARK);

        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.setFillViewport(false);
        tabScroll.setBackgroundColor(DARK);

        tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(5), dp(5), dp(5), dp(5));

        for (int i = 0; i < COUNT; i++) {
            final int index = i;
            TextView tab = button(names[i]);
            tab.setTag("tab_" + i);
            tab.setMinWidth(dp(88));
            tab.setTextSize(12);
            tab.setPadding(dp(16), dp(11), dp(16), dp(11));
            tab.setTextColor(playerColors[i]);
            tab.setBackground(playerTabBackground(i == selected));
            tab.setElevation(dp(2));
            tab.setOnClickListener(v -> selectPlayer(index));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                -2, -2
            );
            lp.setMargins(dp(3), 0, dp(3), 0);
            tabs.addView(tab, lp);
        }

        tabScroll.addView(tabs);
        page.addView(tabScroll, new LinearLayout.LayoutParams(-1, -2));

        browserHost = new FrameLayout(this);
        page.addView(browserHost, new LinearLayout.LayoutParams(
            -1, 0, 1
        ));

        setContentView(page);
    }

    private void createPlayer(int index) {
        WebView web = new WebView(this);
        players[index] = web;

        // This must happen before configuring or using the WebView.
        if (profilesSupported) {
            try {
                WebViewCompat.setProfile(
                    web, "richup_profile_" + index
                );
            } catch (Exception e) {
                profilesSupported = false;
            }
        }

        web.setBackgroundColor(Color.rgb(15, 18, 20));
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadsImagesAutomatically(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setDefaultTextEncodingName("UTF-8");
        s.setJavaScriptCanOpenWindowsAutomatically(false);

        web.setWebChromeClient(new WebChromeClient());

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(
                WebView view, WebResourceRequest request
            ) {
                return routeUrl(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(
                WebView view, String url
            ) {
                return routeUrl(Uri.parse(url));
            }

            private boolean routeUrl(Uri uri) {
                String scheme = uri.getScheme();

                // Keep web navigation inside this player's WebView.
                if ("https".equalsIgnoreCase(scheme)
                    || "http".equalsIgnoreCase(scheme)) {
                    return false;
                }

                // Do not launch external applications from page links.
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                if (url != null && (
                    url.startsWith("https://richup.io")
                    || url.startsWith("https://www.richup.io")
                )) {
                    installAutoScript(index);
                    runUserScripts(index, url);
                }
            }
        });

        browserHost.addView(web, new FrameLayout.LayoutParams(
            -1, -1
        ));

        web.setVisibility(View.GONE);
        web.loadUrl(HOME);
        buildControlPanel();
    }

    private void selectPlayer(int index) {
        selected = index;

        for (int i = 0; i < COUNT; i++) {
            if (players[i] != null) {
                players[i].setVisibility(
                    i == selected ? View.VISIBLE : View.GONE
                );
            }

            View tab = tabs.findViewWithTag("tab_" + i);
            if (tab instanceof TextView) {
                TextView t = (TextView) tab;
                t.setText(names[i]);
                t.setTextColor(playerColors[i]);
                t.setBackground(playerTabBackground(i == selected));
                t.setAlpha(i == selected ? 1f : 0.78f);
            }
        }

        if (panel != null) {
            panel.bringToFront();
            panelToggle.bringToFront();
            updateAutoLabel();
        }
    }

    private void buildControlPanel() {
        if (panel != null) return;

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setGravity(Gravity.CENTER_HORIZONTAL);
        controls.setPadding(dp(7), dp(8), dp(7), dp(8));
        controls.setElevation(dp(10));

        GradientDrawable bg = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] { 0xF52A243A, 0xF0181622 }
        );
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(1), 0xFF7652D6);
        controls.setBackground(bg);

        TextView home = button("⌂ Home");
        TextView reload = button("↻ Reload");
        TextView awake = button("☾ Awake ON");
        autoButton = button("▶ Auto Play");
        TextView join = button("↗ Join Room");
        TextView setup = button("⚙ Players");
        TextView scripts = button("⌘ Scripts");

        home.setOnClickListener(v -> {
            stopAuto(selected);
            players[selected].loadUrl(HOME);
        });

        reload.setOnClickListener(v -> {
            stopAuto(selected);
            players[selected].reload();
        });

        awake.setText(keepAwake ? "☾ Awake ON" : "☾ Awake OFF");
        awake.setOnClickListener(v -> {
            keepAwake = !keepAwake;

            if (keepAwake) {
                getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                );
            } else {
                getWindow().clearFlags(
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                );
            }

            awake.setText(
                keepAwake ? "☾ Awake ON" : "☾ Awake OFF"
            );
            savePreferences();
        });

        autoButton.setOnClickListener(v -> showAutoSettings());
        join.setOnClickListener(v -> showJoinDialog());
        setup.setOnClickListener(v -> showPlayerSettings());
        scripts.setOnClickListener(v -> showScriptsManager());

        controls.addView(home);
        controls.addView(reload);
        controls.addView(awake);
        controls.addView(autoButton);
        controls.addView(join);
        controls.addView(setup);
        controls.addView(scripts);

        panel = controls;

        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(
            -2, -2, Gravity.RIGHT | Gravity.CENTER_VERTICAL
        );
        pp.rightMargin = dp(8);
        browserHost.addView(panel, pp);

        panelToggle = button("‹");
        panelToggle.setTextSize(18);
        panelToggle.setMinWidth(dp(34));
        panelToggle.setOnClickListener(v -> {
            panelOpen = !panelOpen;
            panel.setVisibility(panelOpen ? View.VISIBLE : View.GONE);
            panelToggle.setText(panelOpen ? "‹" : "›");
            FrameLayout.LayoutParams tp =
                (FrameLayout.LayoutParams) panelToggle.getLayoutParams();
            tp.rightMargin = panelOpen ? dp(116) : dp(8);
            panelToggle.setLayoutParams(tp);
        });

        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(
            dp(38), dp(48), Gravity.RIGHT | Gravity.CENTER_VERTICAL
        );
        tp.rightMargin = dp(116);
        browserHost.addView(panelToggle, tp);
    }

    // User scripts are stored in SharedPreferences. Bundled .js files belong in
    // app/src/main/assets/userscripts/ and are loaded automatically on startup.
    private void loadUserScripts() {
        userScripts.clear();
        String saved = prefs.getString("userScripts", "[]");
        try {
            JSONArray arr = new JSONArray(saved);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj == null) continue;
                String code = obj.optString("code", "");
                if (code.trim().isEmpty()) continue;
                String name = obj.optString("name", "Unnamed script");
                String id = obj.optString("id", "saved_" + i);
                boolean enabled = obj.optBoolean("enabled", false);
                userScripts.add(new UserScript(name, code, id, enabled));
            }
        } catch (Exception ignored) { }

        try {
            String[] files = getAssets().list("userscripts");
            if (files != null) {
                for (String file : files) {
                    if (!file.toLowerCase().endsWith(".js")) continue;
                    StringBuilder code = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(getAssets().open("userscripts/" + file), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) code.append(line).append('\n');
                    }
                    String id = "asset:" + file;
                    if (findScriptById(id) == null) {
                        String source = code.toString();
                        userScripts.add(new UserScript(extractScriptName(source, file), source, id, false));
                    }
                }
            }
        } catch (Exception ignored) { }
        saveUserScripts();
    }

    private UserScript findScriptById(String id) {
        for (UserScript script : userScripts) if (script.sourceId.equals(id)) return script;
        return null;
    }

    private String extractScriptName(String code, String fallback) {
        java.util.regex.Matcher m = Pattern.compile("(?im)^\\s*//\\s*@name\\s+(.+?)\\s*$").matcher(code);
        return m.find() ? m.group(1).trim() : fallback;
    }

    private void saveUserScripts() {
        JSONArray arr = new JSONArray();
        try {
            for (UserScript script : userScripts) {
                JSONObject obj = new JSONObject();
                obj.put("name", script.name);
                obj.put("code", script.code);
                obj.put("id", script.sourceId);
                obj.put("enabled", script.enabled);
                arr.put(obj);
            }
            prefs.edit().putString("userScripts", arr.toString()).apply();
        } catch (Exception ignored) { }
    }

    private void showScriptsManager() {
        LinearLayout layout = dialogLayout();
        layout.addView(heading("Userscripts"));
        layout.addView(description("Scripts run only on Richup pages. Only enable scripts you trust."));

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(4), 0, dp(4));
        if (userScripts.isEmpty()) {
            list.addView(description("No scripts yet. Add a .js file to app/src/main/assets/userscripts/ or paste one below."));
        }
        for (int i = 0; i < userScripts.size(); i++) {
            final int index = i;
            UserScript script = userScripts.get(i);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(8), dp(6), dp(4), dp(6));
            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setColor(CARD);
            cardBg.setCornerRadius(dp(12));
            card.setBackground(cardBg);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
            cp.bottomMargin = dp(6);
            list.addView(card, cp);

            CheckBox enabled = new CheckBox(this);
            enabled.setButtonTintList(ColorStateList.valueOf(PALE));
            enabled.setChecked(script.enabled);
            card.addView(enabled, new LinearLayout.LayoutParams(-2, -2));

            TextView title = new TextView(this);
            title.setText(script.name);
            title.setTextColor(Color.WHITE);
            title.setTextSize(13);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1f);
            titleLp.leftMargin = dp(4);
            card.addView(title, titleLp);

            TextView delete = button("✕");
            delete.setTextSize(12);
            delete.setMinWidth(dp(38));
            delete.setPadding(dp(8), dp(6), dp(8), dp(6));
            card.addView(delete, new LinearLayout.LayoutParams(-2, -2));
            enabled.setOnCheckedChangeListener((buttonView, checked) -> {
                userScripts.get(index).enabled = checked;
                saveUserScripts();
                Toast.makeText(this, checked ? "Script enabled" : "Script disabled — reload the page to remove its changes", Toast.LENGTH_SHORT).show();
                WebView web = players[selected];
                if (web != null && web.getUrl() != null) runUserScripts(selected, web.getUrl());
            });
            delete.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Delete script?")
                .setMessage("Remove “" + userScripts.get(index).name + "” from the app?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    userScripts.remove(index);
                    saveUserScripts();
                    showScriptsManager();
                }).show());
        }
        scroll.addView(list);
        layout.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        TextView add = button("＋ Add your script");
        add.setOnClickListener(v -> showAddScriptDialog());
        layout.addView(add);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setView(layout)
            .setPositiveButton("Done", null)
            .create();
        dialog.show();
        styleDialog(dialog);
        dialog.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels * 0.92f), (int)(getResources().getDisplayMetrics().heightPixels * 0.82f));
    }

    private void showAddScriptDialog() {
        LinearLayout layout = dialogLayout();
        layout.addView(heading("Add your script"));
        EditText nameInput = new EditText(this);
        nameInput.setSingleLine(true);
        nameInput.setHint("Script name (optional)");
        nameInput.setTextColor(Color.WHITE);
        nameInput.setHintTextColor(0xFF9B95A8);
        nameInput.setBackground(inputBackground());
        nameInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        layout.addView(nameInput);

        EditText codeInput = new EditText(this);
        codeInput.setGravity(Gravity.TOP | Gravity.START);
        codeInput.setHint("Paste your .user.js or JavaScript code here…");
        codeInput.setTextColor(Color.WHITE);
        codeInput.setHintTextColor(0xFF9B95A8);
        codeInput.setTextSize(12);
        codeInput.setTypeface(Typeface.MONOSPACE);
        codeInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        codeInput.setMinLines(8);
        codeInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        codeInput.setBackground(inputBackground());
        layout.addView(codeInput, new LinearLayout.LayoutParams(-1, 0, 1f));

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setView(layout)
            .setPositiveButton("Save script", null)
            .setNegativeButton("Cancel", null)
            .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String code = codeInput.getText().toString().trim();
            if (code.isEmpty()) { codeInput.setError("Paste the script code first"); return; }
            String name = nameInput.getText().toString().trim();
            if (name.isEmpty()) name = extractScriptName(code, "Custom script " + (userScripts.size() + 1));
            String id = "custom:" + System.currentTimeMillis();
            userScripts.add(new UserScript(name, code, id, true));
            saveUserScripts();
            dialog.dismiss();
            showScriptsManager();
            WebView web = players[selected];
            if (web != null && web.getUrl() != null) runUserScripts(selected, web.getUrl());
        }));
        dialog.show();
        styleDialog(dialog);
        dialog.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels * 0.94f), (int)(getResources().getDisplayMetrics().heightPixels * 0.86f));
    }

    private void runUserScripts(int index, String url) {
        WebView web = players[index];
        if (web == null || url == null) return;
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        if (host == null || !(host.equalsIgnoreCase("richup.io") || host.toLowerCase().endsWith(".richup.io"))) return;

        for (UserScript script : userScripts) {
            if (!script.enabled || !scriptMatches(script.code, url)) continue;
            String code = script.code;
            // Lightweight compatibility shim for common CSS-focused userscripts.
            String shim = "(function(){if(window.__3boodScriptIds&&window.__3boodScriptIds[" + JSONObject.quote(script.sourceId) + "])return;" +
                "window.__3boodScriptIds=window.__3boodScriptIds||{};window.__3boodScriptIds[" + JSONObject.quote(script.sourceId) + "]=true;" +
                "window.GM_addStyle=window.GM_addStyle||function(css){var s=document.createElement('style');s.textContent=css; (document.head||document.documentElement).appendChild(s);return s;};" +
                "window.GM_getResourceText=window.GM_getResourceText||function(){return '';};})();\n";
            // Strip the metadata block: it is not executable JavaScript.
            code = code.replaceFirst("(?s)^\\s*// ==UserScript==.*?// ==/UserScript==\\s*", "");
            web.evaluateJavascript(shim + "\n;try{\n" + code + "\n}catch(e){console.error('Userscript failed:',e);}", null);
        }
    }

    private boolean scriptMatches(String code, String url) {
        java.util.regex.Matcher matcher = Pattern.compile("(?im)^\\s*//\\s*@match\\s+([^\\s]+)").matcher(code);
        boolean hasMatch = false;
        while (matcher.find()) {
            hasMatch = true;
            String glob = matcher.group(1).trim();
            String regex = Pattern.quote(glob).replace("*", "\\E.*\\Q");
            try {
                if (Pattern.compile("^" + regex + "$", Pattern.CASE_INSENSITIVE).matcher(url).matches()) return true;
            } catch (Exception ignored) { }
        }
        // Without @match, do not let pasted code run on arbitrary pages; keep it to Richup only.
        return !hasMatch && (url.startsWith("https://richup.io") || url.startsWith("https://www.richup.io"));
    }

    private void installAutoScript(int index) {
        WebView web = players[index];
        if (web == null) return;

        String script =
            "(function(){" +
            "if(window.__richupAutoInstalled)return;" +
            "window.__richupAutoInstalled=true;" +
            "window.__richupAutoTimer=null;" +
            "window.__richupAutoLastAction=0;" +
            "window.richupAutoSetMode=function(enabled,buy,fast){" +
            "if(window.__richupAutoTimer){" +
            "clearInterval(window.__richupAutoTimer);" +
            "window.__richupAutoTimer=null;}" +
            "window.__richupAutoBuy=!!buy;" +
            "window.__richupAutoFast=!!fast;" +
            "if(!enabled)return;" +
            "window.__richupAutoTimer=setInterval(function(){" +
            "var now=Date.now();" +
            "var gap=window.__richupAutoFast?450:1200;" +
            "if(now-window.__richupAutoLastAction<gap)return;" +
            "var buttons=Array.from(document.querySelectorAll(" +
            "'button,[role=button]'));" +
            "var label=function(b){return (b.innerText||" +
            "b.textContent||b.getAttribute('aria-label')||'')" +
            ".trim().replace(/\\s+/g,' ');};" +
            "var visible=function(b){return !b.disabled&&" +
            "b.getAttribute('aria-disabled')!=='true'&&" +
            "b.getClientRects().length>0&&" +
            "getComputedStyle(b).visibility!=='hidden';};" +
            "var find=function(re){return buttons.find(function(b){" +
            "return visible(b)&&re.test(label(b));});};" +
            "var click=function(b){if(!b)return false;b.click();" +
            "window.__richupAutoLastAction=Date.now();return true;};" +
            "var again=find(/roll\\s*again/i);" +
            "if(again){click(again);return;}" +
            "var buyButton=find(/^buy(?:\\s|$)|buy property|" +
            "purchase property/i);" +
            "if(buyButton&&window.__richupAutoBuy){" +
            "click(buyButton);return;}" +
            "var roll=find(/^roll the dice$|^roll dice$|^roll$/i);" +
            "if(roll){click(roll);return;}" +
            "var end=find(/^end turn$/i);" +
            "if(end&&now-window.__richupAutoLastAction>=" +
            "(window.__richupAutoFast?450:1800)){click(end);return;}" +
            "var auction=find(/^auction$/i);" +
            "if(auction&&now-window.__richupAutoLastAction>=" +
            "(window.__richupAutoFast?450:1800)){click(auction);return;}" +
            "},window.__richupAutoFast?150:300);};})();";

        web.evaluateJavascript(script, value -> applyAutoSettings(index));
    }

    private void applyAutoSettings(int index) {
        WebView web = players[index];
        if (web == null) return;

        web.evaluateJavascript(
            "if(window.richupAutoSetMode)window.richupAutoSetMode(" +
            autoPlay[index] + "," + autoBuy[index] + "," +
            fastMode[index] + ");",
            null
        );
    }

    private void showAutoSettings() {
        LinearLayout layout = dialogLayout();
        layout.addView(heading("Settings — " + names[selected]));

        CheckBox play = check("Auto Play", autoPlay[selected]);
        CheckBox buy = check("Auto Buy", autoBuy[selected]);
        CheckBox fast = check("⚡ Fast Mode", fastMode[selected]);

        layout.addView(play);
        layout.addView(description(
            "Automatically attempts available dice rolls and turn actions."
        ));
        layout.addView(buy);
        layout.addView(description(
            "Attempts to buy properties when a matching button is available."
        ));
        layout.addView(fast);
        layout.addView(description(
            "Reduces the delay between attempts. Fast automation may violate " +
            "game rules and could result in account restrictions."
        ));

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setView(layout)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create();

        dialog.setOnShowListener(d ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    autoPlay[selected] = play.isChecked();
                    autoBuy[selected] = buy.isChecked();
                    fastMode[selected] = fast.isChecked();

                    savePreferences();
                    applyAutoSettings(selected);
                    updateAutoLabel();
                    dialog.dismiss();
                })
        );

        dialog.show();
        styleDialog(dialog);
    }

    private void stopAuto(int index) {
        autoPlay[index] = false;
        applyAutoSettings(index);
        savePreferences();
        updateAutoLabel();
    }

    private void updateAutoLabel() {
        if (autoButton != null) {
            autoButton.setText(
                autoPlay[selected] ? "▶ Auto: ON" : "▶ Auto Play"
            );
        }
    }

    private void showJoinDialog() {
        LinearLayout layout = dialogLayout();
        layout.addView(heading("Join a Richup Room"));
        layout.addView(description(
            "Enter a room code or paste the full Richup room link."
        ));

        EditText codeInput = new EditText(this);
        codeInput.setSingleLine(true);
        codeInput.setHint("Room code or full link");
        codeInput.setTextColor(Color.WHITE);
        codeInput.setHintTextColor(0xFF9B95A8);
        codeInput.setTextSize(14);
        codeInput.setPadding(dp(14), dp(13), dp(14), dp(13));
        codeInput.setBackground(inputBackground());
        codeInput.setInputType(
            InputType.TYPE_CLASS_TEXT |
            InputType.TYPE_TEXT_VARIATION_URI
        );
        layout.addView(codeInput);

        TextView example = description(
            "Example: ihs8s  •  https://richup.io/room/ihs8s"
        );
        example.setPadding(dp(4), dp(9), dp(4), dp(2));
        layout.addView(example);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setView(layout)
            .setPositiveButton("Join Room", null)
            .setNegativeButton("Cancel", null)
            .create();

        dialog.setOnShowListener(d ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String entered = codeInput.getText().toString().trim();
                    if (entered.isEmpty()) {
                        codeInput.setError("Enter a room code or link");
                        return;
                    }

                    String roomCode = null;
                    if (entered.matches("[A-Za-z0-9_-]{3,64}")) {
                        // A short room code such as ihs8s.
                        roomCode = entered;
                    } else {
                        // Accept full links, including links without a scheme.
                        String candidate = entered;
                        if (!candidate.matches("(?i)^https?://.*")) {
                            candidate = "https://" + candidate;
                        }
                        try {
                            Uri uri = Uri.parse(candidate);
                            String host = uri.getHost();
                            boolean validHost = host != null && (
                                host.equalsIgnoreCase("richup.io") ||
                                host.equalsIgnoreCase("www.richup.io")
                            );
                            java.util.regex.Matcher matcher = Pattern.compile(
                                "(?i)^/room/([A-Za-z0-9_-]{3,64})/?$"
                            ).matcher(uri.getPath() == null ? "" : uri.getPath());
                            if (validHost &&
                                ("http".equalsIgnoreCase(uri.getScheme()) ||
                                 "https".equalsIgnoreCase(uri.getScheme())) &&
                                matcher.matches()) {
                                roomCode = matcher.group(1);
                            }
                        } catch (Exception ignored) { }
                    }

                    if (roomCode == null) {
                        codeInput.setError(
                            "Use a room code or a Richup link ending in /room/CODE"
                        );
                        return;
                    }

                    // Normalize to the official HTTPS room URL and keep it in-app.
                    players[selected].loadUrl(
                        "https://richup.io/room/" + roomCode
                    );
                    dialog.dismiss();
                })
        );

        dialog.show();
        styleDialog(dialog);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.92f),
                -2
            );
        }
    }

    private void showPlayerSettings() {
        LinearLayout layout = dialogLayout();
        layout.addView(heading("Player Names & Colors"));

        EditText[] inputs = new EditText[COUNT];
        TextView[] colors = new TextView[COUNT];

        for (int i = 0; i < COUNT; i++) {
            final int index = i;
            EditText input = new EditText(this);
            input.setSingleLine(true);
            input.setText(names[i]);
            input.setHint("Player name");
            input.setTextColor(Color.WHITE);
            input.setHintTextColor(0xFF9B95A8);
            input.setPadding(dp(12), dp(8), dp(12), dp(8));
            input.setBackground(inputBackground());
            layout.addView(input);
            inputs[i] = input;

            TextView color = button("● Change color");
            color.setTextColor(playerColors[i]);
            color.setOnClickListener(v -> {
                int current = 0;
                for (int j = 0; j < colorChoices.length; j++) {
                    if (colorChoices[j] == playerColors[index]) {
                        current = j;
                        break;
                    }
                }
                playerColors[index] =
                    colorChoices[(current + 1) % colorChoices.length];
                color.setTextColor(playerColors[index]);
            });
            layout.addView(color);
            colors[i] = color;
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setView(layout)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create();

        dialog.setOnShowListener(d ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    for (int i = 0; i < COUNT; i++) {
                        String entered =
                            inputs[i].getText().toString().trim();
                        if (!entered.isEmpty()) names[i] = entered;
                    }

                    savePreferences();
                    refreshTabs();
                    dialog.dismiss();
                })
        );

        dialog.show();
        styleDialog(dialog);
    }

    private void refreshTabs() {
        for (int i = 0; i < COUNT; i++) {
            View tab = tabs.findViewWithTag("tab_" + i);
            if (tab instanceof TextView) {
                ((TextView) tab).setText(names[i]);
                ((TextView) tab).setTextColor(playerColors[i]);
                ((TextView) tab).setBackground(playerTabBackground(i == selected));
            }
        }
    }

    private LinearLayout dialogLayout() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), dp(12), dp(18), dp(12));
        layout.setBackgroundColor(DARK);
        return layout;
    }

    private TextView heading(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(PALE);
        t.setTextSize(19);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(5), 0, dp(12));
        return t;
    }

    private TextView description(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xFFB9B3C9);
        t.setTextSize(12);
        t.setPadding(dp(12), 0, dp(4), dp(10));
        return t;
    }

    private CheckBox check(String text, boolean checked) {
        CheckBox c = new CheckBox(this);
        c.setText(text);
        c.setTextColor(Color.WHITE);
        c.setButtonTintList(ColorStateList.valueOf(PURPLE));
        c.setChecked(checked);
        return c;
    }

    private GradientDrawable playerTabBackground(boolean selectedTab) {
        GradientDrawable bg;
        if (selectedTab) {
            bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] { 0xFF8B5CF6, 0xFF6D28D9 }
            );
            bg.setStroke(dp(1), 0xFFB9A0FF);
        } else {
            bg = new GradientDrawable();
            bg.setColor(0xFF242033);
            bg.setStroke(dp(1), 0xFF403653);
        }
        bg.setCornerRadius(dp(16));
        return bg;
    }

    private GradientDrawable inputBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), PURPLE);
        return bg;
    }

    private TextView button(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(12);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(10), dp(10), dp(10), dp(10));
        t.setMinWidth(dp(90));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF282239);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), PURPLE);
        t.setBackground(bg);
        t.setClickable(true);
        t.setFocusable(true);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            -1, -2
        );
        lp.bottomMargin = dp(4);
        t.setLayoutParams(lp);
        return t;
    }

    private void loadPreferences() {
        keepAwake = prefs.getBoolean("keepAwake", true);

        for (int i = 0; i < COUNT; i++) {
            names[i] = prefs.getString("name" + i, names[i]);
            playerColors[i] = prefs.getInt(
                "color" + i, playerColors[i]
            );
            autoPlay[i] = prefs.getBoolean("autoPlay" + i, false);
            autoBuy[i] = prefs.getBoolean("autoBuy" + i, false);
            fastMode[i] = prefs.getBoolean("fastMode" + i, false);
        }
    }

    private void savePreferences() {
        SharedPreferences.Editor e = prefs.edit();
        e.putBoolean("keepAwake", keepAwake);

        for (int i = 0; i < COUNT; i++) {
            e.putString("name" + i, names[i]);
            e.putInt("color" + i, playerColors[i]);
            e.putBoolean("autoPlay" + i, autoPlay[i]);
            e.putBoolean("autoBuy" + i, autoBuy[i]);
            e.putBoolean("fastMode" + i, fastMode[i]);
        }

        e.apply();
    }

    private void styleDialog(AlertDialog dialog) {
        if (dialog.getWindow() != null) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(DARK);
            bg.setCornerRadius(dp(18));
            dialog.getWindow().setBackgroundDrawable(bg);
        }

        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(PALE);
        }
        if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                .setTextColor(PALE);
        }
    }

    private int dp(int value) {
        return (int) (
            value * getResources().getDisplayMetrics().density + 0.5f
        );
    }

    private void applyImmersive() {
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
        if (hasFocus) applyImmersive();
    }

    @Override
    protected void onResume() {
        super.onResume();

        for (int i = 0; i < COUNT; i++) {
            if (players[i] != null) {
                players[i].onResume();
                players[i].resumeTimers();
                if (autoPlay[i]) installAutoScript(i);
            }
        }
    }

    @Override
    protected void onPause() {
        for (WebView player : players) {
            if (player != null) player.onPause();
        }

        super.onPause();
    }

    @Override
    public void onBackPressed() {
        WebView web = players[selected];

        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        savePreferences();

        for (int i = 0; i < COUNT; i++) {
            WebView web = players[i];

            if (web != null) {
                web.stopLoading();
                web.loadUrl("about:blank");
                web.clearHistory();
                web.removeAllViews();
                web.destroy();
                players[i] = null;
            }
        }

        super.onDestroy();
    }
}
