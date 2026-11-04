# YTMaterial

Material Design YouTube client for Android, built with Jetpack Compose and Kotlin.

## Features

- Material 3 dynamic theming (wallpaper-derived or custom)
- Home, Shorts, Subscriptions, and You tabs
- Search with suggestions
- Google Sign-In (device-code flow)
- Video playback with quality selection
- Download support
- Channel pages
- Mini-player with floating nav bar

## Build

```bash
./gradlew assembleDebug
```

## Git & CI dari terminal Ubuntu

```bash
chmod +x scripts/gitflow.sh
./scripts/gitflow.sh setup            # pasang git, gh, JDK 17, set remote
./scripts/gitflow.sh login            # login GitHub
./scripts/gitflow.sh save "pesan"     # add + commit + push
./scripts/gitflow.sh ci               # jalankan workflow Android CI & pantau
./scripts/gitflow.sh release v1.0.0   # tag -> workflow Release APK
```

## Requirements

- JDK 17
- Android SDK with compileSdk 36 and targetSdk 36
- Gradle 8.11.1 (wrapper included)

## Google Sign-In

Sign-in is optional. To enable it, set the `GOOGLE_WEB_CLIENT_ID` property or environment variable to your OAuth 2.0 web client ID.

## License

MIT