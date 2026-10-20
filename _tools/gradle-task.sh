#!/usr/bin/env bash
# 以离线工具链执行 Gradle 任务，参数为要执行的任务列表。
set -u
cd /d/DSHStation/OctoberLauncher || exit 1
TC=$(tr -d '\r\n' < toolchain.path)
JDK=$(ls -d "$TC/jdk"/*/ 2>/dev/null | while read -r d; do [ -f "${d}bin/javac.exe" ] && echo "${d%/}"; done | sort -r | head -1)
export JAVA_HOME="$JDK"
export ANDROID_HOME="$TC/android-sdk"
export GRADLE_USER_HOME="$TC/gradle-home"
exec "$TC/gradle/gradle-8.11.1/bin/gradle.bat" --offline "$@"
