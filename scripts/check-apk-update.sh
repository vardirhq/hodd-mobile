#!/usr/bin/env bash
set -euo pipefail
# Runs only on the disposable CI emulator, after the catalog tests.
debug_apk=${1:?Debug APK required}
release_apk=${2:?Release APK required}
app=no.vardir.hodd
adb shell am force-stop "$app"
adb shell run-as "$app" sh -c "'mkdir -p files/photos; printf hodd-upgrade-photo > files/photos/upgrade-test.jpg'"
# Own the fixture instead of depending on another test's cleanup/order.
cat <<'JSON' | adb shell -T run-as "$app" sh -c "'cat > files/catalog.json'"
{"version":1,"collections":[{"id":"upgrade","name":"Upgrade check"}],"items":[{"id":"kept","collectionId":"upgrade","title":"Keep this item","photoName":"upgrade-test.jpg"}]}
JSON
before=$(adb shell run-as "$app" sh -c "'sha256sum files/catalog.json files/photos/upgrade-test.jpg'" | tr -d '\r')
adb install -r "$release_apk"
adb install -r "$debug_apk"
after=$(adb shell run-as "$app" sh -c "'sha256sum files/catalog.json files/photos/upgrade-test.jpg'" | tr -d '\r')
test "$before" = "$after"
echo 'APK replacement preserved the catalog and photo bytes.'
