#!/bin/bash
# Builds and platform-signs DriveDeck. No Gradle: aapt2 + javac + d8 + apksigner.
set -euo pipefail
cd "$(dirname "$0")"

SDK=/opt/homebrew/share/android-commandlinetools
BT=$SDK/build-tools/35.0.0
AJ=$SDK/platforms/android-34/android.jar
EXPECTED_SHA1="27196e386b875e76adf700e7ea84e4c6eee33dfa"   # this head unit's platform cert

rm -rf build && mkdir -p build/stubs build/classes build/dex

"$BT/aapt2" link --manifest AndroidManifest.xml -I "$AJ" \
    --min-sdk-version 27 --target-sdk-version 27 -o build/base.apk

# Hidden-API stubs: compiled for linking only, never packaged.
javac -source 1.8 -target 1.8 -nowarn -classpath "$AJ" -d build/stubs \
    $(find stubs -name '*.java')
javac -source 1.8 -target 1.8 -nowarn -classpath "$AJ:build/stubs" -d build/classes \
    $(find src -name '*.java')

"$BT/d8" --min-api 27 --lib "$AJ" --classpath build/stubs --output build/dex \
    $(find build/classes -name '*.class')

cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q -j ../unsigned.apk classes.dex)
"$BT/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
"$BT/apksigner" sign --key keys/platform.pk8 --cert keys/platform.x509.pem \
    --out build/drivedeck.apk build/aligned.apk

got=$("$BT/apksigner" verify --print-certs build/drivedeck.apk \
      | awk -F': ' '/SHA-1 digest/{print $2; exit}')
if [ "$got" != "$EXPECTED_SHA1" ]; then
    echo "FAIL: signed with $got, expected $EXPECTED_SHA1" >&2
    exit 1
fi
echo "OK: build/drivedeck.apk signed with the head unit's platform cert ($got)"
