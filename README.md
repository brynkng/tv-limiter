# TV Limiter

An Android TV app that sets daily time limits for each app and for the TV as a whole. Each limit can have a different value for every day of the week. When time runs out, the app is covered by a block screen, and only someone who knows the passkey can add more time.

TV Limiter is free and open source under the GPL-3.0 license. The versions you can download have no internet permission, so nothing they record ever leaves the TV. See the [privacy policy](https://brynkng.github.io/tv-limiter/privacy).

## Download

- **Google Play:** coming soon.
- **GitHub:** download `tv-limiter-<version>.apk` from the [latest release](https://github.com/brynkng/tv-limiter/releases/latest) and install it with `adb` as described under [Install on the TV](#install-on-the-tv).

## Support

TV Limiter is free. If it helps your family, you can leave a tip on [Venmo (@bk.ceramics)](https://venmo.com/u/bk.ceramics). The GitHub version of the app also has a **Support TV Limiter** button with a QR code. The Google Play version leaves it out, because Play only allows payments through its own billing.

## What it does

- **App limits.** Pick any launchable app and give it a number of minutes for each weekday. Any day can be set to "no limit".
- **A whole-TV limit.** One limit for all viewing, with the same weekday table. It counts time in every app except the exempt ones listed below. It starts out turned off.
- **The tightest limit wins.** A minute in an app counts against that app's limit and against the TV limit. Whichever has less time left decides when to block.
- **A block screen that says why.** It names the limit that ran out: "YouTube is done for today" or "TV time is done for today". From there the child can only go home or ask a parent for more time. Leaving the block screen never resumes the blocked app.
- **Adding time.** After the passkey, the owner picks +15 min, +30 min, +60 min, or rest of today. The extra time goes only to the limit that ran out, and only for today.
- **Parent mode.** After the passkey, the block screen also offers **Parent mode until the TV sleeps**. Nothing is counted or blocked until the screensaver starts or the screen turns off, so a parent can watch without using up the child's time. It can also be started or ended early from the home screen. It is kept only in memory, so restarting the app always ends it.
- **Profiles.** Every TV starts with one profile, "Kids", which works just like before: no picker and no extra presses. Add a profile for each child and a "Who's watching" screen covers the TV every time it starts or wakes. Each child picks their name and has their own limits, time used and history. A young child's profile can skip the passkey; older children need their own, so a child can't pick a sibling's more generous limits. The Parent tile takes the parent passkey and starts parent mode.
- **Daily reset.** Usage and grants reset at local midnight, and each day uses that weekday's value.
- **Survives reboots.** Tracking starts again after a reboot and keeps the time already used today.

An example: YouTube has 60 minutes on Tuesdays and the TV has 180. By the afternoon, YouTube has been on for 50 minutes and other apps for 120. Ten more minutes of YouTube uses up both limits at once. The block screen says "YouTube is done for today", and opening any other app then shows "TV time is done for today". If the owner grants YouTube +30 min, the block screen moves straight on to the TV limit's grant menu without asking for the passkey again, because both limits ran out together. Each grant is recorded for its own limit.

### Exempt apps

This app and the home launcher never count and are never blocked, so there is always a way back to the passkey. Everything else counts toward TV time, including the system Settings app.

## TubeGuard (optional)

TV Limiter can also pair with a TubeGuard family and tell the family's parent app which YouTube videos were watched on the TV. This is switched off in the downloads above: it only exists in a build made with a TubeGuard address, so those builds have no internet permission and nothing leaves the TV.

A TubeGuard family can have several children on one TV. Each TV Limiter profile can be linked to one of them: in the profile's editor (after the passkey), **TubeGuard child** lists the children TubeGuard has for this TV, plus **Not linked**. A video is then reported for the child of the profile that was picked when it started, and that child's TubeGuard settings decide the alerts and blocking. A profile that is not linked is reported with no child, and TubeGuard uses the strictest settings among the TV's children. A TV that has only the one "Kids" profile and one TubeGuard child links them on its own. If a parent removes a child from the TV in the parent app, the profile shows **Not linked** again after the TV's next check-in.

In a build that has one, a parent opens **Connect to TubeGuard** on the settings screen (after the passkey), types the 8 digit code from the TubeGuard parent app with the remote, and allows notification access. From then on, each YouTube video is sent with its title, channel, length and how long ago it was watched, within seconds of it starting (a video of a minute or more after 5 seconds of play, a short video other than a Short about 10 seconds after it ends). YouTube plays ads under the video's own title, so if the first length seen was an ad's, the TV sends the video's real length again a moment later as a correction for the same watch. The TV also tells TubeGuard which parts of the video were played, as start and end positions in seconds (for example 0 to 70 and 900 to 940), with the first report, then about every 30 seconds while the video plays, when a minute has been played, and when the watch ends. TubeGuard uses this to have only the parts a child actually watched checked, in 5 minute pieces, and to check a video only after a minute of it has played. The positions come from YouTube's player, and time spent on ads is left out. A short visit to a long video is checked for about as much as was played, not the whole video. Nothing is sent while parent mode is on. A small check-in goes out every few hours saying whether notification access is still on. While connected, the TV's ongoing notification says it is monitored by the family's TubeGuard parent app. **Disconnect** on the same screen stops it and deletes anything not yet sent.

YouTube's Shorts show no title or channel, only a length and a still picture. An item with no title that has played for 5 seconds, and is 4 minutes long or less, is treated as a Short (a regular video is also untitled for a moment while it loads, so a short wait keeps those from counting). Each Short is sent about 5 seconds after it starts, not when it ends, with its length and a small still picture of it (a JPEG about 320 by 180). TubeGuard looks at that one picture to rate the Short, then deletes it. Only Shorts send a picture, nothing else on the screen is ever sent, and a Short of a family on the free plan is never rated, so its picture is not kept at all. If a child's Shorts setting is Block, the TV covers every Short as soon as it is recognized, using the setting TubeGuard last sent it, so it works even before TubeGuard answers. In Shorts the block screen says "Shorts are turned off." and the first button reads **Leave Shorts** ("Press Back to leave Shorts"); allowing a whole channel is not offered, since a Short has none.

When TubeGuard says a video is over one of the family's limits (a banned video or channel, a video like one that was banned, a topic that is switched off, a video rated for older kids than the child's age limit, a Short while Shorts are off, or a category over the child's limit), the TV pauses it and covers it with a block screen. The screen says why in plain words ("This video has a lot of scary or creepy parts.", "This channel is blocked.") and never shows what the parents see. A video rated above the child's age limit says "This video is for older kids (13+)." It offers **Pick another video** (the cover comes off the paused player and a note says to press Back), **Go home**, and **Ask a parent**, which sends the family's parents a notice with the video and the reason and waits on the screen ("Asked. Waiting for a parent.") for them to approve or say no. A parent in the room can instead choose **I'm a parent** and enter the passkey, then allow this video (kept as approved in TubeGuard), allow its channel until the TV sleeps, or start parent mode until the TV sleeps. Only a video that is over a limit is blocked: a video that is only close to one is just an alert, and parent mode blocks nothing. A video TubeGuard has not decided on yet keeps playing, and if TubeGuard can't be reached nothing is blocked. The TV remembers blocked videos for a day, so playing one again is covered at once. It checks with TubeGuard again each time, though, so a block that no longer holds (a ban was removed, a limit was loosened) is dropped, and a block whose reason changed shows the new reason.

```sh
./gradlew assembleGithubRelease -PtubeguardUrl=https://your-tubeguard-address
```

Debug builds default to `http://10.0.2.2:8787`, which an emulator uses to reach a TubeGuard backend running on the same computer.

## Build

You need Android Studio's bundled JDK 21 (the system default Java may be older).

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleGithubDebug        # builds app/build/outputs/apk/github/debug/app-github-debug.apk
./gradlew testGithubDebugUnitTest    # runs the unit tests on your computer
```

There are two versions of the app. `github` has the donation button and `play` does not; otherwise they are the same.

To make signed release builds, create `keystore.properties` in the project folder (git ignores it):

```properties
storeFile=/absolute/path/to/upload-key.jks
storePassword=...
keyAlias=upload
keyPassword=...
```

```sh
./gradlew assembleGithubRelease   # signed APK for GitHub releases
./gradlew bundlePlayRelease       # signed app bundle to upload to Google Play
```

The first build downloads a few Gradle plugins, so it needs network access.

## Install on the TV

1. On the TV, open Settings, find the build number (usually under Device Preferences > About), and press OK on it seven times to turn on developer options.
2. In developer options, turn on USB debugging or network debugging, and note the TV's IP address.
3. From your computer:

   ```sh
   adb connect <tv-ip>
   adb install -r tv-limiter-<version>.apk   # or app/build/outputs/apk/github/debug/app-github-debug.apk
   ```

   Accept the debugging prompt on the TV with the remote.

4. Grant the two permissions the app needs. Some TVs hide these screens in Settings, so `adb` is the reliable way:

   ```sh
   adb shell appops set io.github.brynkng.tvlimiter GET_USAGE_STATS allow
   adb shell appops set io.github.brynkng.tvlimiter SYSTEM_ALERT_WINDOW allow
   ```

   Usage access lets the app see which app is in front. "Display over other apps" lets it bring up the block screen.

5. **Turn off USB and network debugging, and developer options, when you are done.** While debugging is on, anyone on your network with an `adb` app can connect, accept the prompt with the remote, and clear or edit this app's data.

## First run

Open TV Limiter from the home screen. With no passkey set, it asks you to choose one (6 arrow presses), entered twice. If the two entries don't match, you start again.

After that, opening the app asks for the passkey and then shows the home screen:

- The whole-TV limit first, then each app limit. Each row has a bar for the time left today (mint while there is time, pink once it runs out) and notes any time added today.
- **Add an app** opens a grid of installed apps, using each app's TV banner where it has one.
- Choosing a limit opens its edit screen. It shows how today stands and has one row per weekday with a bar for that day's limit. Left and right change the value in 15-minute steps, up to 24 hours. Going below 0 sets the day to "no limit". There are buttons to copy Monday to every day, copy Monday to the other weekdays, copy Saturday to Sunday, turn the limit on or off, and delete it.
- **Remove added time.** When time was added to a limit today from the block screen, its edit screen says how much and offers **Remove added time**. That takes back all of today's added time for that limit, including "rest of today". If the limit is then used up, the app is blocked again within a couple of seconds.
- **See screen time history** shows a chart of TV time by day (last 7), week (last 8) or month (last 6). Left and right on the chart pick a bar; the list beside it shows the most used apps in that period. History starts the first night after this version is installed, and the last 400 days are kept in `history.json`.
- **Change passkey** asks for the current passkey, then the new one twice. Wrong current passkeys count toward the same 5-try lockout.
- When both permissions are granted, the home screen just says "Tracking is on." If one is missing, a card says which, why it's needed, and offers a button to the matching system screen and the `adb` command.

The app locks again whenever it goes to the background, so returning to it from the recent apps list asks for the passkey.

## The passkey

The passkey works like an Xbox passkey: it's 6 presses of the remote's arrow buttons, for example up, up, left, down, right, up. That gives 4,096 possible passkeys, and after 5 wrong tries entry is locked for a minute.

- The screen shows only six circles that fill in as you press. Every arrow fills the next circle the same way, so someone watching the TV can't tell which arrows you pressed. Only someone watching your hand on the remote could.
- The passkey is checked as soon as the sixth press is in. There is no OK step. OK, the number keys, and holding an arrow down do nothing extra.
- Back clears a partial entry. Back again leaves the screen.
- A wrong passkey clears the entry and shows how many tries are left. After 5 wrong tries in a row, entry is locked for 60 seconds with a countdown. The lockout is shared by every passkey prompt in the app and isn't reset by leaving the screen.

## What it does not protect against

- **Changing the clock.** Moving the TV's clock forward starts a new day's limits early.
- **The TV's own Settings.** In Settings > Apps > TV Limiter, anyone with the remote can press **Clear storage** (this deletes the limits and the passkey, and the next launch lets them set a new passkey), **Force stop** (tracking stops until the next reboot or until the app is opened), or revoke usage access or "display over other apps" (tracking or blocking silently stops). Settings is only blocked once TV time runs out.
- **Leaving debugging on.** See step 5 above.
- **Watching the remote** while the passkey is entered.
- **Picture-in-picture or background audio.** A blocked app that keeps playing in a small window or in the background is not stopped.
- **Uninstalling** the app.

## Forgotten passkey

There is no recovery code yet. Turn debugging back on, connect with `adb`, and run:

```sh
adb shell pm clear io.github.brynkng.tvlimiter
```

This clears the passkey and all limits. Grant the two permissions again afterward, then set up a new passkey.

## Project layout

```text
app/src/main/java/dev/tvlimiter/
  domain/   limits, weekday tables, the tightest-limit evaluation, usage counting, passkey hashing, passkey entry state
  data/     JSON files for limits, today's usage and the passkey, written atomically
  service/  the tracking service, foreground app detection, boot start
  ui/       settings screens, the block screen, passkey entry, the screen time report
app/src/github/  the donation screen, in the GitHub version only
app/src/play/    an empty stand-in for it in the Google Play version
app/src/test/java/dev/tvlimiter/  unit tests that run on your computer
docs/       privacy policy (served by GitHub Pages), Play Store listing, ideation and the implementation plan
```

## License

TV Limiter is licensed under the [GNU General Public License v3.0](LICENSE). You can use, study, change and share it; if you publish a changed version, it must use the same license and include its source.

## Fonts

The app uses [Lexend](https://www.lexend.com/), under the SIL Open Font License. The license is in `third_party/lexend/OFL.txt`.
