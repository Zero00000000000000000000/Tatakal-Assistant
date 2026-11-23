# Tatkal Assistant

A native Android 13+ companion app for organizing an IRCTC booking session.

## Current scope
- Android Kotlin app with a lightweight WebView shell.
- User-controlled navigation to the official IRCTC website.
- JavaScript and DOM storage enabled for site compatibility.
- CAPTCHA and OTP remain entirely user-controlled; no bypass, automated solving, or automated challenge submission.
- No credential collection or storage.

## Build
Open this project in Android Studio, or run a Gradle 8.9 build with JDK 17 and Android SDK 35:

```bash
gradle assembleDebug
```

The GitHub Actions workflow builds the debug APK on pushes and pull requests.
