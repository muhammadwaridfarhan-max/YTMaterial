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

## Requirements

- JDK 17
- Android SDK with compileSdk 36 and targetSdk 36
- Gradle 8.11.1 (wrapper included)

## Google Sign-In

Sign-in is optional. To enable it, set the `GOOGLE_WEB_CLIENT_ID` property or environment variable to your OAuth 2.0 web client ID.

## License

MIT