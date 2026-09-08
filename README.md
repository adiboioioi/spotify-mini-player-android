# Spotify Mini Player

Minimal, custom Android UI for controlling local Spotify playback — play/pause,
skip, seek, album art, and a background color that shifts with the track's
artwork. No ads, no unrelated buttons, no messy landscape view. Built with
Jetpack Compose on top of Spotify's App Remote SDK.

Spotify isn't rendering anything here — your app talks to the Spotify app
already installed and logged in on the phone, and draws its own UI on top.

## Features

- Play / pause, skip next / previous, draggable seek bar
- Live album art, track name, and progress via `PlayerState` updates
- Portrait and landscape layouts (album art left, controls right in landscape)
- Background color extracted per-track from album art (via Android's Palette
  library), cross-fading between songs
- Edge-to-edge immersive layout (system bars hidden, swipe to reveal)
<img width="auto" height="500" alt="Screenshot_20260909_011220_com_example_spotifymini_MainActivity" src="https://github.com/user-attachments/assets/e1dc371f-f3ba-4e8c-b3ec-423a306ab324" />


## Requirements

- Android Studio (recent stable)
- A physical Android phone with the Spotify app installed and logged in
  — **the Spotify App Remote SDK does not work on emulators** (no Widevine
  DRM / Play Integrity support on most images)
- A free Spotify Developer account

## Setup


### 1. Create a Spotify Developer app

1. Go to the [Spotify Developer Dashboard](https://developer.spotify.com/dashboard)
   and create an app.
2. Add a Redirect URI: `spotifyminiplayer://callback`.
3. Under supported SDKs, enable **Android**.
4. In **Settings → Android packages**, add:
   - Package name: `com.example.spotifymini`
   - Debug keystore SHA-1 (see below for how to get it)
5. In **User Management**, add the Spotify account(s) you'll test with.
   New apps start in Development Mode, limited to a handful of whitelisted
   testers — see [Distribution](#distribution) below for what wider release
   actually requires.

### 2. Get your debug keystore SHA-1

The debug keystore is generated the first time you build the app, so build
once before running this:

```
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```

(On Windows PowerShell, use `"$env:USERPROFILE\.android\debug.keystore"` as
the keystore path — `~` doesn't expand there.)

Paste the SHA-1 into the dashboard's Android package settings.

### 3. Download the App Remote + Auth SDKs

These aren't published to Maven — grab them from Spotify's SDK releases:
https://github.com/spotify/android-sdk/releases

Place both files in `app/libs/` (create the folder if needed):
- `spotify-app-remote-release-X.X.X.aar`
- `spotify-auth-release-X.X.X.aar`

### 4. Configure your Client ID

Copy the example config and fill in your real Client ID:

```
cp local.properties.example local.properties
```

Edit `local.properties`:

```
sdk.dir=/path/to/your/Android/Sdk
spotify.client.id=YOUR_SPOTIFY_CLIENT_ID
```

`local.properties` is git-ignored — your Client ID never gets committed.
It's read at build time into `BuildConfig.SPOTIFY_CLIENT_ID`.

### 5. Build and run

Open the project in Android Studio, let Gradle sync, select your physical
phone in the device dropdown, and hit **Run**. Use the app's own Run button,
not "Generate APK" — the latter doesn't install/launch anything, and you can
end up debugging a stale build.

On first launch you'll see Spotify's own "Allow access?" screen — accept it.

## Troubleshooting

**`CouldNotFindSpotifyApp`** — you're likely on an emulator. Use a real
device with Spotify installed and logged in.

**`UserNotAuthorizedException`, no consent screen shown** — usually a
dashboard mismatch: double check the Client ID, redirect URI, package name,
and SHA-1 all match exactly what's in the dashboard.

**`SpotifyRemoteServiceException: Unable to connect to Spotify service`**
- Make sure Spotify is actually open (foreground or background) on the same
  physical device — App Remote only controls a local Spotify process, not a
  session playing on another device via Spotify Connect.
- On Android 11+, this can also mean your manifest is missing a `<queries>`
  entry declaring the Spotify package — check `AndroidManifest.xml`.

**Auth hangs forever on "Connecting...", no popup ever appears** — this
project works around a real Android restriction: if App Remote tries to
launch Spotify's consent screen from Spotify's *background* process, Android
blocks it (Background Activity Launch protection), especially aggressively
on Honor/Huawei MagicOS devices. The fix used here is to call Spotify's
`AuthorizationClient.openLoginActivity()` directly from this app's own
foreground Activity first, then call `connect()` only after auth succeeds —
foreground-initiated launches are always allowed. If you fork this and rip
that flow out, you'll likely hit the same silent hang.

**Logcat shows an unreadable `(HKS)...(HKE)` blob** — Honor/MagicOS
encrypts the logcat buffer by default. Enable it via the hidden service menu
(dial `*#*#2846579#*#*`) → **AP LOG settings** → turn on the log switch.

## Architecture notes

- `MainActivity.kt` owns the Spotify connection lifecycle, auth flow, and
  `PlayerState` subscription, exposing state via Compose `State` objects.
- UI is split into reusable pieces (`AlbumArtView`, `TrackInfo`,
  `PlaybackSlider`, `PlaybackControls`) composed differently for portrait vs.
  landscape based on `LocalConfiguration.current.orientation`.
- Background color comes from the Palette library's dominant/muted swatch on
  each new album art bitmap, animated with `animateColorAsState`.

## Distribution

This is currently set up for personal/small-scale use only. As of Spotify's
current developer terms, apps start in Development Mode (a handful of
manually-whitelisted testers) and reaching wider public distribution requires
Spotify's Extended Quota approval, which has a high bar (registered business,
large existing user base, etc.). There's currently no practical path from a
hobby project to public distribution through Spotify's own program — treat
this as a tool for yourself and people you add as testers, not something to
publish on the Play Store as-is.

If you fork this for your own use, also note "Spotify" is a trademark —
avoid using its name/branding in a way that implies official affiliation if
you share your build with others.

## License

MIT — see [LICENSE](LICENSE).
