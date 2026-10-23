NICKCHAT - ANDROID APP PROJECT
==============================
This is the same Nickchat 1.1 (same features, with Gmail login and voice and video calls) inside a real Android app.

EASIEST WAY TO GET THE APK (no programs to install)
1. On github.com click + > New repository. Name it Nickchat-Android. Create it.
2. Click "uploading an existing file". Unzip this folder and drag EVERYTHING
   inside it in (including the hidden .github folder). Click Commit changes.
3. Open the Actions tab. Wait ~5 minutes for "Build Android app" to turn green.
   (If it does not start, click it > Run workflow.)
4. Open the finished run, scroll to Artifacts, download Nickchat-Android-APK.
5. Unzip it, send app-debug.apk to your phone, open it, allow
   "install unknown apps" when asked.

OR: open this folder in Android Studio and press Run.

NOTES
- Calls ask for the microphone and camera the first time.
- Notifications show while the app is open or in the background; if Android
  closes the app, messages wait on the relay (about 12 hours) until you open it.
- Back up chats saves a file into your Downloads folder.
- This build is a debug APK for your own use. For Google Play it needs signing.
- The app was written without a phone to test on, so tell Claude if anything
  looks wrong.
