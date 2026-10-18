#!/bin/bash
# Downloads what the build needs and the repo does not carry, each checked against a pinned SHA-256:
#   libs/  Gson (Maven Central) and the Spotify App Remote SDK (Spotify's GitHub release), Apache-2.0
#   keys/  the AOSP *test* platform key and certificate (public, from AOSP's build/ project)
# The test key is not a secret: it ships in AOSP's source. It only matters because head units whose
# firmware is signed with test-keys (`adb shell getprop ro.build.tags` says test-keys) grant
# platform signature permissions to any APK signed with it. build.sh also checks the certificate.
# Files already present are verified, not downloaded again. build.sh runs this first.
set -euo pipefail
cd "$(dirname "$0")"

AOSP=https://android.googlesource.com/platform/build/+/refs/heads/main/target/product/security

fetch() {   # <path> <sha256> <url> [base64]
    local path=$1 want=$2 url=$3 b64=${4:-}
    if [ ! -f "$path" ]; then
        mkdir -p "$(dirname "$path")"
        echo "fetching $path"
        if [ -n "$b64" ]; then curl -sSfL "$url" | base64 -d > "$path.part"; else curl -sSfL -o "$path.part" "$url"; fi
        mv "$path.part" "$path"
    fi
    local got
    got=$(shasum -a 256 "$path" | awk '{print $1}')
    if [ "$got" != "$want" ]; then
        echo "FAIL: $path has SHA-256 $got, expected $want" >&2
        exit 1
    fi
}

fetch libs/gson-2.10.1.jar \
    4241c14a7727c34feea6507ec801318a3d4a90f070e4525681079fb94ee4c593 \
    https://repo1.maven.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar
fetch libs/spotify-app-remote-release-0.8.0.aar \
    b5a6dd880eaf01f63a871cba9ef7af77c341f8a94ffc8fdf2e9021f9a9d4c198 \
    https://github.com/spotify/android-sdk/releases/download/v0.8.0-appremote_v2.1.0-auth/spotify-app-remote-release-0.8.0.aar
fetch keys/platform.pk8 \
    1ad8ef556870edb70f69a9d3c112544c07de5162ba440d84d33f8bb0c5962875 \
    "$AOSP/platform.pk8?format=TEXT" base64
fetch keys/platform.x509.pem \
    9837de028f460c35cc8d3fa45f14eecce30f6fbfe4b93d399aef1acb80c20d14 \
    "$AOSP/platform.x509.pem?format=TEXT" base64
echo "deps OK"
