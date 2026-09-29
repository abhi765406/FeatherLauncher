package com.featherlauncher.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Feather Launcher settings. All values are written straight to
 * SharedPreferences; MainActivity picks them up on resume and rebuilds.
 */
public class SettingsActivity extends Activity {

    private SharedPreferences prefs;
    private float density;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("feather", MODE_PRIVATE);
        density = getResources().getDisplayMetrics().density;

        int theme = prefs.getInt("theme", Ui.DEFAULT_THEME);
        Ui.applyMonochrome(this, prefs.getBoolean("mono", false));
        Ui.applyFullscreen(this, prefs.getBoolean("hideStatus", false));
        Ui.tintSystemBars(this, theme);

        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int m = dp(16);
        page.setPadding(m, m, m, m);
        scroll.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        page.addView(header("Feather Launcher"));
        page.addView(note("Tiny, private, feather-light. Changes apply instantly."));

        // ---------------- Theme ----------------
        page.addView(section("Theme"));
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        final int[] ids = new int[Ui.THEME_NAMES.length];
        for (int i = 0; i < Ui.THEME_NAMES.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(Ui.THEME_NAMES[i]);
            rb.setTextColor(Ui.text(theme));
            rb.setTextSize(14);
            rb.setPadding(0, dp(6), 0, dp(6));
            ids[i] = View.generateViewId();
            rb.setId(ids[i]);
            group.addView(rb);
        }
        group.check(ids[theme]);
        group.setOnCheckedChangeListener((RadioGroup g, int checkedId) -> {
            for (int i = 0; i < ids.length; i++) {
                if (ids[i] == checkedId) {
                    prefs.edit().putInt("theme", i).apply();
                    Toast.makeText(SettingsActivity.this, "Theme applied", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        });
        page.addView(wrap(group));

        // ---------------- Appearance ----------------
        page.addView(section("Appearance"));

        final TextView columnsLabel = body("Home columns: " + prefs.getInt("columns", 4));
        page.addView(columnsLabel);
        SeekBar columnsBar = new SeekBar(this);
        columnsBar.setMax(3); // 3..6
        columnsBar.setProgress(prefs.getInt("columns", 4) - 3);
        columnsBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                int v = p + 3;
                columnsLabel.setText("Home columns: " + v);
                prefs.edit().putInt("columns", v).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        page.addView(wrap(columnsBar));

        final TextView iconLabel = body("Icon size: " + prefs.getInt("iconDp", 52) + " dp");
        page.addView(iconLabel);
        SeekBar iconBar = new SeekBar(this);
        iconBar.setMax(44); // 40..84
        iconBar.setProgress(prefs.getInt("iconDp", 52) - 40);
        iconBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                int v = p + 40;
                iconLabel.setText("Icon size: " + v + " dp");
                prefs.edit().putInt("iconDp", v).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        page.addView(wrap(iconBar));

        page.addView(check("Show app names", "labels", true));
        page.addView(check("Hide system status bar (full screen)", "hideStatus", false));
        page.addView(check("24-hour clock", "clock24", true));
        page.addView(check("Monochrome mode (grayscale)", "mono", false));

        // ---------------- Eye comfort ----------------
        page.addView(section("Eye comfort"));
        final TextView warmLabel = body("Warm filter: " + prefs.getInt("warm", 0) + "%");
        page.addView(warmLabel);
        SeekBar warmBar = new SeekBar(this);
        warmBar.setMax(70);
        warmBar.setProgress(prefs.getInt("warm", 0));
        warmBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                warmLabel.setText("Warm filter: " + p + "%");
                prefs.edit().putInt("warm", p).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        page.addView(wrap(warmBar));
        page.addView(note("Softens blue light on the home screen. Rendered by the GPU - zero extra battery."));

        // ---------------- Wallpaper ----------------
        page.addView(section("Wallpaper"));
        page.addView(button("Choose wallpaper", v -> {
            try {
                startActivity(new Intent(Intent.ACTION_SET_WALLPAPER));
            } catch (Exception e) {
                Toast.makeText(this, "Wallpaper picker not available", Toast.LENGTH_SHORT).show();
            }
        }));
        page.addView(button("Use a photo as wallpaper", v -> {
            try {
                startActivityForResult(new Intent(Intent.ACTION_PICK,
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI), 7);
            } catch (Exception e) {
                Toast.makeText(this, "Gallery not available", Toast.LENGTH_SHORT).show();
            }
        }));

        // ---------------- System ----------------
        page.addView(section("System"));
        page.addView(button("Set as default home app", v -> {
            try {
                startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
            } catch (Exception e) {
                Toast.makeText(this, "Open Settings > Apps > Default apps manually", Toast.LENGTH_LONG).show();
            }
        }));
        page.addView(button("Reset dock to defaults", v -> {
            prefs.edit().remove("dock").apply();
            Toast.makeText(this, "Dock reset", Toast.LENGTH_SHORT).show();
        }));
        page.addView(button("About", v -> new AlertDialog.Builder(this)
                .setTitle("Feather Launcher v1.0")
                .setMessage("Built for 1-2 GB RAM devices.\n\n"
                        + "\u2022 Installed size under 2 MB\n"
                        + "\u2022 Zero permissions - no internet, no tracking\n"
                        + "\u2022 No services, no background work, no wake locks\n"
                        + "\u2022 Only a few KB of settings storage\n\n"
                        + "Long-press an app for info, uninstall or dock pin. "
                        + "Long-press a dock icon to unpin.")
                .setPositiveButton("OK", null)
                .show()));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 7 && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;
            Intent attach = new Intent(Intent.ACTION_ATTACH_DATA);
            attach.setDataAndType(uri, "image/*");
            attach.putExtra("mimeType", "image/*");
            try {
                startActivity(Intent.createChooser(attach, "Set as"));
            } catch (Exception e) {
                Toast.makeText(this, "No wallpaper cropper found on this device", Toast.LENGTH_LONG).show();
            }
        }
    }

    // ------------------------------------------------------------- helpers

    private int dp(int v) { return (int) (v * density + 0.5f); }

    private TextView header(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Ui.accent(prefs.getInt("theme", Ui.DEFAULT_THEME)));
        t.setTextSize(22);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    private TextView section(String s) {
        TextView t = new TextView(this);
        t.setText(s.toUpperCase());
        t.setTextColor(Ui.accent(prefs.getInt("theme", Ui.DEFAULT_THEME)));
        t.setTextSize(12);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(22), 0, dp(8));
        t.setLayoutParams(lp);
        return t;
    }

    private TextView body(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Ui.text(prefs.getInt("theme", Ui.DEFAULT_THEME)));
        t.setTextSize(14);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(8), 0, dp(2));
        t.setLayoutParams(lp);
        return t;
    }

    private TextView note(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Ui.sub(prefs.getInt("theme", Ui.DEFAULT_THEME)));
        t.setTextSize(12);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), 0, dp(4));
        t.setLayoutParams(lp);
        return t;
    }

    private View wrap(View child) {
        int theme = prefs.getInt("theme", Ui.DEFAULT_THEME);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(4), dp(14), dp(4));
        box.setBackground(Ui.rounded(Ui.card(theme), 16, density, 255));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), 0, dp(4));
        box.setLayoutParams(lp);
        box.addView(child);
        return box;
    }

    private CheckBox check(String label, final String key, boolean def) {
        CheckBox cb = new CheckBox(this);
        cb.setText(label);
        cb.setTextColor(Ui.text(prefs.getInt("theme", Ui.DEFAULT_THEME)));
        cb.setTextSize(14);
        cb.setChecked(prefs.getBoolean(key, def));
        cb.setOnCheckedChangeListener((CompoundButton b, boolean checked) ->
                prefs.edit().putBoolean(key, checked).apply());
        return (CheckBox) wrap(cb);
    }

    private View button(String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(0xFFFFFFFF);
        b.setAllCaps(false);
        int theme = prefs.getInt("theme", Ui.DEFAULT_THEME);
        GradientDrawable bg = Ui.rounded(Ui.accent(theme), 14, density, 255);
        b.setBackground(bg);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), 0, dp(4));
        b.setLayoutParams(lp);
        return b;
    }
}
