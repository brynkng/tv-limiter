#!/bin/zsh
# Drive the emulator or the TV over adb.
#
#   device.sh <emu|tv> connect             connect (tv) and list devices
#   device.sh <emu|tv> install [apk]       install over the existing app, keeping its data
#   device.sh <emu|tv> launch              open TV Limiter's home screen
#   device.sh <emu|tv> keys K1 K2 ...      UP DOWN LEFT RIGHT CENTER BACK HOME, or any KEYCODE_*
#   device.sh <emu|tv> shot NAME           screenshot to local/shots/<emu|tv>/NAME.png, prints the path
#   device.sh <emu|tv> focus               the activity in front
#   device.sh <emu|tv> grant               grant usage access and display over other apps
#   device.sh <emu|tv> shell ...           any adb shell command
#   device.sh <emu|tv> detect-grant        let the debug build's YouTube detection recorder listen
#   device.sh <emu|tv> detect-clear        empty the detection log
#   device.sh <emu|tv> detect-pull NAME    save the detection log to local/detection/<emu|tv>/NAME.jsonl, thumbnails to thumbs/
#
# The TV's address comes from TV_SERIAL, or from local/device.env (git ignores local/).
set -eu

ROOT=${0:A:h:h:h:h:h}
[[ -f $ROOT/local/device.env ]] && source $ROOT/local/device.env
# A running Unity Editor starts its own older adb on 5037 and drops connections.
export ANDROID_ADB_SERVER_PORT=${ANDROID_ADB_SERVER_PORT:-5038}
ADB=${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}
PKG=io.github.brynkng.tvlimiter

target=${1:?usage: device.sh <emu|tv> <command> ...}
cmd=${2:?usage: device.sh <emu|tv> <command> ...}
shift 2

case $target in
  emu) SERIAL=${EMU_SERIAL:-emulator-5554} ;;
  tv) SERIAL=${TV_SERIAL:?set TV_SERIAL or put TV_SERIAL=host:port in local/device.env} ;;
  *) echo "target must be emu or tv" >&2; exit 2 ;;
esac
A=($ADB -s $SERIAL)

case $cmd in
  connect)
    [[ $target == tv ]] && $ADB connect $SERIAL
    $ADB devices -l ;;
  install)
    $A install -r ${1:-$ROOT/app/build/outputs/apk/github/debug/app-github-debug.apk} ;;
  launch)
    $A shell am start -n $PKG/dev.tvlimiter.ui.SettingsActivity ;;
  keys)
    for k in "$@"; do
      case $k in
        UP|DOWN|LEFT|RIGHT|CENTER) k=KEYCODE_DPAD_$k ;;
        BACK|HOME|ENTER) k=KEYCODE_$k ;;
      esac
      $A shell input keyevent $k
      sleep 0.35
    done ;;
  shot)
    out=$ROOT/local/shots/$target/${1:?shot needs a name}.png
    mkdir -p ${out:h}
    $A exec-out screencap -p > $out
    sips -Z 1280 $out >/dev/null
    echo $out ;;
  focus)
    $A shell dumpsys window | grep -m1 mCurrentFocus ;;
  grant)
    $A shell appops set $PKG GET_USAGE_STATS allow
    $A shell appops set $PKG SYSTEM_ALERT_WINDOW allow ;;
  shell)
    $A shell "$@" ;;
  detect-grant)
    # The user id at the end is needed; without it, or with `settings put`, the grant may not take.
    $A shell cmd notification allow_listener $PKG/dev.tvlimiter.detection.DetectionListener 0 ;;
  detect-clear)
    $A shell rm -f /sdcard/Android/data/$PKG/files/detection.jsonl ;;
  detect-pull)
    out=$ROOT/local/detection/$target/${1:?detect-pull needs a name}.jsonl
    mkdir -p ${out:h}
    $A shell cat /sdcard/Android/data/$PKG/files/detection.jsonl > $out
    # Thumbnails are kept across pulls; this copies any new ones.
    mkdir -p ${out:h}/thumbs
    $A pull /sdcard/Android/data/$PKG/files/thumbs/. ${out:h}/thumbs >/dev/null 2>&1 || true
    wc -l < $out
    echo $out ;;
  *) echo "unknown command: $cmd" >&2; exit 2 ;;
esac
