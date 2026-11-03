#!/usr/bin/env bash
# Usage: scan_symbol.sh <symbol>
# Finds declarations, definitions, and usages of a C symbol across the codebase.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SYM="${1:?Usage: scan_symbol.sh <symbol>}"

echo "=== Symbol: $SYM ==="
echo ""

echo "--- Declarations / definitions (grep -rn) ---"
grep -rn --include="*.c" --include="*.h" "$SYM" "$ROOT/src" "$ROOT/include" 2>/dev/null \
  | grep -v "^Binary" | head -60

echo ""
echo "--- Java references ---"
grep -rn "$SYM" "$ROOT/java" 2>/dev/null | head -20

echo ""
echo "--- Done ---"
