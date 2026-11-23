# Google Play listing

Everything to paste into Play Console for TV Limiter, plus the steps in order. Upload the Play version (`./gradlew bundlePlayRelease`, file `app/build/outputs/bundle/playRelease/app-play-release.aab`), never the GitHub version, which has the donation button.

## Steps

1. **Create a developer account** at https://play.google.com/console/signup. Choose a personal account. It costs $25 once and needs ID verification, which can take a few days.
2. **Create the app.** Name "TV Limiter", default language English (United States), App, Free.
3. **Set up the store listing, app content and declarations** with the text below.
4. **Turn on Android TV.** In Test and release > Advanced settings > Form factors, add Android TV. TV apps get a separate TV quality review.
5. **Closed test.** New personal accounts must run a closed test with at least 12 testers who stay opted in for 14 days in a row before production access is allowed. Create a closed testing track, upload the bundle, add the testers' Google accounts (a Google Group works), and send them the opt-in link. Testers need an Android TV or Google TV device to install it.
6. **Apply for production** from the dashboard once the 14 days are up, answering the questions about the test.
7. **Release to production.**

When asked about app signing, accept Play App Signing. The key in `keystore.properties` is then only the upload key, and Google can reset it if it is ever lost.

## Store listing

**App name** (30 characters max)

> TV Limiter: Screen Time

**Short description** (80 characters max)

> Daily time limits for each app on your Android TV, protected by a passkey.

**Full description** (4000 characters max)

> TV Limiter lets a parent set daily screen time limits on an Android TV or Google TV. Pick an app, choose how many minutes it gets on each day of the week, and TV Limiter covers it with a friendly "time is done" screen when the time runs out.
>
> SET LIMITS YOUR WAY
> • A limit for any app, with a different amount for each weekday
> • A limit for the whole TV, on top of app limits; whichever runs out first wins
> • Copy Monday to every day or to the other weekdays in one press
>
> A PASSKEY YOUR CHILD CAN'T WATCH YOU TYPE
> The passkey is six presses of the arrow buttons on the remote. The screen only shows dots, never which buttons you pressed, so you can enter it with your child sitting next to you. Wrong guesses lock the passkey for a while.
>
> EXTRA TIME WHEN YOU SAY SO
> When time runs out, your child can ask you for more. Enter the passkey to add 15, 30 or 60 minutes, or the rest of the day. You can take added time back later.
>
> PARENT MODE
> Want to watch something yourself? After the passkey, choose parent mode: nothing is counted or blocked until the screensaver starts or the TV goes to sleep, so your viewing never uses up your child's time.
>
> SEE WHERE THE TIME GOES
> The screen time report charts TV time by day, week or month, split by app, with the most used apps for each period.
>
> PRIVATE BY DESIGN
> TV Limiter has no internet permission. Everything it records stays on your TV. No accounts, no ads, no tracking.
>
> FREE AND OPEN SOURCE
> TV Limiter is free and its source code is public under the GPL-3.0 license: https://github.com/brynkng/tv-limiter
>
> TV Limiter tracks how long apps are used on the TV so it can enforce the limits a parent sets. It shows a notification while tracking and is meant for parents managing their own family's TV.

**App category:** Parenting

**Contact email:** required by Play and shown publicly. Use an address you are happy to publish.

**Privacy policy URL:** https://brynkng.github.io/tv-limiter/privacy

## Graphics

| Asset           | Size                              | File                                  |
| --------------- | --------------------------------- | ------------------------------------- |
| App icon        | 512 x 512 PNG                     | `docs/play-store/icon-512.png`        |
| Feature graphic | 1024 x 500 PNG or JPG             | `docs/play-store/feature-graphic.png` |
| TV banner       | 1280 x 720 PNG or JPG             | `docs/play-store/tv-banner.png`       |
| TV screenshots  | 1920 x 1080 (at least 1, up to 8) | `docs/play-store/screenshots/`        |

## App content answers

**Privacy policy:** the URL above.

**Ads:** No, the app does not contain ads.

**App access:** All functionality is available without special access. Note for reviewers: "On first launch the app asks you to choose a six-press arrow passkey. Any passkey works. Enter it again to confirm, then grant usage access and display over other apps when the home screen asks."

**Content rating:** fill in the IARC questionnaire with category "Utility, Productivity, Communication, or Other" and answer No to violence, sexuality, language, controlled substances, gambling, user interaction and sharing of location. Expect an Everyone rating.

**Target audience:** 18 and over only. The app is used by parents, so do not select any age group under 13; that would put it under the Families policy, which it is not designed for.

**News app:** No.

**Data safety:** the app collects no data and shares no data. Data that never leaves the device does not count as collected under Play's definition, and the app has no internet permission.

- Does your app collect or share any of the required user data types? **No**
- Is all of the user data encrypted in transit? Not applicable; nothing is transmitted.
- Do you provide a way for users to request that their data is deleted? Users delete it by uninstalling the app or clearing its storage.

**Government app / financial features / health:** No.

## Permission and policy declarations

**Foreground service (special use).** Play asks why the app uses `FOREGROUND_SERVICE_SPECIAL_USE`:

> TV Limiter is a parental control app. Its foreground service checks every two seconds which app is on screen and adds that time to the daily limits a parent has set. When a limit runs out, it shows a block screen over the app. The service has to keep running while the TV is in use, because limits can only be enforced while time is being counted. Without it, a child could keep watching past the limit set by the parent. The user sees an ongoing notification while it runs.

Play may also ask for a short video showing the feature. Record the TV: set a 1-minute app limit, open the app, and show the block screen appearing.

**Monitoring apps.** The manifest declares `isMonitoringTool` as `child_monitoring`, as the [Play Console help](https://support.google.com/googleplay/android-developer/answer/12955211) requires for parental control apps. The listing above says plainly that the app tracks app usage for parents. Play Protect may show users a notice that a monitoring app is installed.

**Usage access and display over other apps** have no separate declaration form, but the listing and the in-app permission screen both explain what they are for.
