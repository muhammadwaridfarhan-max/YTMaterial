# YTMaterial

Material Design YouTube client for Android, built with Jetpack Compose and Kotlin.
Inspired by MeeTube's polish (liquid glass, smooth scrolling, account sync).

## Features

- Material 3 dynamic theming (wallpaper-derived or custom)
- Home, Shorts, Subscriptions, and You tabs
- Search with suggestions + quick filter chips
- Google Sign-In (Credential Manager) — profile photo & name synced
- YouTube device-code login (TV-style) for account / feed sync
- Video playback with quality selection
- Download support
- Channel pages with improved avatar header
- Mini-player with floating glass nav bar
- Floating top bar that hides/shows on scroll (collapses on account tab)

## What's new in 0.6.0

- Account tab shows Google profile photo (synced from Google account)
- Search interface improved with horizontal filter chips
- Larger, smoother channel avatar header
- Rendering optimizations and cleaner You tab layout
- Version bump + ready for CI workflows

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