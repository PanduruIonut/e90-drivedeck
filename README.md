# DriveDeck (E90 head unit)

Private. Home screen for the 825X_Pro head unit: speed + clock, **Waze embedded**, **Spotify embedded**.
If embedding is not possible, each slot falls back to a card (Waze next-turn from its notification,
Spotify now-playing from its media session).

Embedding uses the hidden `android.app.ActivityView`, which needs signature permissions. The APK is
signed with the **public AOSP test platform key** (`keys/`), which this unit's firmware also uses.
That gives the app system-level permissions on the unit: keep it small and don't add networking.
The app has no INTERNET permission.

## Build

```sh
./build.sh        # -> build/drivedeck.apk, fails unless signed with cert SHA-1 27196e38…3dfa
```

No Gradle: `aapt2`, `javac`, `d8`, `zipalign`, `apksigner` from the Homebrew Android SDK.
`stubs/` holds a compile-only stub of `ActivityView`; it is never packaged.

## Install on the head unit

```sh
adb connect <unit-ip>:5555
adb install -r build/drivedeck.apk
adb shell pm grant dev.drivedeck android.permission.ACCESS_FINE_LOCATION
# Waze card (fallback) needs notification access:
adb shell cmd notification allow_listener dev.drivedeck/dev.drivedeck.DeckNotificationListener
adb shell am start -n dev.drivedeck/.MainActivity
adb shell "logcat -d | grep DriveDeck"
```

Test with other apps in the slots:
`adb shell am start -n dev.drivedeck/.MainActivity --es left com.android.settings --es right com.android.deskclock`

## Remove

```sh
adb uninstall dev.drivedeck
```

## What the emulator showed (17 oct 2026, AOSP Android 9 arm64, test-keys, 1920×720)

| Setup | Result |
|---|---|
| signature permissions, no `INTERNAL_SYSTEM_WINDOW` | `SecurityException` in `SafeActivityOptions.checkPermissions` → fallback cards |
| + `INTERNAL_SYSTEM_WINDOW` | launch allowed, but apps open **full screen on display 0** |
| + `force_resizable_activities=1` (reboot) | still display 0 |
| + feature `android.software.activities_on_secondary_displays` (emulator only, via root) | ✅ apps run **inside the slots** (displays #1/#2), touch works (`shots/emu-7-after-tap.png`) |

**So on the head unit everything depends on one check:**

```sh
adb shell pm list features | grep activities_on_secondary_displays
```

If it's there, embedding should work as on the emulator. If not, it can't be added without root (it's a
file in `/system/etc/permissions`), and the slots stay on the fallback cards.

### Music tabs

The music slot has tabs **Spotify · YouTube · YT Music** (`MusicPanel`). A tab switch launches that
app into the same embedded display; switching back brings the earlier app's task to the front with its
state kept (emulator: `shots/tabs-*.png`). The last tab is remembered. Tabs for apps that aren't installed
are dimmed. Test with other apps: `--es right pkg1,pkg2,pkg3`.

### Single resumed activity

Android 9 has one resumed activity system-wide: touching an embedded app pauses DriveDeck. GPS therefore
runs from `onStart`/`onStop`. Emulator check: with Settings resumed inside the slot, GPS fixes still updated
the speed view ("—" → "0 km/h"; the emulator doesn't pass the RMC speed field, so the value itself
is checked in the car).

## First run on the head unit (17 oct 2026, ~12:00)

- ✅ `android.software.activities_on_secondary_displays` **is present** → embedding works on the unit
  (YouTube and Settings ran inside the slots, on virtual displays #2/#3).
- ❌ **Scaling:** the panel is **1280×480 physical** but the system runs with `wm size` override **1920×720**.
  Virtual displays are created at the right logical size (971×690, 525×587), the app windows fill them,
  yet on screen the embedded content shows at roughly half width (`shots/car-*.png`). Next: reproduce on
  the emulator (AVD 1280×480 + `wm size 1920x720`), dump SurfaceFlinger layers, fix offline.
- ❌ After DriveDeck restarts, an app whose task already lives on display 0 (YouTube) is not pulled back
  into its slot. Next: stop the slot's app before the first embed, or move its task to the slot's display.
- Apps: Aurora Store 4.8.4 (F-Droid, signature checked) installed via adb. **Spotify 9.1.92 installed**
  through Aurora. **Waze: "App not supported" (v 0)**. Aurora kept the old anonymous auth
  (`Saved AuthData is valid`) after switching the spoof to Pixel 9a → log out and log in again. Fallback:
  the original Waze APK sideloaded.
- DriveDeck build with "one music app at a time" (pause + force-stop on tab switch) is installed,
  `FORCE_STOP_PACKAGES` granted. Not yet exercised on the unit.
- Wi-Fi/ADB drops repeatedly, and the unit's IP changes between .2 and .8, so these are real reconnects.
  Cause not identified yet (iPhone hotspot suspected). Read `wpa_supplicant` disconnect reasons right after a drop.
- Play Store auto-updates Google apps (Maps, TTS) over the hotspot's mobile data.

## After the first car run (17 oct 2026, afternoon, emulator only)

- **Scaling bug does not reproduce on the emulator** with the unit's exact setup (AVD 1280×480 @240 +
  `wm size 1920x720`): stock ActivityView fills its slots there. So the fault is in the unit's composition
  (the vendor HWC handles the separate SurfaceView layer differently).
- New default embedder: `EmbeddedDisplayView` (TextureView + our own `VirtualDisplay` + hidden
  `InputManager.createInputForwarder`). The app's frames are drawn inside DriveDeck's own window, so the HWC
  only sees one layer. On the emulator, the app fills the slot, touch works, and an app already running
  full screen (Settings on display 0) ends up in its slot (display 3).
  `--ez activityview true` brings back the stock ActivityView for an A/B on the unit; `car-test.sh` does both.
- **Layout A built** (dock | Waze | Now Playing + speed + date), `shots/layout-a-3.png`. Music sources come
  from the dock: picking one stops the previous app (one music app at a time).
- Still to check in the car: does the TextureView embedder fix the half-width rendering? Quick diagnosis:
  developer option "Disable HW overlays" with the stock ActivityView.

### Home app + slot watchdog (17 oct 2026)

- DriveDeck declares `category.HOME` (offered next to the unit's launcher; emulator: listed by
  `cmd package query-activities -c android.intent.category.HOME`). The dock's ▦ opens the unit's own launcher
  `com.android.launcher/com.android.launcher2.Launcher` (all apps, BMW menus).
- Watchdog: while DriveDeck is visible, every 5 s, an empty slot display gets its app relaunched.
  Emulator: force-stop the embedded app → 0 on the slot display immediately, back (2) 7 s later.
  It stops while DriveDeck is hidden, so an app you opened full screen isn't pulled back.
- `car-test.sh` also dumps media sessions (Now Playing) and greps props/settings for the outside temperature.

## Not done yet

- Not verified on the head unit (only on the Android 9 emulator, see above).
- If the embedded app dies, its slot stays black. Needs a relaunch.
- The ⤢ button overlaps the embedded app's top-right corner (e.g. a search icon).
- Not the default home app yet (no HOME intent filter): start it by hand until embedding is proven in the car.
- Map (MapLibre) and the 3D E90 model come after that.
