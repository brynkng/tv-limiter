# TV Limiter

An Android TV app that sets daily time limits for each app and for the TV as a whole. Each limit can have a different value for every day of the week. When time runs out, the app is covered by a block screen, and only someone who knows the PIN can add more time.

## What it does

- **App limits.** Pick any launchable app and give it a number of minutes for each weekday. Any day can be set to "no limit".
- **A whole-TV limit.** One limit for all viewing, with the same weekday table. It counts time in every app except the exempt ones listed below. It starts out turned off.
- **The tightest limit wins.** A minute in an app counts against that app's limit and against the TV limit. Whichever has less time left decides when to block.
- **A block screen that says why.** It names the limit that ran out: "YouTube is done for today" or "TV time is used up". From there the viewer can only go home or ask for the PIN. Leaving the block screen never resumes the blocked app.
- **PIN grants.** After the PIN, the owner picks +15 min, +30 min, +60 min, or rest of today. The extra time goes only to the limit that ran out, and only for today.
- **Daily reset.** Usage and grants reset at local midnight, and each day uses that weekday's value.
- **Survives reboots.** Tracking starts again after a reboot and keeps the time already used today.

An example: YouTube has 60 minutes on Tuesdays and the TV has 180. By the afternoon, YouTube has been on for 50 minutes and other apps for 120. Ten more minutes of YouTube uses up both limits at once. The block screen says "YouTube is done for today", and opening any other app then shows "TV time is used up". If the owner grants YouTube +30 min, the block screen moves straight on to the TV limit's grant menu without asking for the PIN again, because both limits ran out together. Each grant is recorded for its own limit.

### Exempt apps

This app and the home launcher never count and are never blocked, so there is always a way back to the PIN. Everything else counts toward TV time, including the system Settings app.

## Build

You need Android Studio's bundled JDK 21 (the system default Java may be older).

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug        # builds app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # runs the unit tests on your computer
```

The first build downloads a few Gradle plugins, so it needs network access.

## Install on the TV

1. On the TV, open Settings, find the build number (usually under Device Preferences > About), and press OK on it seven times to turn on developer options.
2. In developer options, turn on USB debugging or network debugging, and note the TV's IP address.
3. From your computer:

   ```sh
   adb connect <tv-ip>
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

   Accept the debugging prompt on the TV with the remote.

4. Grant the two permissions the app needs. Some TVs hide these screens in Settings, so `adb` is the reliable way:

   ```sh
   adb shell appops set dev.tvlimiter GET_USAGE_STATS allow
   adb shell appops set dev.tvlimiter SYSTEM_ALERT_WINDOW allow
   ```

   Usage access lets the app see which app is in front. "Display over other apps" lets it bring up the block screen.

5. **Turn off USB and network debugging, and developer options, when you are done.** While debugging is on, anyone on your network with an `adb` app can connect, accept the prompt with the remote, and clear or edit this app's data.

## First run

Open TV Limiter from the home screen. With no PIN set, it asks you to choose one: 4 to 8 digits, entered twice. If the two entries don't match, you start again.

After that, opening the app asks for the PIN and then shows the settings screen:

- The whole-TV limit first, then each app limit with today's used and remaining time.
- **Add app limit** opens a list of installed apps.
- The weekday editor has one row per day. Left and right change the value in 15-minute steps, up to 24 hours. Going below 0 sets the day to "no limit". There are buttons to copy Monday to the other weekdays, copy Saturday to Sunday, turn the limit on or off, and delete it.
- A permissions panel shows whether usage access and "display over other apps" are granted, with a button to the matching system screen and the `adb` commands above.

The settings screen locks again whenever the app goes to the background, so returning to it from the recent apps list asks for the PIN.

## The PIN pad

The pad is built so that watching the remote doesn't give away the PIN.

- A row shows the ten digits in a shuffled order, then **Delete** and **Done**. The order changes every time the pad opens.
- Left and right move the highlight along the row. OK picks the highlighted cell.
- After each digit, the highlight jumps to a random other digit, so the number of presses doesn't reveal which digit was chosen.
- The field above the row shows one ▲ per digit entered, never the digits.
- **Done** works once 4 digits are entered. More than 8 digits are ignored. The number keys on the remote do nothing.
- Back clears a partial entry. Back again leaves the pad.
- A wrong PIN clears the entry, reshuffles the row, and shows how many tries are left. After 5 wrong PINs in a row, entry is locked for 60 seconds with a countdown. The lockout is shared by every PIN prompt in the app and isn't reset by leaving the screen.

The pad does not hide the PIN from someone watching the TV screen, because the highlight sits on the chosen digit when OK is pressed. **Enter the PIN where the viewer can't see the screen.**

## What it does not protect against

- **Changing the clock.** Moving the TV's clock forward starts a new day's limits early.
- **The TV's own Settings.** In Settings > Apps > TV Limiter, anyone with the remote can press **Clear storage** (this deletes the limits and the PIN, and the next launch lets them set a new PIN), **Force stop** (tracking stops until the next reboot or until the app is opened), or revoke usage access or "display over other apps" (tracking or blocking silently stops). Settings is only blocked once TV time runs out.
- **Leaving debugging on.** See step 5 above.
- **Watching the screen** while the PIN is entered.
- **Picture-in-picture or background audio.** A blocked app that keeps playing in a small window or in the background is not stopped.
- **Uninstalling** the app.

## Forgotten PIN

There is no recovery code yet. Turn debugging back on, connect with `adb`, and run:

```sh
adb shell pm clear dev.tvlimiter
```

This clears the PIN and all limits. Grant the two permissions again afterward, then set up a new PIN.

## Project layout

```text
app/src/main/java/dev/tvlimiter/
  domain/   limits, weekday tables, the tightest-limit evaluation, usage counting, PIN hashing, the pad's state
  data/     JSON files for limits, today's usage and the PIN, written atomically
  service/  the tracking service, foreground app detection, boot start
  ui/       settings screens, the block screen, the PIN pad
app/src/test/java/dev/tvlimiter/  unit tests that run on your computer
docs/       ideation and the implementation plan
```
