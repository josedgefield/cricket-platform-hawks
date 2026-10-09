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
#              HAWKS_EMAIL / HAWKS_PASSWORD: an admin or superuser account
#              (default dev-admin@hawks.local / dev-admin-password, the dev profile login).
set -euo pipefail

dir=${1:?usage: $0 <dir> [competition name] [season]}
name=${2:-SCA Club League 2025 - Division 3}
season=${3:-2025}
api=${HAWKS_API:-http://localhost:8080}
email=${HAWKS_EMAIL:-dev-admin@hawks.local}
password=${HAWKS_PASSWORD:-dev-admin-password}

# Sign in once for a session token.
signin=$(printf '{"email":"%s","password":"%s"}' "$email" "$password")
token=$(curl -sS --fail-with-body -H 'Content-Type: application/json' -d "$signin" \
  "$api/api/auth/sign-in" | sed -E 's/.*"token":"([^"]+)".*/\1/')
auth="Authorization: Bearer $token"

if [[ -n $season ]]; then
  body=$(printf '{"source":"sca","name":"%s","season":"%s"}' "$name" "$season")
else
  body=$(printf '{"source":"sca","name":"%s"}' "$name")
fi

# Creating a competition is idempotent: the same source + name returns the same id.
id=$(curl -sS --fail-with-body -H "$auth" -H 'Content-Type: application/json' \
  -d "$body" "$api/api/admin/stats/competitions" | sed -E 's/.*"id":"([^"]+)".*/\1/')
echo "Competition \"$name\": $id"

for kind in batting bowling fielding; do
  file="$dir/sca-$kind.csv"
  [[ -f $file ]] || { echo "skip $kind: $file not found"; continue; }
  echo "--- $kind"
  curl -sS --fail-with-body -H "$auth" -H 'Content-Type: text/plain; charset=utf-8' \
    --data-binary "@$file" \
    "$api/api/admin/stats/competitions/$id/imports/$kind?origin=$(basename "$file")&capturedOn=$(date -r "$file" +%F)"
  echo
done

# Sign this session out again.
curl -sS -o /dev/null -X POST -H "$auth" "$api/api/auth/sign-out" || true
