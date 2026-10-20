#!/usr/bin/env bash
# desktop-app may reach Android APIs only through compat-shims or AndroidCompat (spec 2a, section 6).
# Allowed: Context and the androidx.preference models (AndroidCompat), Toast (AndroidCompat), widget-free androidx,
# the BackHandler/BackPressDispatcher shims (presentation-core) and Compose's multiplatform navigationevent.
set -euo pipefail
cd "$(dirname "$0")/.."
bad=$(grep -rhoE '^import android(x)?\.[A-Za-z0-9_.]+' desktop-app/src/main desktop-app/src/stubs \
    | sort -u \
    | grep -vE '^import android\.(content\.Context|widget\.Toast)$' \
    | grep -vE '^import androidx\.(compose|lifecycle|paging|preference|navigationevent|activity\.compose\.(BackHandler|BackPressDispatcher))' \
    || true)
if [ -n "$bad" ]; then
    echo "Android imports outside the allowed shims:"
    echo "$bad"
    exit 1
fi
echo "No direct Android imports in desktop-app"
