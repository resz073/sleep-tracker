#!/usr/bin/env bash
# Runs one build command on GitHub Actions. If it fails, the useful part of its output is shown
# as an error on the run's summary page, so the problem is easy to find without opening the logs.
# Usage: bash scripts/ci-run.sh "Title" command args...
set -uo pipefail
title="$1"; shift
log="$(mktemp)"
"$@" 2>&1 | tee "$log"
code=${PIPESTATUS[0]}
if [ "$code" -ne 0 ] && ! grep -q '^::error' "$log"; then
  first="$(grep -m 15 -E '(^e: |error:|ERROR:|npm error|Error:|FAILED)' "$log" || true)"
  if grep -q "What went wrong" "$log"; then
    last="$(grep -A 25 "What went wrong" "$log" | head -n 40)"
  else
    last="$(tail -n 40 "$log")"
  fi
  msg="$first"$'\n'"----"$'\n'"$last"
  msg="${msg//'%'/'%25'}"; msg="${msg//$'\r'/}"; msg="${msg//$'\n'/'%0A'}"
  echo "::error title=$title failed::$msg"
fi
rm -f "$log"
exit "$code"
