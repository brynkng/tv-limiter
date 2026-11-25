#!/bin/zsh
# Stop: if Kotlin or Gradle files changed, run the unit tests before Claude finishes.
# A failure sends the output back to Claude to fix. A green run is remembered, so an
# unchanged tree isn't tested twice.
input=$(cat)
[[ $(jq -r '.stop_hook_active' <<<"$input") == true ]] && exit 0
cd ${CLAUDE_PROJECT_DIR:-.}

changed=$( { git diff --name-only HEAD; git ls-files --others --exclude-standard; } | grep -E '\.(kt|kts)$' )
[[ -z $changed ]] && exit 0

stamp=local/.last-green-tests
fingerprint=$( { git diff HEAD; for f in ${(f)changed}; do [[ -f $f ]] && cat $f; done; } | shasum | cut -d' ' -f1 )
[[ -f $stamp && $(<$stamp) == $fingerprint ]] && exit 0

export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
if out=$(./gradlew -q testGithubDebugUnitTest testPlayDebugUnitTest 2>&1); then
  mkdir -p local && print -r -- $fingerprint > $stamp
  exit 0
fi
echo "Unit tests fail with the current changes. Fix them before finishing:" >&2
print -r -- "$out" | tail -40 >&2
exit 2
