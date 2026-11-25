# TV Limiter privacy policy

Last updated: November 26, 2026

TV Limiter is a parental control app for Android TV. A parent uses it to set daily time limits for apps on the TV. This policy explains what the app records and where that information goes.

## What the app records

To enforce limits and show the screen time report, TV Limiter stores these on the TV:

- The limits the parent sets: which apps have limits, and the minutes allowed on each day of the week.
- How long each app was in front each day, kept for up to 400 days so the report can show history.
- Extra time the parent added today.
- The parent passkey, stored only as a salted hash. The passkey itself is never saved.

## Where it goes

Nowhere. TV Limiter does not have the internet permission, so Android does not let it send anything off the TV. There are no accounts, ads, analytics, crash reports or third-party services. The developer never receives any of this information.

This is true of the versions on Google Play and GitHub today. A separate version, which a parent can connect to a TubeGuard family, works differently. It is described in the next section and is not available yet.

## If you connect TV Limiter to TubeGuard

TubeGuard is a service from the same developer that tells parents what their children watched on YouTube and whether it fits the family's values. Only a version of TV Limiter built with TubeGuard can connect to it. That version has the internet permission, and sends nothing until a parent connects it with a code from the TubeGuard parent app, behind the parent passkey.

Once connected, TV Limiter sends TubeGuard:

- For each YouTube video watched on the TV: its title, its channel, its length, and when it was watched. Nothing is sent while parent mode is on.
- For each of those videos, which parts of it were played, as start and end positions in seconds from the start of the video. They are sent with the report, then about every 30 seconds while the video plays, and when it ends. TubeGuard uses them to have only the parts that were watched checked. Time spent on ads is left out.
- For a YouTube Short (which has no title), its length and one small still picture of it, about 320 by 180 pixels. TubeGuard looks at that picture to rate the Short and deletes it as soon as it has done so. It is never kept after that, and a family on the free plan has its pictures discarded without being rated. Nothing else on the screen is ever captured or sent, and the picture is not sent for regular videos.
- Every few hours, a check-in saying whether TV Limiter can still see what YouTube plays, and the app's version. TubeGuard answers with the names of the TV's children and whether each has Shorts turned off, so the TV can cover a Short right away.
- While a video is being checked, or while the block screen is up, a request asking TubeGuard what it decided about that watch. If a child taps Ask a parent, a request to tell the family's parents about that video. If a parent allows a video with the passkey, a note that this video is allowed. The passkey itself stays on the TV.
- When pairing, the TV's name (as set in its settings) and time zone.

TV Limiter keeps the videos it was told to block on the TV for 24 hours, so a replay is covered at once. It does not send the passkey, your app limits, screen time for other apps, or anything about other apps. TubeGuard's own privacy notice explains how it rates videos and how long it keeps watch history (30 days). While connected, the TV shows an ongoing notice saying it is monitored by your family's TubeGuard parent app. Disconnecting in TV Limiter's settings, or removing the TV in the TubeGuard parent app, stops all reporting.

The app does not allow Android to back up its data, so it is not copied to a cloud backup either.

## Permissions

- **Usage access** lets the app see which app is on screen, so it can count time against limits.
- **Display over other apps** lets it show the block screen over an app whose time ran out.
- **Run in the background and start at boot** keep the limits working after the TV restarts. While tracking, the app shows an ongoing notification where the TV supports one.

## Children

The app is set up and controlled by a parent. It records how long apps are used on the TV, which can include a child's viewing, but that record stays on the TV and is only shown to someone who knows the parent passkey.

## Deleting the information

Uninstalling TV Limiter, or clearing its storage in the TV's Settings, deletes everything it recorded.

## Changes and contact

If this policy changes, the new version will be posted at this address with a new date. Questions can be asked by opening an issue at https://github.com/brynkng/tv-limiter/issues.
