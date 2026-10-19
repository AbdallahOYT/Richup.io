# Richup Mod (3bood) — Android WebView project

This is an Android Studio/Gradle project for an online WebView wrapper. It loads the official Richup website at `https://richup.io/`; it does not copy or bundle Richup's game files.

## Included
- App label: **Richup Mod (3bood)**
- Simple custom green/dark launcher icon
- Hardware-accelerated WebView with JavaScript and DOM storage
- Fullscreen immersive mode
- Floating Home, Reload, and Keep Awake controls
- Back button navigates browser history
- GitHub Actions workflow to build a debug APK

## Build an APK using GitHub
1. Create a GitHub repository and upload all files in this project.
2. Open the repository's **Actions** tab.
3. Select **Build Richup Mod (3bood) APK** and press **Run workflow** (or push to `main`).
4. Open the completed workflow run.
5. Under **Artifacts**, download `Richup-Mod-3bood-debug-APK`.
6. Extract the downloaded artifact ZIP and install `app-debug.apk` on your Android phone.

## Important performance note
The wrapper enables hardware acceleration and uses normal WebView caching, but it cannot rewrite Richup's own game code or guarantee higher FPS / lower server latency. For best results, update **Android System WebView** and Chrome through Google Play, close other heavy apps, and test on a stable Wi-Fi connection.

## Customize
- App name: `app/src/main/AndroidManifest.xml` → `android:label`
- Launcher icon: `app/src/main/res/drawable/ic_richup_launcher.xml`
- Package/application ID: `app/build.gradle` → `applicationId` and `namespace`

This is a debug build workflow, not a Play Store release/signing setup.
