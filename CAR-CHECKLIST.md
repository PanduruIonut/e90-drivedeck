# Car session checklist

## At home, before

- [ ] **Google account for the head unit.** With YouTube Premium **Family**: make a separate "car" account
      and add it to the family. With individual Premium: decide whether to use the main account (with 2FA).
- [ ] Have the **Spotify** login at hand (or log in with the phone).
- [ ] Laptop charged + charger, phone charged (the hotspot drains it).
- [ ] iPhone → Personal Hotspot: **Maximize Compatibility ON**. **Low Power Mode OFF**.
- [ ] Mobile data: Waze (~200 MB) and Google updates download over the hotspot.

## In the car

1. **Engine running** (a 30–45 min session drains the battery). Handbrake on, **not** in reverse.
2. Head unit: Wi-Fi → the phone's hotspot. **AutoLink off.** ADB on (Developer options).
3. Laptop on the hotspot → `./connect.sh`.
4. `./car-verdict.sh --fresh` (resumable: if the Wi-Fi drops → `./connect.sh` → `./car-verdict.sh` again):
   - `activityview`: the old variant, **must fail** (FAIL ~0.5). Shows that the judge sees the problem;
   - `hwc_off`: the same with hardware composition off. **PASS = the hardware composer is the cause**;
   - `texture`: the new embedder **without** the vdfix correction, expected to FAIL (~width/1920);
   - `texture_fix`: **the decisive test**, with vdfix: must PASS and the capture must look undistorted;
   - `vdscale`: only if both fail, tries other virtual display sizes;
   - `fps`: frame rate of DriveDeck with Waze, scripted map swipes;
   - `gps`: Waze's location permission and the last GPS fix;
   - `temp`: the outside temperature property (must match the unit's status bar);
   - the full verdict is in `shots/<session>/verdict.md`, with captures and SurfaceFlinger dumps.
5. Google account on the unit → **Play Store: Settings → Auto-update apps → Don't auto-update** →
   install **Waze** → log in to **YouTube** (Premium).
6. Play something in **Spotify** and in **YouTube** → check what the Now Playing card receives.
   Then tap the **Spotify cover** in Now Playing → the library opens over the map → the first time,
   Spotify asks to **authorize** DriveDeck (once) → pick a playlist.
7. Press Home → **DriveDeck** → **"Always"**. Not optional: if DriveDeck is not the Home app, the Waze map
   pauses whenever you touch the dashboard (see point 6 under "Fixed at home" below).
8. If the connection drops: reconnect the unit's Wi-Fi, run `./connect.sh` again (it fetches the log
   with the reason for the drop).

## Undo

| What | Command / where |
|---|---|
| Remove DriveDeck (Home goes back to the unit's launcher) | `adb uninstall dev.drivedeck` |
| Remove Aurora | `adb uninstall com.aurora.store` |
| Animations / packages disabled on 16 Oct | the author's own undo notes (not in this repository) |
| **At the end** | Developer options → **USB debugging OFF** |

## Open after the session of 17 Oct, 16:26

- **Session `s-1017-162610` is NOT valid.** `activityview`, `hwc_off` and `texture` FAILED, but the
  captures show the unit's home screen, not DriveDeck: the judge measured the wrong screen.
  `texture_fix` (the decisive test) never ran. vdfix is still unproven on the unit.
- **The cause, from the log:** coming back to DriveDeck, the slot restarted an app that already had a task
  (Settings in the test) → `am_focused_stack ... bringingFoundTaskToFront returnToHome` → the unit's
  launcher (the home stack) ends up over DriveDeck. The emulator did not show it because DriveDeck was the
  Home app there. Probably the same with Waze while DriveDeck is not the default Home.
- **To do at home, before the car:**
  1. the slot must stop sending `startActivity` for an existing task, only `moveToDisplay`; reproduced on
     the emulator with the stock launcher as Home (FAIL before, PASS after);
  2. `car-verdict.sh`: if DriveDeck is not on top at capture time, the step is INVALID, not FAIL;
  3. a single DriveDeck instance (launcher icon + Home create two).

### Fixed at home, evening of 17 Oct (verified on the emulator, NOT yet on the unit)

1. **The launcher over DriveDeck: fixed.** The mechanism is confirmed: a task started with
   `NEW_TASK|TASK_ON_HOME` (the way a launcher starts apps) brings Home to the front on any `startActivity`
   of it or move of it (`reparent ... returnToHome`). The slot now moves the existing task without
   restarting it, then puts DriveDeck back on top if anything came over it. `./repro-return-home.sh`,
   6 rounds each: old APK + `FLAGS=0x10004000` **0/6**, new APK **6/6**; the control with a plain task is
   6/6 on both. Whether the unit's launcher really sets that flag shows in `<step>.activities.txt` (`flg=`).
2. **`car-verdict.sh`: a step with DriveDeck covered is INVALID and is retried on the next run.**
   On the emulator with the old APK: 4/4 INVALID; with the new one: judged normally. It also waits for the
   old virtual display to go after `force-stop`. Otherwise the slot's app falls over the new DriveDeck (a
   race found in the harness, which could have spoiled the car rounds too).
3. **One instance:** start from the icon + Home → old APK: 3/3 with two dashboards; new: 3/3 with one.
4. **Waze started cold left the card.** Waze opens its main screen by itself, with no display, and it
   landed full screen over DriveDeck: old APK 3/3. Now the slot brings it back for 20 s after starting it:
   3/3 in the card. Tapping Waze in the dock during that time leaves it full screen (2/2).
5. **The Speed card is now "Outside"**: the car's outside sensor, read from `forfan.user.info`, which the
   unit's own app writes. In the car, the `temp` step records it: it must be the same number as in the
   unit's status bar.
6. **The Waze map froze after any tap on the dashboard.** On Android 9 one activity is resumed at a time:
   the tap gave the focus to DriveDeck, Waze was paused and its map stopped drawing. Old APK: 0 new map
   frames with the car moving, 3/3 rounds. Now DriveDeck hands the focus back after 300 ms: 7/7 rounds with
   a live map (44–97 frames), `./repro-map-pause.sh`. **Only with DriveDeck as Home:** otherwise, with a
   Waze started from the unit's launcher, focusing Waze brings the launcher over the dashboard. DriveDeck
   detects that and keeps the focus, so the map pauses. **Watch in the car:** on the emulator Waze now
   crashes more often, because it draws more (the crash is in the emulator's GL). If it crashes often on
   the unit too, the pause/resume on every tap is the suspect.

Known limit: the judge measures coverage only. On the emulator, `texture_fix` PASSES although the image is
stretched, so in the car look at the capture too.

**Next session: `./car-verdict.sh --fresh`** (the old session has its `install` step marked done with the
old APK).

## DriveDeck vs CarPlay: which is faster (measured, not guessed)

The same instrument for both: `fps-probe.sh` swipes the map the same way and reads from SurfaceFlinger
when each frame was shown. DriveDeck is measured by `car-verdict.sh` itself (the `fps` step). CarPlay:

1. Start CarPlay (**wired if possible**: wireless can take over the unit's Wi-Fi and ADB goes with it; the
   probe keeps going regardless, the results are just collected later). Open **Waze in CarPlay**, on the
   map, no route (as in DriveDeck).
2. `./fps-probe.sh start carplay 60 20`: 20 s to get to the map, then 60 s of swipes. Do not touch the
   screen meanwhile. The probe swipes only while CarPlay has the focus; otherwise it skips and logs it.
3. When ADB is back: `./fps-probe.sh pull carplay`. Compare the CarPlay video layer with the DriveDeck
   layer from the `fps` step.

**Touch → response (slow-motion video).** iPhone: Camera → **Slo-mo 240 fps**, phone held still, the
unit's screen and the finger in frame. With the Waze map open: **5 taps** on the search box ("Where to?"),
with a pause between them. Once in DriveDeck, once in CarPlay. Count the frames between the touch and the
first change on screen (1 frame = 4.2 ms), e.g. with `ffmpeg`.

**Ignition → usable map.** A normal video, from turning the ignition on until the Waze map responds to a
touch. Once with DriveDeck as Home, once with CarPlay.
