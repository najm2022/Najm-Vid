#!/usr/bin/env bash
set -u
adb shell wm size 1080x2340
adb shell wm density 420
adb shell settings put system font_scale 1.0
test_result=0
gradle :app:connectedDebugAndroidTest --no-daemon --stacktrace || test_result=$?
mkdir -p ui-screenshots
adb pull /sdcard/Pictures/NajmVidPreview/. ui-screenshots/ || true
adb logcat -d -s AndroidRuntime > ui-screenshots/runtime-log.txt
exit "$test_result"
