# TV Limiter privacy policy

Last updated: November 22, 2026

TV Limiter is a parental control app for Android TV. A parent uses it to set daily time limits for apps on the TV. This policy explains what the app records and where that information goes.

## What the app records

To enforce limits and show the screen time report, TV Limiter stores these on the TV:

- The limits the parent sets: which apps have limits, and the minutes allowed on each day of the week.
- How long each app was in front each day, kept for up to 400 days so the report can show history.
- Extra time the parent added today.
- The parent passkey, stored only as a salted hash. The passkey itself is never saved.

## Where it goes

Nowhere. TV Limiter does not have the internet permission, so Android does not let it send anything off the TV. There are no accounts, ads, analytics, crash reports or third-party services. The developer never receives any of this information.

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
