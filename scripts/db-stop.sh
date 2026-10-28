#!/bin/bash
# Stop the local MySQL server.
. "$(dirname "$0")/_db-env.sh"

if "${MYSQL_BIN}mysqladmin" --socket="$SOCK" -u "$DB_USERNAME" ping >/dev/null 2>&1; then
  "${MYSQL_BIN}mysqladmin" --socket="$SOCK" -u "$DB_USERNAME" shutdown
  echo "MySQL stopped"
else
  echo "MySQL is not running"
fi
