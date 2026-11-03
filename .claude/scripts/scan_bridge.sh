#!/usr/bin/env bash
# Maps the full C<->Java bridge: globals set from argv, their types, and Java usage.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PC_MAIN="$ROOT/src/pc/pc_main.c"
LAUNCHER="$ROOT/java/GameLauncher.java"

echo "=== C<->Java Bridge Audit ==="
echo ""

echo "--- CLI args parsed in pc_main.c ---"
grep -n "\-\-[a-z]" "$PC_MAIN" | sed 's/^/  /'

echo ""
echo "--- Globals written from argv (pc_main.c, ~lines 40-55) ---"
sed -n '35,60p' "$PC_MAIN" | grep -E "^[a-z]|g[A-Z]" | sed 's/^/  /'

echo ""
echo "--- ProcessBuilder args in GameLauncher.java ---"
grep -n "\"--" "$LAUNCHER" | sed 's/^/  /'

echo ""
echo "--- Java UI controls (LevelSelector.java) ---"
grep -n "speed\|level\|course\|arg\|param" "$ROOT/java/LevelSelector.java" \
  --include="*.java" -i 2>/dev/null | head -30 | sed 's/^/  /'

echo ""
echo "--- Done ---"
