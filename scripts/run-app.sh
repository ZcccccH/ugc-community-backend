#!/bin/bash
# Build (if needed) and run the backend. Reads configuration from environment variables
# or from a local .env file (copy .env.example to .env; never commit .env).
set -e

cd "$(dirname "$0")/.."
ROOT="$(pwd)"
if [ -f "$ROOT/.env" ]; then
  set -a; . "$ROOT/.env"; set +a
fi

: "${DB_USERNAME:?Set DB_USERNAME (see .env.example)}"
: "${DB_PASSWORD:?Set DB_PASSWORD (see .env.example)}"
: "${JWT_SECRET:?Set JWT_SECRET to a random string of at least 32 characters (see .env.example)}"

JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
MVN="${MVN:-mvn}"
JAR="$ROOT/target/ugc-community-backend-0.0.1-SNAPSHOT.jar"

if [ "$1" = "--build" ] || [ ! -f "$JAR" ]; then
  echo "Building ..."
  "$MVN" -q -DskipTests package
fi

echo "Starting API on http://localhost:${SERVER_PORT:-8080} (health check: /api/health)"
exec "$JAVA" -jar "$JAR"
