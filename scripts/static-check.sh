#!/usr/bin/env bash
# Static gate — android-detour. detekt runs WITHOUT type resolution (no Android SDK locally);
# only bug-class rules block. CI runs type-aware detekt + the full Gradle build.
set -euo pipefail
echo "▶ correctness: detekt bug-class rules (BLOCKING; non-type-aware)"
detekt --input posted-sdk/src/main --config detekt-correctness.yml --build-upon-default-config --jvm-target 17
echo "▶ style: ktlint (BLOCKING)"
./gradlew ktlintCheck
# Guard: reject stale references to the old GitHub org (postedapp); correct org is thepostedapp.
# The search pattern does not appear in thepostedapp URLs so there are no false positives.
echo "▶ org-url guard: no stale old-org references (BLOCKING)"
STALE_PATTERN='github\.com/postedapp'
if git ls-files | grep -v 'scripts/static-check.sh' | xargs grep -rl "$STALE_PATTERN" 2>/dev/null | grep -q .; then
  echo "✗ stale old-org URL found — update to github.com/thepostedapp:"
  git ls-files | grep -v 'scripts/static-check.sh' | xargs grep -rn "$STALE_PATTERN" 2>/dev/null || true
  exit 1
fi
echo "✓ static gate passed (full + type-aware checks run in CI)"
