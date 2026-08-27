#!/usr/bin/env bash
# Restores sfadb from a backup produced by backup-db.sh.
#
# DESTRUCTIVE: drops and recreates the "db" service's sfadb database, replacing
# ALL current data with the backup's contents. This is an emergency-recovery
# tool, not a routine step — only run it deliberately, e.g. to recover from a
# deploy that corrupted data (the additive-only migrations in this repo should
# never require this; it exists as a safety net regardless).
#
# Usage: ./scripts/restore-db.sh <path-to-backup.sql>
set -euo pipefail

BACKUP_FILE="${1:?Usage: restore-db.sh <path-to-backup.sql>}"
COMPOSE="${COMPOSE_CMD:-docker compose}"
REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_DIR"

if [[ ! -f "$BACKUP_FILE" ]]; then
  echo "ERROR: backup file not found: $BACKUP_FILE" >&2
  exit 1
fi

echo "This will REPLACE ALL DATA in the production 'sfadb' database with:"
echo "  $BACKUP_FILE"
echo
read -rp "Type 'restore' to confirm: " CONFIRM
if [[ "$CONFIRM" != "restore" ]]; then
  echo "Aborted — no changes made."
  exit 1
fi

echo "==> Stopping app (so nothing writes to the database during restore)"
$COMPOSE stop app

echo "==> Dropping and recreating sfadb"
$COMPOSE exec -T db psql -U postgres -c "DROP DATABASE IF EXISTS sfadb;"
$COMPOSE exec -T db psql -U postgres -c "CREATE DATABASE sfadb;"

echo "==> Restoring from backup"
$COMPOSE exec -T db psql -U postgres -d sfadb < "$BACKUP_FILE"

echo "==> Restarting app"
$COMPOSE up -d --no-deps app

echo "==> Restore complete."
