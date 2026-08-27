#!/usr/bin/env bash
# Deploys the current branch to production via docker compose.
#
# What this does, in order:
#   1. Refuses to run if the working tree has uncommitted changes (safety net
#      against deploying local/uncommitted edits by accident).
#   2. Fetches and hard-resets to the latest commit on the given branch.
#   3. Takes a full pg_dump backup of the "db" service BEFORE touching anything
#      (via backup-db.sh).
#   4. Builds a new image for the "app" service only.
#   5. Restarts ONLY the "app" service. "db", "redis", "minio" and their named
#      volumes (pgdata, redisdata, miniodata) are never recreated or touched —
#      production data is untouched by this script.
#   6. Tails the app's startup logs and fails loudly if the app didn't actually
#      come up (schema validation failure, Flyway error, etc).
#
# Why this is safe for a database with real data: the migrations shipped with
# this change (see src/main/resources/db/migration) are additive only — CREATE
# TABLE + INSERT, no ALTER or DROP — so they cannot corrupt or remove existing
# data. Hibernate is configured with ddl-auto=validate (see application.yml),
# so the app will refuse to start rather than silently apply any schema change
# outside of a Flyway migration.
#
# If something goes wrong: the app container can be rolled back to the previous
# image/commit safely even after a migration has run — validate only fails on
# MISSING tables/columns, never on extra ones a newer migration added. Restore
# the pre-deploy backup with restore-db.sh only if data itself was corrupted,
# which these additive migrations should never cause.
#
# Usage: ./scripts/deploy.sh [branch]   (defaults to "main")
set -euo pipefail

BRANCH="${1:-main}"
COMPOSE="${COMPOSE_CMD:-docker compose}"   # set COMPOSE_CMD=docker-compose if this server uses the standalone binary
REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_DIR"

echo "==> Checking working tree is clean"
if [[ -n "$(git status --porcelain)" ]]; then
  echo "ERROR: working tree has uncommitted changes. Commit or stash before deploying." >&2
  git status --short
  exit 1
fi

echo "==> Fetching and checking out $BRANCH"
git fetch origin "$BRANCH"
git checkout "$BRANCH"
git reset --hard "origin/$BRANCH"
COMMIT="$(git rev-parse --short HEAD)"
echo "==> Deploying commit $COMMIT"

echo "==> Backing up database before deploying"
"$REPO_DIR/scripts/backup-db.sh" "deploy-${COMMIT}"

echo "==> Building app image"
$COMPOSE build app

echo "==> Restarting app service only (db/redis/minio untouched)"
$COMPOSE up -d --no-deps app

echo "==> Waiting for app to start (up to ~90s)..."
STARTED=false
for _ in $(seq 1 30); do
  LOGS="$($COMPOSE logs app --since=2m 2>&1)"
  if echo "$LOGS" | grep -q "Started SfaApplication"; then
    STARTED=true
    break
  fi
  if echo "$LOGS" | grep -qi "FlywayValidateException\|APPLICATION FAILED TO START"; then
    echo "ERROR: app failed to start. Recent logs:" >&2
    echo "$LOGS" | tail -100 >&2
    exit 1
  fi
  sleep 3
done

if [[ "$STARTED" != "true" ]]; then
  echo "ERROR: app did not report startup within the timeout. Recent logs:" >&2
  $COMPOSE logs app --since=2m --tail=100 >&2
  exit 1
fi

echo "==> App started. Migration activity from this deploy:"
echo "$LOGS" | grep -i "flyway\|migrat" || echo "(no Flyway log lines found in this window — check 'docker compose logs app' manually)"

echo "==> Deploy of $COMMIT complete."
