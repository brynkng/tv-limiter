#!/bin/zsh
# PreToolUse: keep the release signing key and its passwords out of the conversation and out of git.
input=$(cat)
tool=$(jq -r '.tool_name' <<<"$input")
case $tool in
  Bash) target=$(jq -r '.tool_input.command // ""' <<<"$input") ;;
  *) target=$(jq -r '.tool_input.file_path // .tool_input.notebook_path // ""' <<<"$input") ;;
esac

secret='keystore\.properties|\.jks([^a-z]|$)|\.keystore([^a-z]|$)|\.android-keys|play-service-account[^ ]*\.json'
if grep -qiE "$secret" <<<"$target"; then
  echo "Blocked: this touches the release signing key, its passwords or the Play service account. Gradle reads them itself; never open, print, edit or commit them." >&2
  exit 2
fi
if [[ $tool == Bash ]] && grep -qE 'git +add +(.* )?(-f|--force)( |$)' <<<"$target"; then
  echo "Blocked: git add --force would commit files git ignores on purpose (keys, local/, CLAUDE.local.md)." >&2
  exit 2
fi
exit 0
