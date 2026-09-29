# Feather Launcher

A feather-light Android home screen launcher, built for low-end devices
(1-2 GB RAM, 16 GB storage, e.g. vivo Y91i / Android 8).

## Highlights

- **Under 2 MB APK** - pure native code, no libraries, no WebView
- **Zero permissions** - no INTERNET, no storage, no location. Nothing can be tracked.
- **No background work** - no services, no receivers, no wake locks.
  The clock ticker runs only while the home screen is visible.
- **Tiny footprint** - settings live in a few KB of SharedPreferences; cache stays minimal.
- **Butter-smooth on 1 GB RAM** - all layouts are built programmatically (no XML
  inflation) and app icons are pre-scaled on a background thread.
- **Eye comfort** - warm blue-light filter, grayscale mode, AMOLED black theme.
- **Themes** - Midnight Dark, Pure Light, AMOLED Black, Mint Forest, Sunset Warm.
- **Custom status strip** - clock + battery read directly from the system,
  no broadcast receiver needed.
- **Hide the system status bar** for a full-screen, distraction-free home.
- **App search, dock with pin/unpin, long-press app options**
  (App info / Uninstall / Pin to dock).

## Works on

Android 7.0 (API 24) and above - covers the vivo 1820 / Y91i (Android 8, 2 GB RAM).

## Build with GitHub Actions

1. Create a new repository on GitHub.
2. Upload **all the contents inside this zip** to the repository root
   (so `settings.gradle` sits at the repo root).
3. Go to the **Actions** tab and run the **Build APK** workflow.
4. Download the `FeatherLauncher-app` artifact - the APK is inside.

## Use as home screen

Open the app, then either:

- tap **Set as default home app** in its Settings screen, or
- press the device's Home button and choose *Feather Launcher* when asked.

## Repo layout

```
app/src/main/java/com/featherlauncher/app/   MainActivity, SettingsActivity, Ui
app/src/main/res/                            app name + vector icon only
.github/workflows/build.yml                  proven GitHub Actions build
```

## Privacy

The manifest declares **no `<uses-permission>` entries at all**.
