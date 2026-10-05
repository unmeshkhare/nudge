# Build the Nudge APK

The project is a complete Android app (Capacitor) with:
- the full Nudge web app inside (www/index.html)
- real background reminders (local notifications, survive app close and reboot)
- a native home-screen widget (random Pip messages + streak + next task)
- Pip launcher icon and dark splash

## Option A - no installs: let GitHub build it (about 5 minutes)
1. Create a free GitHub account and a new empty repository (private is fine).
2. Upload everything in this folder (the "Add file -> Upload files" button works; keep the .github folder).
3. Open the **Actions** tab -> "Build Nudge APK" -> wait for the green tick
   (or press "Run workflow" if it did not start).
4. Open the finished run -> **Artifacts** -> download **Nudge-apk**, unzip it to get `app-debug.apk`.
5. Copy the APK to your phone and open it. Allow "Install unknown apps" when asked.

## Option B - on your PC with Android Studio
1. Install Android Studio and Node.js 22+.
2. In this folder: `npm install` then `npx cap sync android`
3. `npx cap open android` -> Build -> Build APK(s)
   (or: `cd android && gradlew assembleDebug`, APK at android/app/build/outputs/apk/debug/)

## After installing
- Allow notifications when asked (needed for reminders).
- Long-press the home screen -> Widgets -> **Nudge** -> drag it out. Tap the circular arrow on the widget for a new message.
- The widget refreshes about every 30 minutes (Android limit) and when you tap the arrow.

## Editing the app
Change `www/index.html`, then run `npx cap sync android` and build again.

## Play Store later
The debug APK is for personal use. A signed release build (AAB) is needed for the Play Store.
