#!/usr/bin/env bash
# Fails when vendored MPL-2.0 code loses its license notice.
set -euo pipefail

status=0
for required in android-compat/LICENSE android-compat/README.md NOTICE; do
  [[ -f "$required" ]] || { echo "missing $required"; status=1; }
done

for file in extension-loader/src/main/kotlin/mihon/desktop/extension/vendor/*.kt; do
  grep -q "Mozilla Public" "$file" || { echo "missing MPL header: $file"; status=1; }
done

exit $status
