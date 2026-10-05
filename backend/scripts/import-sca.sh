#!/usr/bin/env bash
# Import SCA leaderboard tables into a running backend with curl.
#
#   scripts/import-sca.sh <dir> [competition name] [season]
#
# <dir> holds sca-batting.csv, sca-bowling.csv and sca-fielding.csv: each one is the
# table copied from the SCA website and pasted into a file as is (tab-separated, one
# player over several lines). Each file's date is recorded as its capture date.
# Re-running with unchanged files changes nothing.
#
# Environment: HAWKS_API (default http://localhost:8080),
#              HAWKS_ADMIN (default dev-admin:dev-admin-password, the dev profile login).
set -euo pipefail

dir=${1:?usage: $0 <dir> [competition name] [season]}
name=${2:-SCA Club League 2025 - Division 3}
season=${3:-2025}
api=${HAWKS_API:-http://localhost:8080}
admin=${HAWKS_ADMIN:-dev-admin:dev-admin-password}

if [[ -n $season ]]; then
  body=$(printf '{"source":"sca","name":"%s","season":"%s"}' "$name" "$season")
else
  body=$(printf '{"source":"sca","name":"%s"}' "$name")
fi

# Creating a competition is idempotent: the same source + name returns the same id.
id=$(curl -sS --fail-with-body -u "$admin" -H 'Content-Type: application/json' \
  -d "$body" "$api/api/admin/stats/competitions" | sed -E 's/.*"id":"([^"]+)".*/\1/')
echo "Competition \"$name\": $id"

for kind in batting bowling fielding; do
  file="$dir/sca-$kind.csv"
  [[ -f $file ]] || { echo "skip $kind: $file not found"; continue; }
  echo "--- $kind"
  curl -sS --fail-with-body -u "$admin" -H 'Content-Type: text/plain; charset=utf-8' \
    --data-binary "@$file" \
    "$api/api/admin/stats/competitions/$id/imports/$kind?origin=$(basename "$file")&capturedOn=$(date -r "$file" +%F)"
  echo
done
