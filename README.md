# Tatkal Lite Browser

A lightweight native Kotlin Android browser focused on preparing an IRCTC Tatkal session.

## Features
- Opens the official IRCTC website inside the app (WebView; no external browser hand-off).
- Compact journey strip with locally saved origin, destination, date and train number.
- User-triggered **Fill page** helper for matching visible journey fields; values are never auto-submitted.
- CAPTCHA and functional images remain enabled. The app does not indiscriminately block images.
- Basic browser navigation, page reload through the site button, and IRCTC-domain navigation restriction.
- Minimal native UI and no third-party analytics or advertising SDKs.

## Important limits
- CAPTCHA images and challenge widgets are displayed normally; users must solve them themselves.
- The helper deliberately excludes CAPTCHA, OTP, passwords, payment/card fields, contact details and verification fields.
- Login, OTP, passenger verification, final booking and payment remain user-controlled on the official IRCTC site.
- This app does not bypass authentication, automate OTP/CAPTCHA, or submit a booking.
- Journey details are stored locally in app preferences on this device only. Clear app data to remove them.

## Build
Requirements: JDK 17, Android SDK 35 and Gradle 8.9.

```bash
gradle assembleDebug
```

The Android CI workflow uploads a debug APK after a successful build.
