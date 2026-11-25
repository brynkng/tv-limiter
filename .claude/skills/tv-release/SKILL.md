---
name: tv-release
description: Release a new version of TV Limiter to GitHub, and prepare the Google Play bundle. Only when the user runs /tv-release.
disable-model-invocation: true
argument-hint: "<version, e.g. 1.2.0>"
---

# Releasing TV Limiter $ARGUMENTS

Each step that publishes something needs the user's go-ahead, unless they already gave it for this release.

1. **Version.** In `app/build.gradle.kts`, set `versionName` to the new version and add 1 to `versionCode`. Play rejects a bundle whose `versionCode` it has seen before. Commit it as `build: release <version>` after the feature, test and docs commits.
2. **Build.** Run `.claude/skills/tv-release/scripts/build-release.sh <version>`. It stops if the tree isn't clean, the version doesn't match, the tag exists, or `keystore.properties` is missing. Then it runs the tests and lint for both versions and builds the signed APK and Play bundle. Fix any failure; don't skip it.
3. **Push.** `git push origin feat/tv-app-limiter:main`.
4. **GitHub release.** Write short notes for the user, in plain words, listing only what changed since the last release (`gh release view -R brynkng/tv-limiter` shows the previous one's style). Then:

   ```sh
   gh release create v<version> -R brynkng/tv-limiter --target main --title "TV Limiter <version>" --notes-file <notes> local/release/tv-limiter-<version>.apk
   ```

5. **Play listing.** If the change is visible to parents, update `docs/play-store/listing.md` and tell the user which text to paste into Play Console.
6. **Play bundle.** Tell the user the bundle path, `app/build/outputs/bundle/playRelease/app-play-release.aab`. Until a Play service account exists, the user uploads it in Play Console by hand. After the first manual upload, `fastlane supply --aab <bundle> --track alpha` (the closed test track) can do it; setting that up needs the user's service account JSON, which must stay out of git.

## Rules

- Never print, read or commit `keystore.properties` or the `.jks` key. Losing them means a Google upload key reset.
- Commits use the noreply author already set in this repo, with no Co-Authored-By or "generated with" lines.
- The user's TV runs a debug build. Don't install a release build on it: different signature, so it needs an uninstall that wipes the family's data.
