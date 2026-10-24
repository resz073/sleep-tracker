#!/usr/bin/env bash
# Lists the Android permissions in the finished app and which library asked for each one,
# from Gradle's manifest merger report. Shown on the build's summary page.
# Run from the android/ folder after the Gradle build.
R="${1:-app/build/outputs/logs/manifest-merger-release-report.txt}"
[ -f "$R" ] || exit 0

# Permissions that made it into the final manifest (ones removed on purpose are left out)
M="$(find app/build/intermediates -path '*release*' -name AndroidManifest.xml 2>/dev/null | xargs -r grep -l '<uses-permission' 2>/dev/null | head -1)"
keep=""
[ -n "$M" ] && keep="$(grep -o 'uses-permission[^>]*android:name="[^"]*"' "$M" | sed 's/.*android:name="//; s/"$//' | sort -u | tr '\n' ' ')"

list="$(KEEP="$keep" awk '
  function flush() {
    if (p != "" && (ENVIRON["KEEP"] == "" || index(" " ENVIRON["KEEP"], " " full " ") > 0)) {
      short = full; sub(/^android\.permission\./, "", short)
      out = out sep short " (" (src == "" ? "?" : src) ")"; sep = "; "
    }
    p = ""; src = ""
  }
  /^uses-permission#/ { flush(); p = 1; full = $0; sub(/^uses-permission#/, "", full); next }
  p != "" && /^(ADDED|MERGED) from/ {
    s = "app"; if (match($0, /\[[^]]+\]/)) { s = substr($0, RSTART + 1, RLENGTH - 2); if (s ~ /^:/) sub(/^:/, "", s); else sub(/:[^:]*$/, "", s) }
    if (index(src, s) == 0) src = src (src == "" ? "" : ", ") s
    next
  }
  /^[^ \t]/ && !/^(ADDED|MERGED|INJECTED|IMPLIED|REJECTED|REMOVED)/ { flush() }
  END { flush(); print out }
' "$R" || true)"
echo "::notice title=Permissions::$list"
