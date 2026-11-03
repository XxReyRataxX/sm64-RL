#!/usr/bin/env bash
# Usage: scan_feature.sh <keyword>
# Finds all C/H files that mention a feature keyword; shows surrounding context.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
KEYWORD="${1:?Usage: scan_feature.sh <keyword>}"

echo "=== Feature scan: '$KEYWORD' ==="
echo ""

echo "--- C/H files (src/) ---"
grep -rln --include="*.c" --include="*.h" -i "$KEYWORD" "$ROOT/src" 2>/dev/null \
  | sort | while read -r f; do
  echo "  $f"
  grep -n -i "$KEYWORD" "$f" | head -5 | sed 's/^/    /'
  echo ""
done

echo "--- Java files ---"
grep -rln -i "$KEYWORD" "$ROOT/java" 2>/dev/null | sort | while read -r f; do
  echo "  $f"
  grep -n -i "$KEYWORD" "$f" | head -5 | sed 's/^/    /'
  echo ""
done

echo "--- Done ---"
