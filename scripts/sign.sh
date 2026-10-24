#!/usr/bin/env bash
# Signs the release APK and AAB that Gradle built, into dist/.
# Uses your upload key when the KEYSTORE_* secrets are set (needed for Google Play).
# Otherwise uses the public test key in ci/, which is fine for trying the app on a phone.
set -euo pipefail
mkdir -p dist
TMP="${RUNNER_TEMP:-$(mktemp -d)}"
trap 'rm -f "$TMP/upload-key.jks" "$TMP/aligned.apk"' EXIT

if [ -n "${KEYSTORE_BASE64:-}" ]; then
  if [ -z "${KEYSTORE_PASSWORD:-}" ]; then echo "Error: KEYSTORE_BASE64 is set but KEYSTORE_PASSWORD is missing." >&2; exit 1; fi
  printf '%s' "$KEYSTORE_BASE64" | tr -d ' \r\n' | base64 -d > "$TMP/upload-key.jks"
  KS="$TMP/upload-key.jks"; export SIGN_PASS="$KEYSTORE_PASSWORD"; ALIAS="${KEY_ALIAS:-upload}"
  NAME=sleep-tracker; KIND="your upload key"
else
  KS="ci/test-key.p12"; export SIGN_PASS=android; ALIAS=test
  NAME=sleep-tracker-test; KIND="the public test key"
  echo "::warning title=Test build::No KEYSTORE_BASE64 secret, so this build is signed with the public test key. Fine for your phone, not for Google Play."
fi

BT="$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)"
IN=android/app/build/outputs/apk/release/app-release-unsigned.apk
"$BT/zipalign" -P 16 -f 4 "$IN" "$TMP/aligned.apk" 2>/dev/null || "$BT/zipalign" -p -f 4 "$IN" "$TMP/aligned.apk"
"$BT/apksigner" sign --ks "$KS" --ks-pass env:SIGN_PASS --key-pass env:SIGN_PASS --ks-key-alias "$ALIAS" --out "dist/$NAME.apk" "$TMP/aligned.apk"
CERT="$("$BT/apksigner" verify --print-certs "dist/$NAME.apk" | grep -m1 "SHA-1 digest" | sed 's/.*: //')"

cp android/app/build/outputs/bundle/release/app-release.aab "dist/$NAME.aab"
jarsigner -keystore "$KS" -storepass:env SIGN_PASS -keypass:env SIGN_PASS -sigalg SHA256withRSA -digestalg SHA-256 "dist/$NAME.aab" "$ALIAS" >/dev/null
jarsigner -verify "dist/$NAME.aab" | grep -q "jar verified" || { echo "Error: the AAB signature didn't verify." >&2; exit 1; }

echo "::notice title=Signed::$NAME.apk ($(du -h "dist/$NAME.apk" | cut -f1)) and $NAME.aab, signed with $KIND (SHA-1 $CERT), build tools $(basename "$BT")."
ls -l dist
