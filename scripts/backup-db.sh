#!/usr/bin/env bash
# Dumps the production Postgres database (the docker-compose "db" service) to a
# timestamped .sql file under db-backups/, mirroring this repo's existing dump
# naming convention (sfadb-pre-<change>-<timestamp>.sql).
#
# Read-only against the database — does not touch pgdata or require downtime.
# Run this before any deploy that includes a schema migration (deploy.sh does
# this automatically), or any time you just want a manual snapshot.
#
# Usage: ./scripts/backup-db.sh [label]
#   label   short tag for the backup filename, e.g. "reasons" or "pre-deploy"
#           (defaults to "manual")
set -euo pipefail

LABEL="${1:-manual}"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="$REPO_DIR/db-backups"
OUT_FILE="$OUT_DIR/sfadb-pre-${LABEL}-${TIMESTAMP}.sql"
COMPOSE="${COMPOSE_CMD:-docker compose}"

mkdir -p "$OUT_DIR"

echo "==> Backing up sfadb -> $OUT_FILE"
cd "$REPO_DIR"
$COMPOSE exec -T db pg_dump -U postgres -d sfadb --format=plain --no-owner --no-privileges > "$OUT_FILE"

if [[ ! -s "$OUT_FILE" ]]; then
  echo "ERROR: backup file is empty — something went wrong. Aborting." >&2
  rm -f "$OUT_FILE"
  exit 1
fi

SIZE=$(du -h "$OUT_FILE" | cut -f1)
echo "==> Backup complete: $OUT_FILE ($SIZE)"
