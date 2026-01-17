# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

网页转APK生成器 - A web-to-Android APK generator that wraps any website into a native Android app with extensive native capabilities.

## Commands

```bash
# Install dependencies (first time)
python setup_env.py

# Start the web server
python app.py
# Or use: start.bat

# Access the web UI
# http://localhost:5000
```

## Architecture

### Build Flow
1. User submits form via web UI (`templates/index.html`)
2. Flask backend (`app.py`) receives request at `/build` endpoint
3. `build_apk()` generator function:
   - Copies `android-template/` to `output/build_<uuid>/`
   - Processes icon into multiple densities
   - Generates or uses existing keystore
   - Dynamically modifies: `strings.xml`, `colors.xml`, `bools.xml`, `build.gradle`, `AndroidManifest.xml`
   - Renames package directories and updates package declarations in Kotlin files
   - Conditionally injects/removes code for optional features (FCM, Google Login)
   - Runs Gradle build via subprocess
   - Packages APK + keystore + docs into ZIP
4. SSE streams progress back to frontend

### Key Files

**Backend:**
- `app.py` - Flask server, APK build logic, dynamic code injection

**Android Template (`android-template/app/src/main/java/com/webapk/app/`):**
- `MainActivity.kt` - WebView setup, JS bridge (`Web2APKBridge` inner class), all native feature implementations
- `FCMService.kt` - Firebase push notification handler (conditionally included)
- `ExternalBrowserActivity.kt` - In-app browser for external links
- `ScanActivity.kt` - QR/barcode scanner
- `ForegroundService.kt` - Persistent notification service
- `WidgetProvider.kt` - Home screen widget
- `NotificationReceiver.kt` - Local notification handler

**Frontend:**
- `templates/index.html` - Single-page form with target market toggle (overseas/China)

### Dynamic Code Injection Pattern

In `app.py`, features are conditionally enabled by:
1. String replacement in Kotlin source files
2. Adding/removing dependencies in `build.gradle`
3. Adding service declarations to `AndroidManifest.xml`
4. Deleting unused `.kt` files (e.g., `FCMService.kt` when FCM disabled)

Example: FCM is enabled by detecting `fcm_config_path`, then:
- Copies `google-services.json` to `app/`
- Adds google-services plugin to both gradle files
- Adds Firebase dependencies
- Replaces stub JS interface methods with real implementations
- Registers FCMService in manifest

### JS Bridge Interface

All native features are exposed to web pages via `window.Web2APK.*` methods defined in `Web2APKBridge` class. Callbacks use `webView.evaluateJavascript()` to invoke JS functions like `onPushRegistered()`, `onScanResult()`, etc.

### Target Market System

UI toggles between "海外 (Overseas)" and "中国大陆 (China)" modes:
- Overseas: Shows Google Login + FCM push options
- China: Shows placeholder for JPush/Getui (not yet implemented)

## Important Notes

- Android template uses Kotlin, not Java
- Package name is dynamically replaced throughout the codebase during build
- Gradle version is `8.1.0`, Android Gradle Plugin is `8.1.0`
- MinSDK 24 (Android 7.0), TargetSDK 34 (Android 14)
- Build outputs go to `output/` directory (gitignored)
- Uploaded files go to `uploads/` directory (gitignored)
- Tools (JDK, Android SDK) are in `tools/` directory (auto-downloaded by `setup_env.py`)
