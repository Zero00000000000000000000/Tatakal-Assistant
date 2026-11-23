# Tatkal Assistant

A native Kotlin Android companion for preparing an IRCTC Tatkal booking session.

## Included
- Official IRCTC site inside a WebView.
- Local journey and one-passenger preparation form.
- Copy prepared details to the device clipboard.
- Clear form action.
- Navigation limited to IRCTC domains.
- GitHub Actions debug APK build.

## Privacy and boundaries
- Form values are held in screen memory only; this app does not persist or upload them.
- Copying details puts them on the system clipboard.
- The app does not collect IRCTC credentials, automate login, submit a booking, bypass CAPTCHA, solve human-verification challenges, automate OTP, or handle payment.
- CAPTCHA, OTP, passenger verification, and final booking steps remain user-controlled.

## Build
Requirements: JDK 17, Android SDK 35, and Gradle 8.9.

```bash
gradle assembleDebug
```

The APK is uploaded as the `tatkal-assistant-debug` artifact by the Android CI workflow after a successful build.
