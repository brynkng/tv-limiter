# Profiles mode

## Goal

A TV runs in one of two modes.

- **Shared mode** (today's behavior, the default): one set of limits for everyone. The passkey comes up only when time runs out.
- **Profiles mode**: a "Who's watching" screen covers the TV every time it starts or wakes. Each child's profile has its own limits, usage, history and passkey. A young child's profile may skip the passkey; older children need theirs. The Parent tile takes the parent passkey and starts parent mode.

This is the TV Limiter half of TubeGuard's R35 (`~/workspace/tubeguard/docs/plans/2026-11-22-1946-feat-family-youtube-monitor-plan.md`). YouTube checks per profile come later, through TubeGuard.

## Decisions

- **Today's data becomes the shared profile, unmoved.** `limits.json`, `usage.json`, `history.json` and `pin.json` stay where they are and keep working in shared mode. Each child profile's files go in `files/profiles/<id>/`. No migration, and switching back to shared mode finds the shared limits untouched.
- **The parent passkey stays `pin.json`.** Child passkeys are separate records in each profile's folder, with the same hashing and attempt limits (`PinGuard`).
- **Age bands, not ages.** Each child profile has a band: Young (about 6), Older (about 10), Teen (about 13). Only Young may skip the passkey. TubeGuard later replaces this with "the TV's strictest profile", which needs content settings TV Limiter doesn't have.
- **The viewer lives in memory**, like parent mode. A restart of the app, screen off or the screensaver clears it, so the picker comes back. This is the same choice as parent mode: a crash never leaves the TV unlimited.
- **A new profile starts with a copy of the shared limits**, so a parent edits rather than builds from nothing.
- **Grants go to the viewer's profile.** The block screen's "+15 min" and "rest of today" apply to whoever is watching.
- **The launcher icon opens the picker in profiles mode.** The picker has a "Settings" button, behind the parent passkey as today. In shared mode the icon opens settings as today. The launcher activity keeps its name, so a TV's pinned app icon keeps working.

## Units

### 1. Profiles model and storage

- `domain/Profiles.kt`: `Mode` (SHARED, PROFILES), `AgeBand` (YOUNG, OLDER, TEEN, with a label and whether the passkey is optional), `Profile(id, name, band)`, `ProfilesFile(mode, profiles)`.
- `domain/Viewer.kt`: the in-memory viewer, like `ParentMode`: `current: String?`, `start(id)`, `end()`, one shared instance.
- `domain/ViewerGate.kt`, pure: from mode, viewer and parent mode, decide each tick whether to show the picker (profiles mode, nobody picked, not parent mode), count nothing (parent mode), or count for a profile id (the shared id in shared mode).
- `data/ProfilesRepository.kt` for `profiles.json`.
- `LimitsRepository`, `UsageRepository`, `HistoryRepository` and `PinRepository.guard` take a profile id, one cached instance per id, with the shared id (`Profiles.SHARED_ID`) mapping to today's file paths. Callers that mean the shared profile or the parent passkey keep working with a default.
- Tests: `ViewerGate` in every combination, the age-band passkey rule, the profile file paths (shared id gives today's paths).

### 2. Tracking and the picker

- `TrackingService`: screen off and the screensaver also end the viewer. Screen on and the screensaver ending show the picker in profiles mode. Each tick asks `ViewerGate`. When it says picker, a non-exempt app is covered by the picker and nothing is counted; otherwise usage and limits come from the viewer's profile.
- `ui/WhoActivity.kt`, a full-screen picker built like `BlockActivity` (single instance, its own task, Back goes home, `visible` flag so the service doesn't relaunch it):
  - one tile per profile, then a Parent tile, then Settings;
  - a Young profile starts at once, any other opens `PinPadView` against that profile's guard;
  - Parent checks the parent guard and starts parent mode;
  - Settings opens `SettingsActivity` (which asks for the parent passkey itself).
- `BlockActivity` takes the profile id in its intent and uses that profile's limits and usage.
- `SettingsActivity` in profiles mode, opened from the launcher without coming from the picker, sends the viewer to the picker and finishes in `onCreate`.

### 3. Managing profiles in settings

- A "Profiles" section on the home screen: the mode switch (shared or profiles), the list of child profiles, and "Add a profile".
- A profile editor: name, age band, set or change passkey (`PinSetupActivity` for that profile; required unless Young), delete (with a confirmation that its limits and history go too).
- In profiles mode the home screen shows one profile's limits at a time, with a row to choose which. The limit editor, app picker and report take the profile id as an intent extra.
- Switching to profiles mode needs at least one child profile, and every non-Young profile needs a passkey before the switch is allowed.

## Verification

- Unit tests for every domain change; `./gradlew testGithubDebugUnitTest testPlayDebugUnitTest`, both lints and the debug build pass.
- On the emulator (the `tv-tester` agent):
  - shared mode behaves exactly as before;
  - in profiles mode with a Young and a Teen profile, waking the TV shows the picker, Young starts without a passkey, Teen needs its own, Parent starts parent mode;
  - each profile's time counts only against its own limits, and running out blocks only that profile;
  - the screensaver brings the picker back.

## Not in this plan

- YouTube checks per profile and the TubeGuard link (TubeGuard U6).

## Pricing

Profiles in TV Limiter are free (user-directed 2026-11-24). The paid part is YouTube checks per profile, through TubeGuard.
