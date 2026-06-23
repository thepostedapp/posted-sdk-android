#!/usr/bin/env bash
# Static gate — android-detour. detekt runs WITHOUT type resolution (no Android SDK locally);
# only bug-class rules block. CI runs type-aware detekt + the full Gradle build.
set -euo pipefail
echo "▶ correctness: detekt bug-class rules (BLOCKING; non-type-aware)"
detekt --input detour-sdk/src/main --config detekt-correctness.yml --build-upon-default-config --jvm-target 17
echo "▶ style (non-blocking)"
ktlint "detour-sdk/src/**/*.kt" || echo "::warn:: ktlint findings (non-blocking)"
echo "✓ static gate passed (full + type-aware checks run in CI)"
