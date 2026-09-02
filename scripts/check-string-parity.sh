#!/usr/bin/env bash
# Checks that every module's values/strings.xml and values-it/strings.xml declare the same set of
# string names. Run this before every release; it exits non-zero and names the offending file if
# any module's English and Italian resources have drifted apart.
#
# Usage: scripts/check-string-parity.sh   (run from anywhere; paths are resolved from the repo root)

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

status=0

for en in $(find . -path "*/res/values/strings.xml" -not -path "*/build/*"); do
  it="${en/values\/strings.xml/values-it/strings.xml}"
  if [ ! -f "$it" ]; then
    echo "MISMATCH: $en (no matching $it)"
    status=1
    continue
  fi
  if ! diff <(grep -o 'name="[^"]*"' "$en" | sort -u) <(grep -o 'name="[^"]*"' "$it" | sort -u) >/dev/null; then
    echo "MISMATCH: $en"
    status=1
  fi
done

exit $status
