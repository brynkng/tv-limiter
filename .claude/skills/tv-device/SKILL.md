---
name: tv-device
description: Install, open, drive and screenshot TV Limiter on the Android emulator or the user's TV over adb. Use whenever a change needs checking on a device, a screenshot is needed, or adb misbehaves.
allowed-tools: Bash(.claude/skills/tv-device/scripts/device.sh emu *)
---

# Driving a device

Use `.claude/skills/tv-device/scripts/device.sh <emu|tv> <command>`. Run it with no arguments to see the commands. Screenshots land in `local/shots/<emu|tv>/` (git ignores `local/`). Read the PNG to see it.

## Which device

- **Emulator first.** It is `emulator-5554`. Start it with `~/Library/Android/sdk/emulator/emulator -avd locked37 -no-audio -no-boot-anim` in the background, then wait for `device.sh emu shell getprop sys.boot_completed` to print `1`.
- **The TV only when the user says so.** It is the family TV: someone may be watching, it holds the real limits and passkey, and its home screen is behind that passkey. Its address is in `local/device.env`. Never type a passkey on it and never uninstall or clear its data. Before opening anything on it, run `device.sh tv focus`: if anything but the home screen or the screensaver is in front, someone may be watching, so ask first.

## Build and install

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleGithubDebug
.claude/skills/tv-device/scripts/device.sh emu install
.claude/skills/tv-device/scripts/device.sh emu grant     # first install only
.claude/skills/tv-device/scripts/device.sh emu launch
```

`install` uses `adb install -r`, which keeps the app's data. A release or Play build has a different signature from a debug build, so moving between them needs an uninstall, which deletes the limits, history and passkey. Ask first.

## YouTube detection recorder (debug builds only)

Debug builds include a notification listener that logs what YouTube tells Android while a video plays, for TubeGuard's detection test. Grant it once per device with `device.sh <emu|tv> detect-grant`, clear the log with `detect-clear` before each scenario, and save it with `detect-pull NAME`. Release builds leave it out.

## Quirks that cost time before

- **adb keeps disconnecting:** a running Unity Editor starts its own older adb on port 5037. The script uses port 5038; keep it that way.
- **The first D-pad key may do nothing on the emulator:** `locked37` is a phone image, and in touch mode it swallows the first key. It is not always in touch mode (after `adb root` it was not), and then a throwaway key counts as a passkey press and uses up a try. Check first: `uiautomator dump` and look for `focused="true"`. Send one throwaway `DOWN` only when nothing is focused.
- **An emulator started outside the script** registers with the adb server on 5037, not 5038. If `device.sh` says `emulator-5554 not found`, run it with `ANDROID_ADB_SERVER_PORT=5037`.
- **Testing the screensaver on the emulator:** it works, with root and a faked charger (screensavers only run while charging). `adb root`, then `settings put secure screensaver_enabled 1`, `settings put secure screensaver_components com.android.dreams.basic/com.android.dreams.basic.Colors`, `dumpsys battery set ac 1`, `dumpsys battery set status 2`, then `cmd dreams start-dreaming` (check `dumpsys power` shows `mWakefulness=Dreaming`), `cmd dreams stop-dreaming`, and `dumpsys battery reset`.
- **Portrait screenshots:** turn off auto-rotate first, or the rotation is ignored: `device.sh emu shell settings put system accelerometer_rotation 0`, then `device.sh emu shell settings put system user_rotation 1`, then `device.sh emu shell wm size 1080x1920`.
- **Unknown emulator passkey:** stop the app, delete it, and set a new one on the next launch (debug builds only, emulator only):
  `device.sh emu shell am force-stop io.github.brynkng.tvlimiter`, then `device.sh emu shell run-as io.github.brynkng.tvlimiter rm files/pin.json`.
- **`adb root` restarts the whole system.** Avoid it. `run-as io.github.brynkng.tvlimiter` works only with debug builds.
- **A limited app to test with:** use Chrome. Calendar on the emulator stops at a Google sign-in.
- **Screens relock:** `GuardedActivity` screens ask for the passkey again after going to the background. The emulator's passkey is usually `UP UP UP DOWN DOWN DOWN` (see the touch mode note about a throwaway key). If one try fails, stop and reset it as described above rather than guessing again. On a fresh install, enter it twice to set it.
- **After pressing a button, focus may jump back to the first row** because the screen redraws. Check where the focus outline is before sending more keys.
- **Seed data:** `local/seed/` holds limits, usage and history JSON for the emulator. Push it with `run-as` into the app's `files/` folder while the app is stopped.
