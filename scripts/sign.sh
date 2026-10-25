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

# Text helpers. Byte-wise, so invisible characters from a phone's text view can be found and dropped.
export LC_ALL=C
longest_token() { printf '%s\n' "$1" | tr -s ' \t\r' '\n' | grep -E '^[A-Za-z0-9+/]+=*$' | awk '{ print length($0) " " $0 }' | sort -rn | head -1 | cut -d' ' -f2 || true; }
pure() { printf '%s' "$1" | grep -Eq '^[A-Za-z0-9+/]+=*$' && printf '%s' "$1" || true; }

if [ -n "${KEYSTORE_BASE64:-}" ]; then
  KT="$KEYSTORE_BASE64"; PW="${KEYSTORE_PASSWORD:-}"
  if [ "${#KT}" -lt 200 ] && [ "${#PW}" -gt 1000 ]; then
    echo "::warning title=Signing key::KEYSTORE_BASE64 and KEYSTORE_PASSWORD look swapped, so this build swapped them back. Swap them in the secrets when you can."
    t="$KT"; KT="$PW"; PW="$t"
  fi
  [ -n "$PW" ] || fail "KEYSTORE_PASSWORD" "This secret is missing. Add it: it's the password in $NOTES."

  # Ways to read the key text: as pasted with line breaks removed, the longest run of key text (label or
  # whole notes file pasted in), the same with invisible characters dropped, and only the key characters.
  CLEAN="$(printf '%s' "$KT" | tr -cd '\11\12\15\40-\176')"
  KEYS=("$(pure "$(printf '%s' "$KT" | tr -d ' \t\r\n')")" "$(longest_token "$KT")" \
        "$(pure "$(printf '%s' "$CLEAN" | tr -d ' \t\r\n')")" "$(longest_token "$CLEAN")" \
        "$(printf '%s' "$KT" | tr -cd 'A-Za-z0-9+/=')")
  # Ways to read the password: as pasted, trimmed, without spaces or line breaks, the last word, without invisible characters
  PWS=("$PW" "$(printf '%s' "$PW" | tr -d '\r' | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')" \
       "$(printf '%s' "$PW" | tr -d ' \t\r\n')" "$(printf '%s\n' "$PW" | tr -s ' \t\r' '\n' | grep -v '^$' | tail -1 || true)" \
       "$(printf '%s' "$PW" | tr -cd 'A-Za-z0-9')")

  SIGN_PASS=""; B64=""; OUT=""; PW_WRONG=0; TRIED=" "
  for K in "${KEYS[@]}"; do
    [ "${#K}" -ge 100 ] || continue
    case "$TRIED" in *" ${#K}:${K:0:12}${K: -12} "*) continue;; esac; TRIED="$TRIED${#K}:${K:0:12}${K: -12} "
    printf '%s' "$K" | base64 -d > "$TMP/upload-key.jks" 2>/dev/null || continue
    for CAND in "${PWS[@]}"; do
      [ -n "$CAND" ] || continue
      export CAND
      if OUT="$(keytool -list -keystore "$TMP/upload-key.jks" -storepass:env CAND 2>&1)"; then SIGN_PASS="$CAND"; B64="$K"; break 2; fi
      printf '%s' "$OUT" | grep -qi "password was incorrect" && PW_WRONG=1
    done
  done

  if [ -z "$SIGN_PASS" ]; then
    [ "$PW_WRONG" -eq 0 ] || fail "KEYSTORE_PASSWORD" "The password doesn't open the key. Paste only the password from $NOTES (the line under 'KEYSTORE_PASSWORD', 24 letters and numbers)."
    N="$(printf '%s' "$KT" | wc -c | tr -d ' ')"; ODD="$(printf '%s' "$KT" | tr -d '\11\12\15\40-\176' | wc -c | tr -d ' ')"
    T1="$(printf '%s' "$KT" | tr -d ' \t\r\n')"; HINT=""
    if printf '%s' "$T1" | grep -Eq '^[A-Za-z0-9]{16,40}$'; then HINT=" It looks like the password, not the key text."
    elif printf '%s' "$T1" | grep -Eqi '^([0-9a-f]{2}:){19,31}[0-9a-f]{2}$'; then HINT=" It looks like a fingerprint, not the key text."
    elif [ "$ODD" -gt 0 ]; then HINT=" It also has unusual (non-keyboard) characters, which phone file previews sometimes add."
    fi
    fail "KEYSTORE_BASE64" "This secret ($N characters) isn't the complete key text.$HINT Replace it with everything in KEYSTORE_BASE64.txt, which is one line of about 3,550 characters."
  fi
  [ "$B64" = "$KT" ] || echo "::notice title=KEYSTORE_BASE64::The secret had extra text or characters around the key. The build used just the key."
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
