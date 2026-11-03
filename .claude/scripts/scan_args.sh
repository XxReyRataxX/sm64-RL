#!/usr/bin/env bash
# Lists all CLI arguments currently parsed in pc_main.c with their types and globals.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PC_MAIN="$ROOT/src/pc/pc_main.c"

echo "=== CLI Arguments in pc_main.c ==="
echo ""

echo "--- Argument parsing blocks (main()) ---"
# Show context around each --arg string
grep -n '"--' "$PC_MAIN" | while IFS= read -r line; do
  lineno=$(echo "$line" | cut -d: -f1)
  start=$((lineno - 1))
  end=$((lineno + 4))
  echo "  [line $lineno]"
  sed -n "${start},${end}p" "$PC_MAIN" | sed 's/^/    /'
  echo ""
done

echo ""
echo "--- Global variables (top of file) ---"
awk '/^OSMesg gMain/,/^static struct/' "$PC_MAIN" | grep -E "^s[0-9]|^f[0-9]|^u[0-9]|^int|^float|^char" | sed 's/^/  /'

echo ""
echo "--- Done ---"
