# TV Limiter

Android TV parental-controls app: daily limits per app and for the whole TV, protected by an arrow-key passkey. Kotlin with programmatic Views (no XML layouts, no androidx). Start with `README.md`; the Play Store text and steps are in `docs/play-store/listing.md`.

## Commands

Set `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` before `./gradlew` (the system Java is too old).

| Task                      | Command                                                   |
| ------------------------- | --------------------------------------------------------- |
| Unit tests, both versions | `./gradlew testGithubDebugUnitTest testPlayDebugUnitTest` |
| Lint                      | `./gradlew lintGithubDebug lintPlayDebug`                 |
| Debug build               | `./gradlew assembleGithubDebug`                           |
| Signed APK / Play bundle  | `./gradlew assembleGithubRelease` / `bundlePlayRelease`   |

There are two versions: `github` has the Venmo donate screen, `play` leaves it out because Play allows payments only through its own billing.

## Working here

- **Check on a device:** the `tv-device` skill. To check a UI change end to end, hand it to the `tv-tester` agent.
- **Release:** `/tv-release <version>`.
- **Code layout:** `domain/` holds pure logic with unit tests, `data/` holds JSON file storage, `service/` holds the tracking service, `ui/` holds the activities and their view models.
- **Tests:** every domain or view model change gets a unit test. Tests run before Claude finishes whenever Kotlin files changed.
- **Commits:** only when asked. Feature commits first, then tests, then docs, in conventional commit format (`feat(scope): ...`).
- **Signing key:** never open, print, edit or commit `keystore.properties` or any `.jks` file. A hook blocks it.
- **Writing:** README and listing text use plain words a parent understands, with no em dashes.
