#!/usr/bin/env bash
# Shows key Makefile variables, targets, and version/platform defines.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
MK="$ROOT/Makefile"

echo "=== Makefile Overview ==="
echo ""

echo "--- VERSION / TARGET variables ---"
grep -n "^VERSION\|^TARGET\|^PLATFORM\|^GRUCODE\|version\b" "$MK" | head -20 | sed 's/^/  /'

echo ""
echo "--- Compiler / flags ---"
grep -n "^CC\b\|^CFLAGS\|^LDFLAGS\|^OPT_FLAGS" "$MK" | head -20 | sed 's/^/  /'

echo ""
echo "--- Output binary path ---"
grep -n "sm64\." "$MK" | grep -v "sha1\|^#" | head -10 | sed 's/^/  /'

echo ""
echo "--- Phony / top-level targets ---"
grep -n "^\.PHONY\|^all:\|^clean:\|^extract:" "$MK" | head -15 | sed 's/^/  /'

echo ""
echo "--- Extra Makefile includes ---"
grep -n "^include\|-include" "$MK" | head -10 | sed 's/^/  /'

echo ""
echo "--- Done ---"
