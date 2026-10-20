#!/usr/bin/env bash
# P4 本记导出六变体：改 kernel_prefs 偏好后点导出，拉取新文件到 _analysis/verify/20261020-parity。
set -u
cd /d/DSHStation/OctoberLauncher || exit 1
export MSYS_NO_PATHCONV=1
ADB=/d/DSHStation/.toolchain/android-sdk/platform-tools/adb.exe
P4=P4L202610130006
OUT=_analysis/verify/20261020-parity
PKG=com.shuiyu.octoberrain

pull_prefs() {
  $ADB -s "$P4" exec-out "run-as $PKG cat files/weread/kernel_prefs.json" > "/tmp/p4-kp.json"
}

push_prefs() {
  local local_file="$1"
  $ADB -s "$P4" push "$(cygpath -w "$local_file")" /data/local/tmp/kp.json >/dev/null 2>&1
  $ADB -s "$P4" shell "run-as $PKG cp /data/local/tmp/kp.json files/weread/kernel_prefs.json"
}

export_variant() {
  local name="$1" tpl="$2" paper="$3" size="$4" sign="$5" date="$6"
  pull_prefs
  node _tools/kp-set.mjs "$(cygpath -w /tmp/p4-kp.json)" "$(cygpath -w /tmp/p4-kp-out.json)" \
    "export_tpl=$tpl" "paper=$paper" "nsize=$size" "note_sign=$sign" "note_date=$date" >/dev/null
  push_prefs /tmp/p4-kp-out.json
  $ADB -s "$P4" shell input tap 460 658
  sleep 3
  $ADB -s "$P4" shell input keyevent KEYCODE_BACK
  sleep 1
  local newest
  newest=$($ADB -s "$P4" shell "ls -t /sdcard/Pictures/微读墨记/ | head -1" | tr -d '\r')
  $ADB -s "$P4" pull "/sdcard/Pictures/微读墨记/$newest" "$OUT/$name" >/dev/null 2>&1
  local dims
  dims=$(ffprobe -v error -select_streams v:0 -show_entries stream=width,height -of csv=p=0 "$OUT/$name")
  echo "$name $dims"
}

export_variant p4-export-1-hand-zhong.png 1 1 1 true true
export_variant p4-export-2-su-bai.png 0 0 1 true true
export_variant p4-export-3-ka-zhi.png 2 2 1 true true
export_variant p4-export-4-size-small.png 1 1 0 true true
export_variant p4-export-5-size-large.png 1 1 2 true true
export_variant p4-export-6-no-sign-date.png 1 1 1 false false
