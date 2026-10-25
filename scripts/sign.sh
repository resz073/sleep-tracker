#!/usr/bin/env bash
# Signs the release APK and AAB that Gradle built, into dist/.
# Uses your upload key when the KEYSTORE_* secrets are set (needed for Google Play).
# Otherwise uses the public test key in ci/, which is fine for trying the app on a phone.
# Common paste mistakes in the secrets are fixed here, and anything else is explained on the build's summary page.
set -euo pipefail
mkdir -p dist
TMP="${RUNNER_TEMP:-$(mktemp -d)}"
trap 'rm -f "$TMP/upload-key.jks" "$TMP/aligned.apk"' EXIT
fail() { echo "::error title=$1::$2"; exit 1; }
NOTES="sleep-tracker-signing.txt"

# The key file as text. Copes with line breaks, or with the label or the whole notes file pasted in.
keytext() {
  local all
  all="$(printf '%s' "$1" | tr -d ' \t\r\n')"
  if printf '%s' "$all" | grep -Eq '^[A-Za-z0-9+/]+=*$'; then printf '%s' "$all"; return 0; fi
  printf '%s\n' "$1" | tr -s ' \t\r' '\n' | grep -E '^[A-Za-z0-9+/]+=*$' | awk '{ print length($0) " " $0 }' | sort -rn | head -1 | cut -d' ' -f2 || true
}

if [ -n "${KEYSTORE_BASE64:-}" ]; then
  KT="$KEYSTORE_BASE64"; PW="${KEYSTORE_PASSWORD:-}"
  if [ "${#KT}" -lt 200 ] && [ "${#PW}" -gt 1000 ]; then
    echo "::warning title=Signing key::KEYSTORE_BASE64 and KEYSTORE_PASSWORD look swapped, so this build swapped them back. Swap them in the secrets when you can."
    t="$KT"; KT="$PW"; PW="$t"
  fi
  [ -n "$PW" ] || fail "KEYSTORE_PASSWORD" "This secret is missing. Add it: it's the password in $NOTES."

  B64="$(keytext "$KT")"
  [ "${#B64}" -ge 100 ] || fail "KEYSTORE_BASE64" "This secret doesn't contain the key text. Paste only the long line under 'KEYSTORE_BASE64:' in $NOTES."
  if ! printf '%s' "$B64" | base64 -d > "$TMP/upload-key.jks" 2>/dev/null; then
    fail "KEYSTORE_BASE64" "The key text looks cut off or changed (${#B64} characters). Copy the whole line under 'KEYSTORE_BASE64:' in $NOTES, about 3,500 characters, and paste it again."
  fi
  [ "${#B64}" -eq "$(printf '%s' "$KT" | tr -d ' \t\r\n' | wc -c)" ] || echo "::notice title=KEYSTORE_BASE64::The secret had extra text around the key. The build used just the key part."

  # Find the password that opens the key, allowing for stray spaces or line breaks
  P1="$(printf '%s' "$PW" | tr -d '\r' | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' || true)"
  P2="$(printf '%s' "$PW" | tr -d ' \t\r\n' || true)"
  P3="$(printf '%s\n' "$PW" | tr -s ' \t\r' '\n' | grep -v '^$' | tail -1 || true)"
  SIGN_PASS=""; OUT=""
  for CAND in "$PW" "$P1" "$P2" "$P3"; do
    [ -n "$CAND" ] || continue
    export CAND
    if OUT="$(keytool -list -keystore "$TMP/upload-key.jks" -storepass:env CAND 2>&1)"; then SIGN_PASS="$CAND"; break; fi
    printf '%s' "$OUT" | grep -qi "password was incorrect" || fail "KEYSTORE_BASE64" "This isn't a complete key file (${#B64} characters of key text). Copy the whole line under 'KEYSTORE_BASE64:' in $NOTES and paste it again."
  done
  [ -n "$SIGN_PASS" ] || fail "KEYSTORE_PASSWORD" "The password doesn't open the key. Paste only the password from $NOTES (the line under 'KEYSTORE_PASSWORD', 24 letters and numbers)."
  [ "$SIGN_PASS" = "$PW" ] || echo "::notice title=KEYSTORE_PASSWORD::The secret had extra spaces or text around the password. The build used just the password."
  export SIGN_PASS

  ALIASES="$(printf '%s\n' "$OUT" | grep -E ', PrivateKeyEntry' | cut -d, -f1 || true)"
  ALIAS="$(printf '%s' "${KEY_ALIAS:-upload}" | tr -d ' \t\r\n')"
  if ! printf '%s\n' "$ALIASES" | grep -qx "$ALIAS"; then
    if [ "$(printf '%s\n' "$ALIASES" | grep -c .)" -eq 1 ]; then ALIAS="$ALIASES"
    else fail "KEY_ALIAS" "The key file has no key called '$ALIAS'. Delete the KEY_ALIAS secret, or set it to one of: $(printf '%s' "$ALIASES" | tr '\n' ' ')"; fi
  fi
  KS="$TMP/upload-key.jks"; NAME=sleep-tracker; KIND="your upload key"
else
  KS="ci/test-key.p12"; export SIGN_PASS=android; ALIAS=test
  NAME=sleep-tracker-test; KIND="the public test key"
  echo "::warning title=Test build::No KEYSTORE_BASE64 secret, so this build is signed with the public test key. Fine for your phone, not for Google Play."
fi

BT="$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)"
IN=android/app/build/outputs/apk/release/app-release-unsigned.apk
"$BT/zipalign" -P 16 -f 4 "$IN" "$TMP/aligned.apk" 2>/dev/null || "$BT/zipalign" -p -f 4 "$IN" "$TMP/aligned.apk"
"$BT/apksigner" sign --v4-signing-enabled false --ks "$KS" --ks-pass env:SIGN_PASS --key-pass env:SIGN_PASS --ks-key-alias "$ALIAS" --out "dist/$NAME.apk" "$TMP/aligned.apk"
CERT="$("$BT/apksigner" verify --print-certs "dist/$NAME.apk" | grep -m1 "SHA-1 digest" | sed 's/.*: //' | tr 'A-F' 'a-f')"
CERTC="$(printf '%s' "$CERT" | tr 'a-f' 'A-F' | sed 's/../&:/g; s/:$//')"

cp android/app/build/outputs/bundle/release/app-release.aab "dist/$NAME.aab"
jarsigner -keystore "$KS" -storepass:env SIGN_PASS -keypass:env SIGN_PASS -sigalg SHA256withRSA -digestalg SHA-256 "dist/$NAME.aab" "$ALIAS" >/dev/null
jarsigner -verify "dist/$NAME.aab" | grep -q "jar verified" || fail "Signing" "The AAB signature didn't verify."

echo "::notice title=Signed::$NAME.apk ($(du -h "dist/$NAME.apk" | cut -f1)) and $NAME.aab, signed with $KIND (SHA-1 $CERTC), build tools $(basename "$BT")."

# Google sign-in only works when this key's fingerprint is registered in Firebase
if [ -f google-services.json ] && ! grep -q "sleep-tracker-placeholder" google-services.json; then
  if grep -qi "$CERT" google-services.json; then
    echo "::notice title=Google sign-in::This key's SHA-1 is registered in your Firebase project."
  else
    echo "::warning title=Google sign-in::This key's SHA-1 ($CERTC) isn't in google-services.json, so Google sign-in may not work. In Firebase: Project settings, your Android app, Add fingerprint. Then download google-services.json again and update the GOOGLE_SERVICES_JSON secret."
  fi
fi
ls -l dist
