#!/usr/bin/env bash
# Runs the native app's unit tests (the sleep engine against the current app's own results) and shows the
# outcome on the GitHub Actions run's summary page. Run from anywhere; it works in native/.
set -uo pipefail
cd "$(dirname "$0")/.."
./gradlew testDebugUnitTest --warning-mode=summary
code=$?
node scripts/test-report.mjs app/build/test-results/testDebugUnitTest
exit "$code"
