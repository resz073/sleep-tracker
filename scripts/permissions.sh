#!/usr/bin/env bash
# Lists the Android permissions in the finished app and which library asked for each one,
# from Gradle's manifest merger report. Shown on the build's summary page.
R="${1:-android/app/build/outputs/logs/manifest-merger-release-report.txt}"
[ -f "$R" ] || exit 0
list="$(awk '
  function flush() { if (p != "") { out = out sep p " (" (src == "" ? "?" : src) ")"; sep = "; " } p = ""; src = "" }
  /^uses-permission#/ { flush(); p = $0; sub(/^uses-permission#(android\.permission\.)?/, "", p); next }
  p != "" && /^(ADDED|MERGED) from/ {
    s = "app"; if (match($0, /\[[^]]+\]/)) { s = substr($0, RSTART + 1, RLENGTH - 2); if (s ~ /^:/) sub(/^:/, "", s); else sub(/:[^:]*$/, "", s) }
    if (index(src, s) == 0) src = src (src == "" ? "" : ", ") s
    next
  }
  /^[^ \t]/ && !/^(ADDED|MERGED|INJECTED|IMPLIED|REJECTED)/ { flush() }
  END { flush(); print out }
' "$R" || true)"
echo "::notice title=Permissions::$list"
