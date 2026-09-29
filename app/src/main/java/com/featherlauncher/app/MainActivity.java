package com.featherlauncher.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Feather Launcher home screen.
 *
 * Design goals:
 *  - Zero permissions, zero services, zero receivers: nothing runs in the
 *    background and nothing can be tracked. The clock ticker only runs while
 *    the home screen is visible.
 *  - Everything is built programmatically (no layout XML) and app icons are
 *    pre-scaled on a background thread, so UI stays at 60fps even on 1GB RAM
 *    devices like the vivo Y91i.
 */
public class MainActivity extends Activity {

    private static final String DEFAULT_DOCK = "dial\nsms\ncamera\nbrowser";

    private SharedPreferences prefs;
    private GridView grid;
    private AppAdapter adapter;
    private EditText searchBox;
    private TextView clockText;
    private TextView batteryText;
    private TextView emptyText;
    private ProgressBar loadingBar;
    private LinearLayout dockRow;
    private View warmOverlay;

    private final List<AppEntry> allApps = new ArrayList<>();
    private final List<AppEntry> shownApps = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private String prefsSnapshot = "";
    private float density;

    private static final class AppEntry {
        String label;
        String pkg;
        Bitmap icon;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("feather", MODE_PRIVATE);
        density = getResources().getDisplayMetrics().density;

        Ui.applyMonochrome(this, prefs.getBoolean("mono", false));
        Ui.applyFullscreen(this, prefs.getBoolean("hideStatus", false));
        Ui.tintSystemBars(this, theme());
        setContentView(buildUi());

        adapter = new AppAdapter();
        grid.setAdapter(adapter);
        loadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        String snap = snapshot();
        if (!snap.equals(prefsSnapshot)) {
            if (!prefsSnapshot.isEmpty()) {
                recreate();
                return;
            }
        }
        prefsSnapshot = snap;
        loadApps();
        startTicker();
    }

    @Override
    protected void onPause() {
        stopTicker();
        super.onPause();
    }

    /** Launcher must never close on back - home stays home. */
    @Override
    public void onBackPressed() {
        if (searchBox != null && searchBox.getText().length() > 0) {
            searchBox.setText("");
        }
        // otherwise: do nothing, stay on home
    }

    // ------------------------------------------------------------------ UI

    private int theme() { return prefs.getInt("theme", Ui.DEFAULT_THEME); }
    private int columns() { return prefs.getInt("columns", 4); }
    private int iconDp() { return prefs.getInt("iconDp", 52); }
    private boolean showLabels() { return prefs.getBoolean("labels", true); }
    private int warmAlpha() { return Math.min(160, prefs.getInt("warm", 0) * 2); }

    private View buildUi() {
        int bg = Ui.bg(theme());
        int card = Ui.card(theme());
        int text = Ui.text(theme());
        int sub = Ui.sub(theme());
        int accent = Ui.accent(theme());

        // Main vertical container
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);

        // ---- custom status strip (clock / battery / settings)
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        int p8 = dp(8);
        strip.setPadding(p8, p8, p8, p8);
        strip.setBackground(Ui.rounded(card, 18, density, 150));

        clockText = makeText("", text, 14, true);
        LinearLayout.LayoutParams lpClock = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        strip.addView(clockText, lpClock);

        batteryText = makeText("Battery --%", sub, 12, false);
        strip.addView(batteryText);

        TextView settingsBtn = makeText("\u2699", text, 18, false);
        settingsBtn.setPadding(dp(12), 0, dp(4), 0);
        settingsBtn.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, SettingsActivity.class)));
        strip.addView(settingsBtn);

        LinearLayout.LayoutParams lpStrip = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpStrip.setMargins(dp(8), dp(8), dp(8), dp(4));
        main.addView(strip, lpStrip);

        // ---- search bar
        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setBackground(Ui.rounded(card, 20, density, 210));
        searchRow.setPadding(dp(14), 0, dp(6), 0);

        TextView magnifier = makeText("\uD83D\uDD0D", text, 14, false);
        searchRow.addView(magnifier);

        searchBox = new EditText(this);
        searchBox.setHint("Search apps");
        searchBox.setHintTextColor(sub);
        searchBox.setTextColor(text);
        searchBox.setTextSize(14);
        searchBox.setSingleLine(true);
        searchBox.setBackground(null);
        LinearLayout.LayoutParams lpSearch = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        searchRow.addView(searchBox, lpSearch);

        TextView clearBtn = makeText("\u2715", sub, 14, false);
        clearBtn.setPadding(dp(10), dp(6), dp(10), dp(6));
        clearBtn.setOnClickListener(v -> searchBox.setText(""));
        searchRow.addView(clearBtn);

        LinearLayout.LayoutParams lpRow = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpRow.setMargins(dp(8), dp(4), dp(8), dp(4));
        main.addView(searchRow, lpRow);

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { filter(s.toString()); }
        });

        // ---- app grid container
        FrameLayout gridContainer = new FrameLayout(this);
        LinearLayout.LayoutParams lpGrid = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        main.addView(gridContainer, lpGrid);

        loadingBar = new ProgressBar(this);
        FrameLayout.LayoutParams lpBar = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        gridContainer.addView(loadingBar, lpBar);

        emptyText = makeText("No apps found", sub, 14, false);
        emptyText.setVisibility(View.GONE);
        FrameLayout.LayoutParams lpEmpty = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        gridContainer.addView(emptyText, lpEmpty);

        grid = new GridView(this);
        grid.setNumColumns(columns());
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalSpacing(dp(10));
        grid.setHorizontalSpacing(dp(4));
        grid.setPadding(dp(6), dp(6), dp(6), dp(6));
        grid.setClipToPadding(false);
        grid.setSelector(new android.graphics.drawable.ColorDrawable(0x33FFFFFF));
        gridContainer.addView(grid, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        grid.setOnItemClickListener((parent, view, pos, id) -> {
            if (pos >= 0 && pos < shownApps.size()) launch(shownApps.get(pos));
        });
        grid.setOnItemLongClickListener((parent, view, pos, id) -> {
            if (pos >= 0 && pos < shownApps.size()) {
                showAppOptions(shownApps.get(pos));
                return true;
            }
            return false;
        });

        // ---- dock
        dockRow = new LinearLayout(this);
        dockRow.setOrientation(LinearLayout.HORIZONTAL);
        dockRow.setGravity(Gravity.CENTER);
        int p12 = dp(12);
        dockRow.setPadding(p8, p8, p8, p8);
        dockRow.setBackground(Ui.rounded(card, 22, density, 160));
        LinearLayout.LayoutParams lpDock = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpDock.setMargins(dp(8), dp(4), dp(8), dp(8));
        main.addView(dockRow, lpDock);
        buildDock();

        // ---- root with warm eye-comfort overlay on top
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0x00000000); // transparent: shows wallpaper
        root.addView(main);

        warmOverlay = new View(this);
        warmOverlay.setBackgroundColor(0x00000000);
        int wa = warmAlpha();
        if (wa > 0) {
            warmOverlay.setBackgroundColor((0xFF9E6A00 & 0x00FFFFFF) | ((wa & 0xFF) << 24));
        }
        root.addView(warmOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return root;
    }

    private TextView makeText(String s, int color, int sizeSp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(sizeSp);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    private int dp(int v) { return (int) (v * density + 0.5f); }

    // ---------------------------------------------------------------- dock

    private void buildDock() {
        dockRow.removeAllViews();
        String[] items = prefs.getString("dock", DEFAULT_DOCK).split("\n");
        for (String code : items) {
            if (code.trim().isEmpty()) continue;
            dockRow.addView(makeDockItem(code.trim()));
        }
    }

    private View makeDockItem(String code) {
        int size = dp(52);
        FrameLayout item = new FrameLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMargins(dp(6), 0, dp(6), 0);
        item.setLayoutParams(lp);
        item.setBackground(Ui.circle(0xFFFFFF, 40));

        if (isPseudo(code)) {
            TextView emoji = makeText(pseudoEmoji(code), 0xFFFFFFFF, 20, false);
            FrameLayout.LayoutParams lpT = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
            item.addView(emoji, lpT);
        } else {
            try {
                Drawable d = getPackageManager().getApplicationIcon(code);
                int px = dp(30);
                ImageView iv = new ImageView(this);
                iv.setImageBitmap(scaleDrawable(d, px));
                FrameLayout.LayoutParams lpI = new FrameLayout.LayoutParams(px, px, Gravity.CENTER);
                item.addView(iv, lpI);
            } catch (Exception e) {
                item.setVisibility(View.GONE);
            }
        }

        item.setOnClickListener(v -> openDockItem(code));
        item.setOnLongClickListener(v -> {
            if (!isPseudo(code)) confirmUnpin(code);
            return true;
        });
        return item;
    }

    private boolean isPseudo(String code) {
        return "dial".equals(code) || "sms".equals(code)
                || "camera".equals(code) || "browser".equals(code);
    }

    private String pseudoEmoji(String code) {
        if ("dial".equals(code)) return "\uD83D\uDCDE";
        if ("sms".equals(code)) return "\uD83D\uDCAC";
        if ("camera".equals(code)) return "\uD83D\uDCF7";
        return "\uD83C\uDF10"; // browser
    }

    private void openDockItem(String code) {
        Intent intent = null;
        if ("dial".equals(code)) {
            intent = new Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:"));
        } else if ("sms".equals(code)) {
            intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("sms:"));
        } else if ("camera".equals(code)) {
            intent = new Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
        } else if ("browser".equals(code)) {
            intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://duckduckgo.com"));
        } else {
            intent = getPackageManager().getLaunchIntentForPackage(code);
            if (intent == null) {
                unpin(code);
                Toast.makeText(this, "App removed - unpinned from dock", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app found for this action", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmUnpin(final String pkg) {
        new AlertDialog.Builder(this)
                .setMessage("Remove this app from the dock?")
                .setPositiveButton("Remove", (d, w) -> unpin(pkg))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void unpin(String pkg) {
        List<String> items = new ArrayList<>();
        for (String s : prefs.getString("dock", DEFAULT_DOCK).split("\n")) {
            if (!s.equals(pkg) && !s.trim().isEmpty()) items.add(s);
        }
        prefs.edit().putString("dock", join(items)).apply();
        buildDock();
    }

    private String join(List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(items.get(i));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------- app list

    private void loadApps() {
        executor.execute(() -> {
            final List<AppEntry> list = new ArrayList<>();
            PackageManager pm = getPackageManager();
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> infos = pm.queryIntentActivities(intent, 0);
            final int iconPx = dp(iconDp());
            for (ResolveInfo ri : infos) {
                AppEntry e = new AppEntry();
                CharSequence label = ri.loadLabel(pm);
                e.label = label != null ? label.toString() : ri.activityInfo.packageName;
                e.pkg = ri.activityInfo.packageName;
                try {
                    e.icon = scaleDrawable(ri.loadIcon(pm), iconPx);
                } catch (Exception ex) {
                    continue;
                }
                list.add(e);
            }
            Collections.sort(list, (a, b) -> Collator.getInstance().compare(a.label, b.label));
            runOnUiThread(() -> {
                allApps.clear();
                allApps.addAll(list);
                if (loadingBar != null) loadingBar.setVisibility(View.GONE);
                filter(searchBox.getText().toString());
            });
        });
    }

    private Bitmap scaleDrawable(Drawable d, int px) {
        Bitmap bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        d.setBounds(0, 0, px, px);
        d.draw(canvas);
        return bmp;
    }

    private void filter(String query) {
        shownApps.clear();
        String q = query.trim().toLowerCase(Locale.getDefault());
        if (q.isEmpty()) {
            shownApps.addAll(allApps);
        } else {
            for (AppEntry e : allApps) {
                if (e.label.toLowerCase(Locale.getDefault()).contains(q)) shownApps.add(e);
            }
        }
        boolean empty = shownApps.isEmpty() && !allApps.isEmpty();
        if (emptyText != null) emptyText.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void launch(AppEntry e) {
        Intent i = getPackageManager().getLaunchIntentForPackage(e.pkg);
        if (i != null) {
            startActivity(i);
        } else {
            Toast.makeText(this, "Cannot open " + e.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void showAppOptions(final AppEntry e) {
        String[] opts = {"App info", "Uninstall", "Pin to dock"};
        new AlertDialog.Builder(this)
                .setTitle(e.label)
                .setItems(opts, (DialogInterface d, int which) -> {
                    if (which == 0) {
                        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.parse("package:" + e.pkg)));
                    } else if (which == 1) {
                        startActivity(new Intent(Intent.ACTION_DELETE,
                                android.net.Uri.parse("package:" + e.pkg)));
                    } else {
                        pinToDock(e.pkg);
                    }
                })
                .show();
    }

    private void pinToDock(String pkg) {
        String cur = prefs.getString("dock", DEFAULT_DOCK);
        for (String s : cur.split("\n")) {
            if (s.equals(pkg)) {
                Toast.makeText(this, "Already on the dock", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        if (cur.split("\n").length >= 6) {
            Toast.makeText(this, "Dock is full (6 max). Long-press to remove one.", Toast.LENGTH_LONG).show();
            return;
        }
        prefs.edit().putString("dock", cur + "\n" + pkg).apply();
        buildDock();
        Toast.makeText(this, "Pinned to dock", Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------ clock / battery

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateClockBattery();
            handler.postDelayed(this, 1000);
        }
    };

    private void startTicker() {
        updateClockBattery();
        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker, 1000);
    }

    private void stopTicker() {
        handler.removeCallbacks(ticker);
    }

    private void updateClockBattery() {
        boolean h24 = prefs.getBoolean("clock24", true);
        SimpleDateFormat fmt = new SimpleDateFormat(h24 ? "HH:mm" : "hh:mm a", Locale.getDefault());
        if (clockText != null) clockText.setText(fmt.format(new Date()));

        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        int level = bm != null ? bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) : -1;
        if (batteryText != null) {
            batteryText.setText(level >= 0 ? "Battery " + level + "%" : "Battery n/a");
        }
    }

    // ------------------------------------------------------------ misc

    private String snapshot() {
        return theme() + "|" + columns() + "|" + iconDp() + "|" + showLabels()
                + "|" + prefs.getBoolean("hideStatus", false)
                + "|" + prefs.getBoolean("mono", false)
                + "|" + prefs.getInt("warm", 0)
                + "|" + prefs.getBoolean("clock24", true)
                + "|" + prefs.getString("dock", DEFAULT_DOCK);
    }

    private class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return shownApps.size(); }
        @Override public Object getItem(int pos) { return shownApps.get(pos); }
        @Override public long getItemId(int pos) { return pos; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            LinearLayout cell;
            if (convert instanceof LinearLayout) {
                cell = (LinearLayout) convert;
            } else {
                cell = new LinearLayout(MainActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                cell.setPadding(dp(2), dp(6), dp(2), dp(6));
                AbsListView.LayoutParams lp = new AbsListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                cell.setLayoutParams(lp);

                ImageView iv = new ImageView(MainActivity.this);
                iv.setId(1);
                LinearLayout.LayoutParams lpI = new LinearLayout.LayoutParams(
                        dp(iconDp()), dp(iconDp()));
                cell.addView(iv, lpI);

                TextView tv = new TextView(MainActivity.this);
                tv.setId(2);
                tv.setTextColor(Ui.text(theme()));
                tv.setTextSize(11);
                tv.setMaxLines(1);
                tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams lpT = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lpT.setMargins(dp(2), dp(4), dp(2), 0);
                cell.addView(tv, lpT);
            }
            AppEntry e = shownApps.get(pos);
            ImageView iv = cell.findViewById(1);
            TextView tv = cell.findViewById(2);
            iv.setImageBitmap(e.icon);
            tv.setText(e.label);
            tv.setVisibility(showLabels() ? View.VISIBLE : View.GONE);
            return cell;
        }
    }
}
