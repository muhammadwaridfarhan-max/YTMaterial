# YTMaterial

Material Design YouTube client for Android, built with Jetpack Compose and Kotlin.
A clean, fast YouTube experience with glass UI, background play, downloads and
Google sign-in.

## Features

* Material 3 dynamic theming (wallpaper-derived or custom)
* Home, Shorts, Subscriptions, and You tabs
* Search with suggestions
* Google Sign-In (device-code flow + account photo sync)
* Video playback with quality selection
* Download support
* Channel pages
* Mini-player with floating nav bar
* **Google feed sync** — when signed in with a Google account, the home tab can show
  that account's YouTube feed (subscriptions + liked videos) via the YouTube Data API v3.

## Build

```shell
./gradlew assembleDebug
```

## Requirements

* JDK 17
* Android SDK with compileSdk 36 and targetSdk 36
* Gradle 8.11.1 (wrapper included)

## Google Sign-In

Sign-in is optional. To enable it:

1. Create an OAuth 2.0 **Web client** in [Google Cloud Console](https://console.cloud.google.com/).
2. Set the `GOOGLE_WEB_CLIENT_ID` env var (or project property) to that Web client ID.
3. The app also includes a device-code flow for YouTube API access.

> ⚠️ Never commit secrets. Any client secret must stay out of the repo — read the code
> that ships one and move it to an Android Keystore/backend.

## Google Feed Sync (home tab)

When signed in with Google and a YouTube API access token is available:

1. Open the **You** tab (avatar).
2. Toggle **Use Google feed**.
3. The home tab switches to that account's YouTube feed (subscriptions + liked videos).

The feed syncs from the [YouTube Data API v3](https://developers.google.com/youtube/v3) using
the stored access token (`https://www.googleapis.com/oauth2/v3/userinfo` for refresh, and
`youtube.channels/playlistItems/search` for the feed). Each page is fetched per source and the
feed is endless (it moves on to the next source when one runs dry).

## Workflow (Ubuntu terminal)

```shell
./scripts/gitflow.sh setup     # install git + gh, set remote
./scripts/gitflow.sh login     # GitHub login
./scripts/gitflow.sh save "msg"# add + commit + push to current branch
./scripts/gitflow.sh push      # push only
./scripts/gitflow.sh branch feat/mywork   # new branch
./scripts/gitflow.sh ci        # trigger the Android CI workflow
./scripts/gitflow.sh release v0.6.0  # tag -> release APK workflow
```

The CI workflows (`.github/workflows/`) build debug and release APKs on every push to `main`
and on version tags.

## License

MIT
