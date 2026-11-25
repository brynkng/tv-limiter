#!/bin/zsh
# Check and build a release: clean tree, version matches, tests and lint for both
# versions, signed APK and Play bundle. Publishes nothing.
#
#   build-release.sh 1.2.0
set -eu

ROOT=${0:A:h:h:h:h:h}
cd $ROOT
version=${1:?usage: build-release.sh <version>}
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

[[ -z $(git status --porcelain --untracked-files=no) ]] || { echo "Commit or stash changes first." >&2; exit 1; }
grep -q "versionName = \"$version\"" app/build.gradle.kts || { echo "versionName in app/build.gradle.kts is not $version." >&2; exit 1; }
git rev-parse -q --verify "refs/tags/v$version" >/dev/null && { echo "Tag v$version already exists." >&2; exit 1; }
[[ -f keystore.properties ]] || { echo "keystore.properties is missing, so release builds would be unsigned." >&2; exit 1; }

./gradlew -q testGithubDebugUnitTest testPlayDebugUnitTest lintGithubDebug lintPlayDebug assembleGithubRelease bundlePlayRelease

apk=app/build/outputs/apk/github/release/app-github-release.apk
aab=app/build/outputs/bundle/playRelease/app-play-release.aab
bt=$(ls -d ~/Library/Android/sdk/build-tools/* | tail -1)
$bt/apksigner verify $apk
$bt/aapt2 dump badging $apk | head -1

mkdir -p local/release
cp $apk local/release/tv-limiter-$version.apk
echo "APK:    $ROOT/local/release/tv-limiter-$version.apk"
echo "Bundle: $ROOT/$aab"
