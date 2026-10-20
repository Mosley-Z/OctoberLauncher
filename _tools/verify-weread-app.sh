#!/usr/bin/env bash
# 微读墨记 App 页面真机验证辅助脚本。
# 功能：封装 adb 安装、截图、按文字点击、按文字长按、滑动、UI 树导出，
# 供操作者按步骤逐步调用；截图统一写入 _analysis/verify/20261020-app/ 并按序号递增。
# 参数：$1 子命令（install/shot/tap/longpress/swipe/dump/texts/screen/back/home），其余为子命令参数。
# 行为：每条命令执行后短暂等待 UI 稳定；不清理 files/weread 与 files/weread_covers 数据。

set -u
export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL="*"

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ADB="/d/DSHStation/.toolchain/android-sdk/platform-tools/adb.exe"
SERIAL="${VERIFY_SERIAL:-SA4B03CN2608081096X}"
PKG="com.shuiyu.octoberrain"
OUT_DIR="$REPO_ROOT/_analysis/verify/20261020-app"
UI_DUMP="/tmp/verify-weread-ui.xml"
COUNTER_FILE="$OUT_DIR/.counter"

mkdir -p "$OUT_DIR"

# 读取并自增截图序号，保证 step-NN 命名连续。
next_index() {
    local n=0
    if [ -f "$COUNTER_FILE" ]; then
        n="$(cat "$COUNTER_FILE")"
    fi
    n=$((n + 1))
    echo "$n" > "$COUNTER_FILE"
    echo "$n"
}

# 截取当前画面，参数 $1 为步骤描述，结果为 step-NN-描述.png。
shot() {
    local label="$1"
    local idx
    idx="$(next_index)"
    local name
    name="$(printf "%s/step-%02d-%s.png" "$OUT_DIR" "$idx" "$label")"
    "$ADB" -s "$SERIAL" exec-out screencap -p > "$name" 2>/dev/null
    echo "$name"
}

# 导出当前 UI 树到本地临时文件，供按文字定位使用。
dump_ui() {
    "$ADB" -s "$SERIAL" shell uiautomator dump /sdcard/verify-ui.xml >/dev/null 2>&1
    "$ADB" -s "$SERIAL" shell cat /sdcard/verify-ui.xml > "$UI_DUMP" 2>/dev/null
}

# 打印当前画面所有可见文字，参数无；便于人工核对页面结构。
texts() {
    dump_ui
    grep -o 'text="[^"]*"' "$UI_DUMP" | sed 's/^text="//; s/"$//' | grep -v '^$'
}

# 按文字查找节点中心坐标，参数 $1 为文字；$2 为可选序号（同一文字多处时取第几个，从 1 起）。
# 输出 "x y"；找不到时输出空并返回 1。
center_of_text() {
    local target="$1"
    local nth="${2:-1}"
    local bounds
    bounds="$(grep -o "text=\"$target\"[^>]*bounds=\"\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]\"" "$UI_DUMP" |
        sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]"/\1 \2 \3 \4/' |
        sed -n "${nth}p")"
    if [ -z "$bounds" ]; then
        return 1
    fi
    echo "$bounds" | awk '{ printf "%d %d\n", ($1 + $3) / 2, ($2 + $4) / 2 }'
}

# 按文字点击，参数 $1 为文字，$2 为可选序号。
tap_text() {
    dump_ui
    local pos
    pos="$(center_of_text "$1" "${2:-1}")" || {
        echo "未找到文字：$1" >&2
        return 1
    }
    "$ADB" -s "$SERIAL" shell input tap $pos
    sleep 0.8
}

# 按文字长按，参数 $1 为文字，$2 为可选时长毫秒（默认 1000）。
longpress_text() {
    local target="$1"
    local duration="${2:-1000}"
    dump_ui
    local pos
    pos="$(center_of_text "$target")" || {
        echo "未找到文字：$target" >&2
        return 1
    }
    local x y
    x="$(echo "$pos" | awk '{print $1}')"
    y="$(echo "$pos" | awk '{print $2}')"
    "$ADB" -s "$SERIAL" shell input swipe "$x" "$y" "$x" "$y" "$duration"
    sleep 0.8
}

# 按坐标点击，参数 $1 $2 为 x y。
tap_xy() {
    "$ADB" -s "$SERIAL" shell input tap "$1" "$2"
    sleep 0.8
}

# 滑动，参数 x1 y1 x2 y2 [时长毫秒]。
swipe() {
    "$ADB" -s "$SERIAL" shell input swipe "$1" "$2" "$3" "$4" "${5:-400}"
    sleep 0.8
}

# 安装最新 debug 包并保留数据，参数无。
install_apk() {
    "$ADB" -s "$SERIAL" install -r "$REPO_ROOT/app/build/outputs/apk/debug/app-debug.apk"
}

# 打印当前前台窗口，参数无。
screen() {
    "$ADB" -s "$SERIAL" shell dumpsys window | grep -E "mCurrentFocus|mFocusedApp" | head -2
}

case "${1:-}" in
    install) install_apk ;;
    shot) shot "${2:-state}" ;;
    tap) tap_text "$2" "${3:-1}" ;;
    longpress) longpress_text "$2" "${3:-1000}" ;;
    txy) tap_xy "$2" "$3" ;;
    swipe) swipe "$2" "$3" "$4" "$5" "${6:-400}" ;;
    dump) dump_ui && echo "$UI_DUMP" ;;
    texts) texts ;;
    screen) screen ;;
    back) "$ADB" -s "$SERIAL" shell input keyevent KEYCODE_BACK; sleep 0.8 ;;
    home) "$ADB" -s "$SERIAL" shell input keyevent KEYCODE_HOME; sleep 0.8 ;;
    start) "$ADB" -s "$SERIAL" shell am start -n "$PKG/.MainActivity" >/dev/null; sleep 1.5 ;;
    *)
        echo "用法：$0 {install|shot <描述>|tap <文字> [序号]|longpress <文字> [毫秒]|txy <x> <y>|swipe <x1> <y1> <x2> <y2> [毫秒]|dump|texts|screen|back|home|start}"
        exit 1
        ;;
esac
