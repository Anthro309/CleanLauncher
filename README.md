# Clean Launcher v2.1 (Chromecast / Google TV)

A smooth, modern home screen with no Google ads or recommendations.

- **My apps**: On first run, up to six installed apps are pinned (prioritizing Stremio, Netflix, Hulu, StreamQ and YouTube). These five can be added when installed later. You can pin/unpin anything under **All apps**; your choices are remembered.
- **Popular on Stremio**: poster rows for popular movies and series, pulled from Stremio's public catalog (no account needed). OK opens the title in Stremio, or just opens Stremio if the link isn't supported.
- **Backgrounds**: 8 built-in backgrounds that fade into each other. Press the **Background** button to cycle; your pick is remembered.

## Controls
- OK: open an app or title
- Hold OK (or the remote's Menu button) on an app: add/remove from home, hide, app info, uninstall
- Top right: Background, All apps / Back to home, Show hidden apps, Settings
- Back: return from **All apps** to the home screen. On the home screen, Back stays on the launcher.
- If catalogs are unavailable, installed apps still function offline.

## 1. Build the APK (no Android Studio needed)
1. Create a new GitHub repo (for example `clean-launcher`) under your Anthro309 account.
2. Upload everything in this folder, keeping the folder structure (including the hidden `.github` folder).
3. Open the repo's **Actions** tab, pick **Build APK**, click **Run workflow**.
4. When it finishes, open the run and download the **CleanLauncher-apk** artifact. Unzip it to get `app-debug.apk`.

If the build fails, copy the error from the log and send it to me.

## 2. Install on the Chromecast
With your Chromecast connected over adb:

```
adb -s YOUR_CHROMECAST_IP:5555 install -r ~/Downloads/app-debug.apk
```

Clean Launcher now shows up in your app list. Open it once to make sure it works.

## 3. Make it your Home screen
The stock launcher answers the Home button, so turn it off for your user profile:

```
adb -s YOUR_CHROMECAST_IP:5555 shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
adb -s YOUR_CHROMECAST_IP:5555 shell pm disable-user --user 0 com.google.android.tvrecommendations
```

Press Home. Android should ask which launcher to use (pick Clean Launcher, Always). If the screen is black or it behaves badly, undo it:

```
adb -s YOUR_CHROMECAST_IP:5555 shell pm enable com.google.android.apps.tv.launcherx
adb -s YOUR_CHROMECAST_IP:5555 shell pm enable com.google.android.tvrecommendations
```

Do this step only after confirming Clean Launcher opens and shows your apps.

## Changes in v2.1
- App drawer Back navigation corrected.
- Initial favorites work even if none of the suggested streaming apps are present.
- Stremio catalog refreshes are deduplicated and HTTP connections closed reliably.
- Offline catalog fallback message added.
- Background index safely restored if saved preferences are corrupt.
- Unnecessary package-deletion permission removed.
- GitHub build now runs Android lint and includes the lint report.

## Verification
- Source/configuration checks were performed on this package.
- **An APK has not been compiled or tested on a TV here**: this environment does not have Android SDK/Gradle.
- GitHub Actions will run `lintDebug` and `assembleDebug` and produce an APK or actionable build logs.
- Check launch, D-pad focus, long-press, Stremio deep links and Home selection on your actual device before disabling the stock launcher.

## Notes
- Netflix, Hulu and YouTube recommendations can't be pulled into a third-party launcher, so the recommendation rows come from Stremio's catalog only.
- If an app isn't added automatically (for example StreamQ under a different name), open **All apps**, hold OK on it, and choose **Add to home**.
