package com.featherlauncher.app;

import android.app.Activity;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.WindowManager;

/**
 * Central place for themes and small UI helpers.
 * All colors are plain ints - no resources, no bloat.
 */
public final class Ui {

    public static final String[] THEME_NAMES = {
            "Midnight Dark", "Pure Light", "AMOLED Black", "Mint Forest", "Sunset Warm"
    };

    public static final int DEFAULT_THEME = 0; // "Midnight Dark"

    public static int bg(int t) {
        switch (t) {
            case 1:  return 0xFFF6F7FB;
            case 2:  return 0xFF000000;
            case 3:  return 0xFF0D1512;
            case 4:  return 0xFF1A1310;
            default: return 0xFF12141B;
        }
    }

    public static int card(int t) {
        switch (t) {
            case 1:  return 0xFFFFFFFF;
            case 2:  return 0xFF131318;
            case 3:  return 0xFF182420;
            case 4:  return 0xFF261D16;
            default: return 0xFF1D2230;
        }
    }

    public static int text(int t) {
        return t == 1 ? 0xFF181A20 : 0xFFE9ECF4;
    }

    public static int sub(int t) {
        return t == 1 ? 0xFF5A6070 : 0xFF9AA3B5;
    }

    public static int accent(int t) {
        switch (t) {
            case 1:  return 0xFF3360D6;
            case 2:  return 0xFF9C6BFF;
            case 3:  return 0xFF4CC38A;
            case 4:  return 0xFFE8955A;
            default: return 0xFF7C9EFF;
        }
    }

    /** Rounded rectangle background with optional transparency level (0-255). */
    public static GradientDrawable rounded(int color, float radiusDp, float density, int alpha) {
        GradientDrawable g = new GradientDrawable();
        int a = alpha < 0 ? (color >>> 24) : alpha;
        g.setColor((color & 0x00FFFFFF) | ((a & 0xFF) << 24));
        g.setCornerRadius(radiusDp * density);
        return g;
    }

    public static GradientDrawable circle(int color, int alpha) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        int a = alpha < 0 ? (color >>> 24) : alpha;
        g.setColor((color & 0x00FFFFFF) | ((a & 0xFF) << 24));
        return g;
    }

    public static void applyFullscreen(Activity a, boolean hide) {
        if (hide) {
            a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } else {
            a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    /** True monochrome mode: desaturates the whole window, done by the GPU. */
    public static void applyMonochrome(Activity a, boolean on) {
        if (on) {
            Paint p = new Paint();
            ColorMatrix cm = new ColorMatrix();
            cm.setSaturation(0f);
            p.setColorFilter(new ColorMatrixColorFilter(cm));
            a.getWindow().getDecorView().setLayerType(View.LAYER_TYPE_HARDWARE, p);
        } else {
            a.getWindow().getDecorView().setLayerType(View.LAYER_TYPE_NONE, null);
        }
    }

    public static void tintSystemBars(Activity a, int theme) {
        a.getWindow().setStatusBarColor(bg(theme));
        a.getWindow().setNavigationBarColor(bg(theme));
    }

    private Ui() {}
}
