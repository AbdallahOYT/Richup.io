
package io.richup.wrapper;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.BatteryManager;
import android.graphics.drawable.Drawable;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import android.os.Handler;
import android.os.Looper;
import android.os.Debug;
import android.content.Intent;
import android.content.IntentFilter;
import android.app.ActivityManager;
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

    private static int PURPLE = Color.rgb(139, 92, 246);
    private static final int PALE = Color.rgb(196, 181, 253);
    private static final int DARK = Color.rgb(23, 21, 31);
    private static final int CARD = Color.rgb(36, 32, 51);
    private static final String HOME = "https://richup.io/";
    private static final int COUNT = 4;

    private final WebView[] players = new WebView[COUNT];
    private final boolean[] playerLoaded = new boolean[COUNT];
    private final boolean[] playerVisible = new boolean[] { true, true, true, true };
    private final ArrayList<UserScript> userScripts = new ArrayList<>();

    private static class UserScript {
        String name;
        String code;
        String sourceId;
        boolean enabled;
        String lastStatus = "Disabled";
        final boolean[] playerTargets = new boolean[] { true, true, true, true };

        UserScript(String name, String code, String sourceId, boolean enabled) {
            this.name = name;
            this.code = code;
            this.sourceId = sourceId;
            this.enabled = enabled;
            this.lastStatus = enabled ? "Enabled" : "Disabled";
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
    private final Handler analyticsHandler = new Handler(Looper.getMainLooper());
    private boolean analyticsOpen = false;
    private long lastCpuTimeNs = 0;
    private long lastWallTimeMs = 0;

    private int selected = 0;
    private boolean panelOpen = false;
    private int panelOpacity = 92;
    private int panelWidthDp = 150;
    private int themeAccent = Color.rgb(139, 92, 246);
    private String themeName = "Violet";
    private static final int REQ_IMPORT_SCRIPT = 4101, REQ_EXPORT_SCRIPT = 4102, REQ_BACKUP_EXPORT = 4103, REQ_BACKUP_IMPORT = 4104;
    private int pendingExportScript = -1;
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
        PURPLE = themeAccent;
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
        panelOpen = false;

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
            tab.setVisibility(playerVisible[i] ? View.VISIBLE : View.GONE);
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
                    prefs.edit().putString("roomUrl" + index, url).apply();
                    installAutoScript(index);
                    runUserScripts(index, url);
                }
            }
        });

        browserHost.addView(web, new FrameLayout.LayoutParams(
            -1, -1
        ));

        web.setVisibility(View.GONE);
        // Lazy-load inactive players to reduce startup CPU, RAM and network use.
        buildControlPanel();
    }

    private void selectPlayer(int index) {
        selected = index;
        if (players[index] != null && !playerLoaded[index]) {
            playerLoaded[index] = true;
            String savedRoom = prefs.getString("roomUrl" + index, HOME);
            players[index].loadUrl(savedRoom);
        }

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
                t.setVisibility(playerVisible[i] ? View.VISIBLE : View.GONE);
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
        controls.setPadding(dp(6), dp(7), dp(6), dp(7));
        controls.setElevation(dp(10));

        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] { 0xF52A243A, 0xF0181622 });
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), 0xFF7652D6);
        controls.setBackground(bg);

        TextView home = button("⌂  Home");
        TextView reload = button("↻  Reload");
        TextView awake = button(keepAwake ? "☾  Awake ON" : "☾  Awake OFF");
        autoButton = button("▶  Auto Play");
        TextView join = button("↗  Join Room");
        TextView setup = button("♙  Players");
        TextView scripts = button("⌘  Scripts");
        TextView analytics = button("◈  Analytics");
        TextView sidebar = button("⚙  Layout");
        TextView themes = button("✦  Theme");
        TextView backup = button("⇅  Backup");

        home.setOnClickListener(v -> { stopAuto(selected); players[selected].loadUrl(HOME); });
        reload.setOnClickListener(v -> { stopAuto(selected); players[selected].reload(); });
        awake.setOnClickListener(v -> {
            keepAwake = !keepAwake;
            if (keepAwake) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            awake.setText(keepAwake ? "☾  Awake ON" : "☾  Awake OFF");
            savePreferences();
        });
        autoButton.setOnClickListener(v -> showAutoSettings());
        join.setOnClickListener(v -> showJoinDialog());
        setup.setOnClickListener(v -> showPlayerSettings());
        scripts.setOnClickListener(v -> showScriptsManager());
        analytics.setOnClickListener(v -> showAnalyticsDialog());
        sidebar.setOnClickListener(v -> showSidebarSettings());
        themes.setOnClickListener(v -> showThemeStudio());
        backup.setOnClickListener(v -> showBackupDialog());

        // Short, collapsible categories keep the panel narrow and avoid a wall of text.
        addSidebarCategory(controls, "GAME", "⌂", new View[] { home, reload, awake, autoButton }, true);
        addSidebarCategory(controls, "PLAYERS", "♙", new View[] { join, setup }, false);
        addSidebarCategory(controls, "TOOLS", "✦", new View[] { scripts, analytics, themes, backup, sidebar }, false);
        analytics.setVisibility(prefs.getBoolean("showAnalytics", true) ? View.VISIBLE : View.GONE);
        themes.setVisibility(prefs.getBoolean("showThemes", true) ? View.VISIBLE : View.GONE);
        backup.setVisibility(prefs.getBoolean("showBackup", true) ? View.VISIBLE : View.GONE);
        if (prefs.getBoolean("compactSidebar", true)) {
            for (int i = 0; i < controls.getChildCount(); i++) {
                View child = controls.getChildAt(i);
                if (child instanceof TextView) ((TextView) child).setTextSize(10);
            }
        }

        panel = controls;
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(-2, -2, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        pp.rightMargin = dp(8);
        browserHost.addView(panel, pp);

        panelToggle = button("‹");
        panelToggle.setTextSize(18);
        panelToggle.setMinWidth(dp(34));
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(dp(38), dp(48), Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        tp.rightMargin = dp(8);
        browserHost.addView(panelToggle, tp);
        panel.setVisibility(View.GONE);
        panelToggle.setText("›");
        panelToggle.setOnClickListener(v -> togglePanel());
    }

    private void addSidebarCategory(LinearLayout parent, String title, String symbol, View[] items, boolean expandedInitially) {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        TextView header = button(symbol + "  " + title + (expandedInitially ? "  ▾" : "  ▸"));
        header.setTextSize(10);
        header.setTextColor(PALE);
        header.setPadding(dp(6), dp(8), dp(6), dp(8));
        group.addView(header);
        LinearLayout contents = new LinearLayout(this);
        contents.setOrientation(LinearLayout.VERTICAL);
        contents.setPadding(dp(2), dp(3), dp(2), dp(3));
        for (View item : items) {
            contents.addView(item);
            if (item.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) item.getLayoutParams();
                lp.bottomMargin = dp(3);
                item.setLayoutParams(lp);
            }
        }
        contents.setVisibility(expandedInitially ? View.VISIBLE : View.GONE);
        header.setOnClickListener(v -> {
            boolean open = contents.getVisibility() != View.VISIBLE;
            contents.setVisibility(open ? View.VISIBLE : View.GONE);
            header.setText(symbol + "  " + title + (open ? "  ▾" : "  ▸"));
        });
        group.addView(contents);
        parent.addView(group);
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
                // Userscripts always start OFF after app launch for safety.
                UserScript loaded = new UserScript(name, code, id, false);
                JSONArray targets = obj.optJSONArray("targets"); if (targets != null) for (int t = 0; t < Math.min(COUNT, targets.length()); t++) loaded.playerTargets[t] = targets.optBoolean(t, true);
                userScripts.add(loaded);
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
                JSONArray targets = new JSONArray(); for (boolean target : script.playerTargets) targets.put(target); obj.put("targets", targets);
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

            TextView art = new TextView(this);
            String[] artIcons = {"✦", "◈", "✿", "⚡", "☯", "◆", "❖", "✧"};
            int[] artColors = {0xFF8B5CF6, 0xFF06B6D4, 0xFFEC4899, 0xFFF59E0B, 0xFF22C55E, 0xFF6366F1, 0xFFEF4444, 0xFF14B8A6};
            int artIndex = Math.abs(script.sourceId.hashCode()) % artIcons.length;
            art.setText(artIcons[artIndex]); art.setTextColor(Color.WHITE); art.setTextSize(18); art.setGravity(Gravity.CENTER);
            GradientDrawable artBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{artColors[artIndex], 0xFF29243A}); artBg.setCornerRadius(dp(11)); art.setBackground(artBg);
            card.addView(art, new LinearLayout.LayoutParams(dp(38), dp(38)));
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
            title.setOnClickListener(v -> showEditScriptDialog(index));

            LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.VERTICAL);
            TextView edit = button("Edit"); edit.setTextSize(10); edit.setMinWidth(dp(48)); edit.setPadding(dp(5), dp(4), dp(5), dp(4));
            edit.setOnClickListener(v -> showEditScriptDialog(index));
            TextView export = button("↥"); export.setTextSize(11); export.setMinWidth(dp(48)); export.setPadding(dp(5), dp(4), dp(5), dp(4));
            export.setOnClickListener(v -> { pendingExportScript = index; Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT); intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("text/javascript"); intent.putExtra(Intent.EXTRA_TITLE, safeFileName(userScripts.get(index).name) + ".js"); startActivityForResult(intent, REQ_EXPORT_SCRIPT); });
            TextView delete = button("✕");
            delete.setTextSize(12);
            delete.setMinWidth(dp(38));
            delete.setPadding(dp(8), dp(6), dp(8), dp(6));
            actions.addView(edit); actions.addView(export); actions.addView(delete);
            card.addView(actions, new LinearLayout.LayoutParams(-2, -2));
            enabled.setOnCheckedChangeListener((buttonView, checked) -> {
                userScripts.get(index).enabled = checked;
                userScripts.get(index).lastStatus = checked ? "Enabled" : "Disabled";
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

        TextView add = button("＋ Add script manually");
        add.setOnClickListener(v -> showAddScriptDialog());
        layout.addView(add);
        TextView importFile = button("⇧ Import .js file");
        importFile.setOnClickListener(v -> { Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("*/*"); startActivityForResult(intent, REQ_IMPORT_SCRIPT); });
        layout.addView(importFile);
        TextView backupScripts = button("Export all scripts (JSON)");
        backupScripts.setOnClickListener(v -> { Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT); intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("application/json"); intent.putExtra(Intent.EXTRA_TITLE, "richup-userscripts.json"); startActivityForResult(intent, REQ_BACKUP_EXPORT); });
        layout.addView(backupScripts);

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
            userScripts.add(new UserScript(name, code, id, false));
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
            if (!script.enabled || !script.playerTargets[index] || !scriptMatches(script.code, url)) continue;
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
        layout.addView(heading("Bots & Automation  •  " + names[selected]));
        layout.addView(description("Configure this player. Changes apply only to the selected tab."));

        CheckBox play = check("▶  Auto Play", autoPlay[selected]);
        CheckBox buy = check("◈  Auto Buy", autoBuy[selected]);
        CheckBox fast = check("⚡  Fast Mode", fastMode[selected]);
        addSettingCard(layout, "AUTO PLAY", "Automatically attempts available dice rolls and turn actions.", play);
        addSettingCard(layout, "AUTO BUY", "Attempts to buy properties when a matching button is available.", buy);
        addSettingCard(layout, "FAST MODE", "Shortens the delay between attempts. Very fast automation may be unreliable or restricted by game rules.", fast);

        TextView footer = description("Edited by abood  •  Richup by Bodz");
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(12), 0, dp(2));
        layout.addView(footer);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setView(layout).setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            autoPlay[selected] = play.isChecked();
            autoBuy[selected] = buy.isChecked();
            fastMode[selected] = fast.isChecked();
            savePreferences();
            applyAutoSettings(selected);
            updateAutoLabel();
            dialog.dismiss();
        }));
        dialog.show();
        styleDialog(dialog);
        if (dialog.getWindow() != null) dialog.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels * 0.92f), -2);
    }

    private void addSettingCard(LinearLayout parent, String title, String desc, CheckBox control) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(10), dp(8));
        card.setBackground(cardBackground());
        TextView label = new TextView(this);
        label.setText(title);
        label.setTextColor(PALE);
        label.setTextSize(10);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(label);
        card.addView(control);
        TextView detail = description(desc);
        detail.setPadding(dp(4), 0, dp(4), dp(3));
        card.addView(detail);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        parent.addView(card, lp);
    }

    private GradientDrawable cardBackground() {
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] { 0xFF29243A, 0xFF201C2D });
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), 0xFF403653);
        return bg;
    }

    private void togglePanel() {
        panelOpen = !panelOpen;
        prefs.edit().putBoolean("panelOpen", panelOpen).apply();
        if (panelOpen) {
            panel.setVisibility(View.VISIBLE); panel.setTranslationX(dp(22)); panel.setAlpha(0.35f);
            panel.animate().translationX(0).alpha(1f).setDuration(180).start();
        } else {
            panel.animate().translationX(dp(panelWidthDp)).alpha(0.25f).setDuration(150).withEndAction(() -> panel.setVisibility(View.GONE)).start();
        }
        panelToggle.setText(panelOpen ? "‹" : "›");
        FrameLayout.LayoutParams tp = (FrameLayout.LayoutParams) panelToggle.getLayoutParams();
        tp.rightMargin = panelOpen ? dp(panelWidthDp - 34) : dp(8); panelToggle.setLayoutParams(tp);
    }

    private String safeFileName(String value) { return value.replaceAll("[^A-Za-z0-9._-]", "_"); }

    private void showEditScriptDialog(int index) {
        UserScript script = userScripts.get(index); LinearLayout box = dialogLayout(); box.addView(heading("Edit userscript"));
        EditText name = new EditText(this); name.setSingleLine(true); name.setText(script.name); name.setTextColor(Color.WHITE); name.setBackground(inputBackground()); box.addView(name);
        EditText code = new EditText(this); code.setGravity(Gravity.TOP); code.setTypeface(Typeface.MONOSPACE); code.setTextSize(12); code.setText(script.code); code.setTextColor(Color.WHITE); code.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE); code.setMinLines(8); code.setBackground(inputBackground()); box.addView(code, new LinearLayout.LayoutParams(-1, 0, 1f));
        box.addView(description("Run on which player tabs?")); CheckBox[] targets = new CheckBox[COUNT];
        for (int i=0;i<COUNT;i++){ targets[i]=check(names[i],script.playerTargets[i]); box.addView(targets[i]); }
        AlertDialog d = new AlertDialog.Builder(this).setView(box).setPositiveButton("Save",null).setNegativeButton("Cancel",null).create();
        d.setOnShowListener(v -> d.getButton(-1).setOnClickListener(w -> { String n=name.getText().toString().trim(); if(!n.isEmpty())script.name=n; script.code=code.getText().toString(); for(int i=0;i<COUNT;i++)script.playerTargets[i]=targets[i].isChecked(); saveUserScripts(); d.dismiss(); showScriptsManager(); }));
        d.show(); styleDialog(d); if(d.getWindow()!=null)d.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*.94f),(int)(getResources().getDisplayMetrics().heightPixels*.86f));
    }

    private void showSidebarSettings() {
        LinearLayout box=dialogLayout(); box.addView(heading("Sidebar & Layout"));
        box.addView(description("Keep only the controls you use. Changes are saved automatically."));
        SeekBar opacity=new SeekBar(this); opacity.setMax(100); opacity.setProgress(panelOpacity); box.addView(description("Sidebar opacity: " + panelOpacity + "%")); box.addView(opacity);
        SeekBar width=new SeekBar(this); width.setMax(120); width.setProgress(Math.max(30,panelWidthDp)-30); box.addView(description("Panel width: " + panelWidthDp + " dp")); box.addView(width);
        String[] features={"Home","Reload","Awake","Auto Play","Join Room","Players","Scripts","Analytics","Theme Studio","Backup / Restore"};
        box.addView(description("Feature visibility is available here; the core navigation controls remain accessible."));
        CheckBox compact=check("Compact sidebar buttons",prefs.getBoolean("compactSidebar",false)); box.addView(compact);
        CheckBox showAnalytics=check("Show Analytics button",prefs.getBoolean("showAnalytics",true)); box.addView(showAnalytics);
        CheckBox showThemes=check("Show Theme Studio button",prefs.getBoolean("showThemes",true)); box.addView(showThemes);
        CheckBox showBackup=check("Show Backup / Restore button",prefs.getBoolean("showBackup",true)); box.addView(showBackup);
        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int v,boolean u){panelOpacity=Math.max(35,v); if(panel!=null)panel.setAlpha(panelOpen?panelOpacity/100f:0.25f);} public void onStartTrackingTouch(SeekBar b){} public void onStopTrackingTouch(SeekBar b){}});
        width.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int v,boolean u){panelWidthDp=v+30;} public void onStartTrackingTouch(SeekBar b){} public void onStopTrackingTouch(SeekBar b){}});
        AlertDialog d=new AlertDialog.Builder(this).setView(box).setPositiveButton("Save",null).setNegativeButton("Cancel",null).create(); d.setOnShowListener(v->d.getButton(-1).setOnClickListener(w->{prefs.edit().putBoolean("compactSidebar",compact.isChecked()).putBoolean("showAnalytics",showAnalytics.isChecked()).putBoolean("showThemes",showThemes.isChecked()).putBoolean("showBackup",showBackup.isChecked()).apply(); savePreferences(); if(panel!=null){panel.setAlpha(panelOpacity/100f); panel.setVisibility(panelOpen?View.VISIBLE:View.GONE);} d.dismiss(); Toast.makeText(this,"Sidebar settings saved. Reopen app to apply button visibility.",Toast.LENGTH_SHORT).show();})); d.show(); styleDialog(d);
    }

    private void showThemeStudio() {
        final String[] labels = {"Violet", "Blue", "Red", "Mint", "Amber", "Pink"};
        final int[] colors = {
            Color.rgb(139,92,246), Color.rgb(59,130,246), Color.rgb(239,68,68),
            Color.rgb(16,185,129), Color.rgb(245,158,11), Color.rgb(236,72,153)
        };
        final int[] selected = {0};
        for (int i = 0; i < colors.length; i++) if (colors[i] == themeAccent) selected[0] = i;

        LinearLayout box = dialogLayout();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(2), 0, dp(8));

        TextView title = heading("THEME STUDIO");
        title.setTextSize(18);
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        title.setPadding(dp(2), dp(4), 0, dp(3));
        content.addView(title);
        TextView subtitle = description("Make Richup yours. Choose an accent and preview it before applying.");
        subtitle.setPadding(dp(2), 0, dp(2), dp(14));
        content.addView(subtitle);

        LinearLayout preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.VERTICAL);
        preview.setPadding(dp(15), dp(13), dp(15), dp(13));
        GradientDrawable previewBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[] {0xFF302644, 0xFF211C30});
        previewBg.setCornerRadius(dp(18));
        previewBg.setStroke(dp(1), colors[selected[0]]);
        preview.setBackground(previewBg);
        TextView previewLabel = new TextView(this);
        previewLabel.setText("LIVE PREVIEW");
        previewLabel.setTextColor(0xFFBDB4D1);
        previewLabel.setTextSize(10);
        previewLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        preview.addView(previewLabel);
        TextView previewTitle = new TextView(this);
        previewTitle.setText("Richup by Bodz");
        previewTitle.setTextColor(Color.WHITE);
        previewTitle.setTextSize(18);
        previewTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        previewTitle.setPadding(0, dp(5), 0, dp(9));
        preview.addView(previewTitle);
        TextView previewButton = new TextView(this);
        previewButton.setText("  ●  " + labels[selected[0]] + " theme     ✓ Selected  ");
        previewButton.setTextColor(Color.WHITE);
        previewButton.setTextSize(12);
        previewButton.setGravity(Gravity.CENTER);
        previewButton.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable buttonBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            new int[] {colors[selected[0]], darkenThemeColor(colors[selected[0]])});
        buttonBg.setCornerRadius(dp(13));
        previewButton.setBackground(buttonBg);
        preview.addView(previewButton);
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(-1, -2);
        previewLp.bottomMargin = dp(17);
        content.addView(preview, previewLp);

        TextView paletteTitle = heading("ACCENT COLOR");
        paletteTitle.setTextSize(13);
        paletteTitle.setGravity(Gravity.START);
        paletteTitle.setPadding(dp(2), dp(1), 0, dp(8));
        content.addView(paletteTitle);
        LinearLayout palette = new LinearLayout(this);
        palette.setOrientation(LinearLayout.VERTICAL);
        final EditText[] hexRef = new EditText[1];
        final TextView[][] swatches = new TextView[2][3];
        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            int active = colors[selected[0]];
            for (int r = 0; r < 2; r++) for (int c = 0; c < 3; c++) {
                int i = r * 3 + c;
                TextView item = swatches[r][c];
                if (item == null) continue;
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(0xFF282239);
                bg.setCornerRadius(dp(13));
                bg.setStroke(dp(i == selected[0] ? 2 : 1), i == selected[0] ? colors[i] : 0xFF494158);
                item.setBackground(bg);
                item.setText((i == selected[0] ? "✓  " : "   ") + labels[i]);
                item.setTextColor(i == selected[0] ? colors[i] : 0xFFE5E0EE);
            }
            previewBg.setStroke(dp(1), active);
            buttonBg.setColors(new int[] {active, darkenThemeColor(active)});
            previewButton.setText("  ●  " + labels[selected[0]] + " theme     ✓ Selected  ");
            previewButton.setTextColor(Color.WHITE);
        };
        for (int r = 0; r < 2; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < 3; c++) {
                final int i = r * 3 + c;
                TextView swatch = new TextView(this);
                swatch.setTextSize(12);
                swatch.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                swatch.setGravity(Gravity.CENTER);
                swatch.setPadding(dp(3), dp(14), dp(3), dp(14));
                swatches[r][c] = swatch;
                swatch.setOnClickListener(v -> { selected[0] = i; if (hexRef[0] != null) hexRef[0].setText(""); refresh[0].run(); });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
                lp.setMargins(dp(3), dp(3), dp(3), dp(3));
                row.addView(swatch, lp);
            }
            palette.addView(row);
        }
        content.addView(palette);

        TextView customTitle = heading("CUSTOM COLOR");
        customTitle.setTextSize(13);
        customTitle.setGravity(Gravity.START);
        customTitle.setPadding(dp(2), dp(14), 0, dp(5));
        content.addView(customTitle);
        EditText hex = new EditText(this);
        hexRef[0] = hex;
        hex.setSingleLine(true);
        hex.setTextColor(Color.WHITE);
        hex.setHintTextColor(0xFF8E879D);
        hex.setHint("Hex code, e.g. #8B5CF6");
        hex.setTextSize(14);
        hex.setPadding(dp(13), dp(11), dp(13), dp(11));
        hex.setBackground(inputBackground());
        content.addView(hex, new LinearLayout.LayoutParams(-1, -2));
        content.addView(description("Custom colors are saved with your theme. Use #RRGGBB format."));
        TextView footer = description("Edited by abood  •  Richup by Bodz");
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(8), 0, dp(2));
        content.addView(footer);

        hex.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int count, int after) {}
            public void onTextChanged(CharSequence s, int st, int before, int count) {
                String value = s.toString().trim();
                if (value.isEmpty()) { refresh[0].run(); return; }
                try {
                    int custom = Color.parseColor(value);
                    previewBg.setStroke(dp(1), custom);
                    buttonBg.setColors(new int[] {custom, darkenThemeColor(custom)});
                    previewButton.setText("  ●  Custom theme     ✓ Preview  ");
                } catch (Exception ignored) { }
            }
            public void afterTextChanged(android.text.Editable e) {}
        });
        refresh[0].run();
        scroll.addView(content);
        box.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        AlertDialog d = new AlertDialog.Builder(this).setView(box)
            .setPositiveButton("Apply theme", null).setNegativeButton("Cancel", null).create();
        d.setOnShowListener(v -> {
            d.getButton(-1).setOnClickListener(w -> {
                int accent = colors[selected[0]];
                String name = labels[selected[0]];
                String value = hex.getText().toString().trim();
                if (!value.isEmpty()) {
                    try { accent = Color.parseColor(value); name = "Custom"; }
                    catch (Exception ex) { hex.setError("Enter a valid hex color, e.g. #8B5CF6"); return; }
                }
                themeAccent = accent;
                themeName = name;
                PURPLE = themeAccent;
                prefs.edit().putInt("themeAccent", themeAccent).putString("themeName", themeName).apply();
                savePreferences();
                d.dismiss();
                Toast.makeText(this, "Theme saved. Restart to refresh all existing controls.", Toast.LENGTH_SHORT).show();
            });
        });
        d.show();
        styleDialog(d);
        if (d.getWindow() != null) d.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels * .92f),
            (int)(getResources().getDisplayMetrics().heightPixels * .86f));
    }

    private int darkenThemeColor(int color) {
        return Color.rgb((int)(Color.red(color) * .78f), (int)(Color.green(color) * .78f), (int)(Color.blue(color) * .78f));
    }

    private void showBackupDialog() {
        new AlertDialog.Builder(this).setTitle("Backup & Restore").setMessage("Export all settings and userscripts to a JSON file, or restore a previous backup. Keep a backup before changing scripts or themes.")
            .setPositiveButton("Export backup",(d,w)->{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"richup-bodz-backup.json");startActivityForResult(i,REQ_BACKUP_EXPORT);})
            .setNegativeButton("Restore backup",(d,w)->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");startActivityForResult(i,REQ_BACKUP_IMPORT);})
            .setNeutralButton("Cancel",null).show();
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data); if(resultCode!=RESULT_OK||data==null||data.getData()==null)return; Uri uri=data.getData();
        try {
            if(requestCode==REQ_IMPORT_SCRIPT){String code=readUri(uri);String name=extractScriptName(code,"Imported script "+(userScripts.size()+1));userScripts.add(new UserScript(name,code,"import:"+System.currentTimeMillis(),false));saveUserScripts();showScriptsManager();Toast.makeText(this,"Script imported (OFF by default)",Toast.LENGTH_SHORT).show();}
            else if(requestCode==REQ_EXPORT_SCRIPT && pendingExportScript>=0 && pendingExportScript<userScripts.size()){writeUri(uri,userScripts.get(pendingExportScript).code);Toast.makeText(this,"Script exported",Toast.LENGTH_SHORT).show();}
            else if(requestCode==REQ_BACKUP_EXPORT){JSONObject root=new JSONObject();root.put("version",2);JSONObject settings=new JSONObject();for(java.util.Map.Entry<String,?> entry:prefs.getAll().entrySet())settings.put(entry.getKey(),entry.getValue());root.put("preferences",settings);root.put("scripts",new JSONArray(prefs.getString("userScripts","[]")));writeUri(uri,root.toString(2));Toast.makeText(this,"Backup exported",Toast.LENGTH_SHORT).show();}
            else if(requestCode==REQ_BACKUP_IMPORT){JSONObject root=new JSONObject(readUri(uri));JSONArray arr=root.optJSONArray("scripts");SharedPreferences.Editor edit=prefs.edit();if(arr!=null)edit.putString("userScripts",arr.toString());JSONObject settings=root.optJSONObject("preferences");if(settings!=null){java.util.Iterator<String> keys=settings.keys();while(keys.hasNext()){String key=keys.next();Object value=settings.opt(key);if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else if(value instanceof Integer)edit.putInt(key,(Integer)value);else if(value instanceof Long)edit.putLong(key,(Long)value);else if(value instanceof String)edit.putString(key,(String)value);else if(value instanceof Double)edit.putFloat(key,((Double)value).floatValue());}}edit.apply();loadUserScripts();loadPreferences();Toast.makeText(this,"Backup restored. Restart the app to apply all settings.",Toast.LENGTH_LONG).show();}
        } catch(Exception e){Toast.makeText(this,"File operation failed: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private String readUri(Uri uri) throws Exception {StringBuilder b=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri),StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null)b.append(line).append('\n');}return b.toString();}
    private void writeUri(Uri uri,String value) throws Exception {try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new Exception("Cannot open file");out.write(value.getBytes(StandardCharsets.UTF_8));}}

    private void showAnalyticsDialog() {
        analyticsOpen = true;
        LinearLayout layout = dialogLayout();
        layout.addView(heading("Analytics & Game Booster"));
        layout.addView(description("Live estimates for this app and the selected Richup tab."));

        TextView[] values = new TextView[6];
        String[] labels = { "APP CPU", "WEBVIEW FPS", "FRAME / LAG", "BATTERY", "BATTERY TEMP", "APP MEMORY" };
        String[] initial = { "Measuring…", "Measuring…", "Measuring…", "Reading…", "Reading…", "Reading…" };
        for (int i = 0; i < labels.length; i++) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(13), dp(10), dp(13), dp(10));
            card.setBackground(cardBackground());
            TextView label = new TextView(this);
            label.setText(labels[i]); label.setTextColor(0xFFB8AECF); label.setTextSize(10);
            label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            TextView value = new TextView(this);
            value.setText(initial[i]); value.setTextColor(PALE); value.setTextSize(21);
            value.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            card.addView(label); card.addView(value); values[i] = value;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(7); layout.addView(card, lp);
        }
        TextView note = description("FPS and frame timing are measured inside the webpage. Android does not expose reliable device-wide CPU temperature or GPU metrics to ordinary apps, so these are app-level estimates where available.");
        layout.addView(note);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(layout).setPositiveButton("Close", (d, w) -> analyticsOpen = false).create();
        dialog.setOnCancelListener(d -> analyticsOpen = false);
        dialog.setOnDismissListener(d -> analyticsOpen = false);
        dialog.show(); styleDialog(dialog);
        if (dialog.getWindow() != null) dialog.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels * 0.90f), (int)(getResources().getDisplayMetrics().heightPixels * 0.84f));
        lastCpuTimeNs = Debug.threadCpuTimeNanos(); lastWallTimeMs = android.os.SystemClock.elapsedRealtime();
        Runnable updater = new Runnable() {
            @Override public void run() {
                if (!analyticsOpen || isFinishing()) return;
                try {
                    long cpuNow = Debug.threadCpuTimeNanos();
                    long wallNow = android.os.SystemClock.elapsedRealtime();
                    long cpuDelta = Math.max(0, cpuNow - lastCpuTimeNs);
                    long wallDelta = Math.max(1, wallNow - lastWallTimeMs) * 1000000L;
                    double cpu = Math.min(100.0, 100.0 * cpuDelta / wallDelta);
                    lastCpuTimeNs = cpuNow; lastWallTimeMs = wallNow;
                    values[0].setText(String.format(java.util.Locale.US, "%.1f%% main thread", cpu));
                    ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                    ActivityManager am = (ActivityManager)getSystemService(ACTIVITY_SERVICE);
                    if (am != null) { am.getMemoryInfo(mi); android.os.Debug.MemoryInfo mem = new android.os.Debug.MemoryInfo(); Debug.getMemoryInfo(mem); values[5].setText(String.format(java.util.Locale.US, "%.0f MB PSS", mem.getTotalPss() / 1024.0)); }
                    Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                    if (battery != null) {
                        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                        if (level >= 0 && scale > 0) values[3].setText((100 * level / scale) + "%");
                        int temp = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                        values[4].setText(temp >= 0 ? String.format(java.util.Locale.US, "%.1f°C", temp / 10.0) : "Unavailable");
                    }
                    WebView current = players[selected];
                    if (current != null) current.evaluateJavascript("(function(){if(!window.__bodzFps){window.__bodzFps={n:0,last:performance.now(),fps:0,frame:0};(function tick(t){var s=window.__bodzFps;s.n++;if(t-s.last>=1000){s.fps=Math.round(s.n*1000/(t-s.last));s.frame=Math.round((t-s.last)/Math.max(1,s.n));s.n=0;s.last=t;}requestAnimationFrame(tick)})(performance.now());}return JSON.stringify(window.__bodzFps)})()", result -> {
                        try { JSONObject o = new JSONObject(result == null ? "{}" : result.replaceAll("^\"|\"$", "")); int fps = o.optInt("fps", 0); int frame = o.optInt("frame", 0); values[1].setText(fps > 0 ? fps + " FPS" : "Measuring…"); values[2].setText(frame > 0 ? frame + " ms / frame" : "Measuring…"); } catch (Exception ignored) { values[1].setText("Unavailable"); values[2].setText("Unavailable"); }
                    });
                } catch (Exception ignored) { }
                analyticsHandler.postDelayed(this, 1500);
            }
        };
        analyticsHandler.post(updater);
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
        LinearLayout content = dialogLayout();
        content.addView(heading("Players"));
        content.addView(description("Personalize each tab. Choose its name, accent color, and visibility."));

        EditText[] inputs = new EditText[COUNT];
        CheckBox[] visibility = new CheckBox[COUNT];
        TextView[] colorButtons = new TextView[COUNT];
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.VERTICAL);
        cards.setPadding(0, dp(2), 0, dp(4));

        for (int i = 0; i < COUNT; i++) {
            final int index = i;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(13), dp(11), dp(13), dp(10));
            card.setBackground(cardBackground());
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
            cardLp.bottomMargin = dp(9);
            cards.addView(card, cardLp);

            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            TextView dot = new TextView(this);
            dot.setText("●"); dot.setTextColor(playerColors[i]); dot.setTextSize(19);
            dot.setPadding(0, 0, dp(9), 0);
            top.addView(dot);
            TextView label = new TextView(this);
            label.setText(i == 0 ? "MAIN PLAYER" : "PLAYER " + i);
            label.setTextColor(PALE); label.setTextSize(10);
            label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            top.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView status = new TextView(this);
            status.setText(i == selected ? "ACTIVE" : (playerVisible[i] ? "VISIBLE" : "HIDDEN"));
            status.setTextColor(i == selected ? 0xFFBDA4FF : 0xFFAAA3B9);
            status.setTextSize(9); status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            top.addView(status);
            card.addView(top);

            EditText input = new EditText(this);
            input.setSingleLine(true); input.setText(names[i]); input.setHint("Player name");
            input.setTextColor(Color.WHITE); input.setHintTextColor(0xFF9B95A8);
            input.setTextSize(14); input.setPadding(dp(11), dp(7), dp(11), dp(7));
            input.setBackground(inputBackground());
            LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(-1, -2);
            inputLp.topMargin = dp(9); card.addView(input, inputLp); inputs[i] = input;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(4), 0, 0);
            CheckBox shown = check("Show tab", playerVisible[i]);
            if (i == 0) { shown.setChecked(true); shown.setEnabled(false); }
            row.addView(shown, new LinearLayout.LayoutParams(0, -2, 1f)); visibility[i] = shown;
            TextView color = button("●  COLOR");
            color.setTextColor(playerColors[i]); color.setTextSize(10);
            color.setPadding(dp(12), dp(7), dp(12), dp(7));
            color.setOnClickListener(v -> {
                int current = 0;
                for (int j = 0; j < colorChoices.length; j++) if (colorChoices[j] == playerColors[index]) { current = j; break; }
                playerColors[index] = colorChoices[(current + 1) % colorChoices.length];
                color.setTextColor(playerColors[index]); dot.setTextColor(playerColors[index]);
            });
            row.addView(color); colorButtons[i] = color;
            card.addView(row);
        }
        scroll.addView(cards);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        AlertDialog dialog = new AlertDialog.Builder(this).setView(content)
            .setPositiveButton("Save changes", null).setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            for (int i = 0; i < COUNT; i++) {
                String entered = inputs[i].getText().toString().trim();
                if (!entered.isEmpty()) names[i] = entered;
                playerVisible[i] = (i == 0) || visibility[i].isChecked();
            }
            if (!playerVisible[selected]) selected = 0;
            savePreferences(); refreshTabs(); selectPlayer(selected); dialog.dismiss();
        }));
        dialog.show();
        styleDialog(dialog);
        if (dialog.getWindow() != null) dialog.getWindow().setLayout(
            (int)(getResources().getDisplayMetrics().widthPixels * 0.92f),
            (int)(getResources().getDisplayMetrics().heightPixels * 0.82f));
    }

    private void refreshTabs() {
        for (int i = 0; i < COUNT; i++) {
            View tab = tabs.findViewWithTag("tab_" + i);
            if (tab instanceof TextView) {
                ((TextView) tab).setText(names[i]);
                ((TextView) tab).setTextColor(playerColors[i]);
                ((TextView) tab).setBackground(playerTabBackground(i == selected));
                tab.setVisibility(playerVisible[i] ? View.VISIBLE : View.GONE);
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
        panelOpen = false;
        panelOpacity = prefs.getInt("panelOpacity", 92);
        panelWidthDp = prefs.getInt("panelWidthDp", 150);
        themeAccent = prefs.getInt("themeAccent", Color.rgb(139, 92, 246));
        themeName = prefs.getString("themeName", "Violet");

        for (int i = 0; i < COUNT; i++) {
            names[i] = prefs.getString("name" + i, names[i]);
            playerVisible[i] = i == 0 || prefs.getBoolean("visible" + i, true);
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
        e.putBoolean("panelOpen", panelOpen); e.putInt("panelOpacity", panelOpacity); e.putInt("panelWidthDp", panelWidthDp); e.putInt("themeAccent", themeAccent); e.putString("themeName", themeName);

        for (int i = 0; i < COUNT; i++) {
            e.putString("name" + i, names[i]);
            e.putBoolean("visible" + i, playerVisible[i]);
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
