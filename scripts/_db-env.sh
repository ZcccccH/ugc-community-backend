#!/bin/bash
# Shared settings for the local MySQL helper scripts.
# Configure via environment variables (or a local .env file that is NOT committed).
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [ -f "$ROOT/.env" ]; then
  set -a; . "$ROOT/.env"; set +a
fi

: "${DB_USERNAME:?Set DB_USERNAME (see .env.example)}"
: "${DB_PASSWORD:?Set DB_PASSWORD (see .env.example)}"
DB_NAME="${DB_NAME:-ugc_community}"
DB_PORT="${DB_PORT:-3306}"

# Directory of a portable MySQL install; leave empty to use mysql/mysqld from PATH.
MYSQL_HOME="${MYSQL_HOME:-}"
MYSQL_BIN="${MYSQL_HOME:+$MYSQL_HOME/bin/}"
MYSQL_DATA_DIR="${MYSQL_DATA_DIR:-$HOME/mysql-data}"
MYSQL_TMP_DIR="${MYSQL_TMP_DIR:-$HOME/mysql-tmp}"
SOCK="$MYSQL_TMP_DIR/mysql.sock"

# Pass the password through the environment instead of the command line.
export MYSQL_PWD="$DB_PASSWORD"
