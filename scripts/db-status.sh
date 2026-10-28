#!/bin/bash
# Show local MySQL status and a few table counts.
. "$(dirname "$0")/_db-env.sh"

if ! "${MYSQL_BIN}mysqladmin" --socket="$SOCK" -u "$DB_USERNAME" ping >/dev/null 2>&1; then
  echo "MySQL: not running"
  exit 1
fi

echo "MySQL: running (port $DB_PORT)"
"${MYSQL_BIN}mysql" --socket="$SOCK" -u "$DB_USERNAME" "$DB_NAME" \
  -e "SELECT (SELECT COUNT(*) FROM user) AS users, (SELECT COUNT(*) FROM work) AS works, (SELECT COUNT(*) FROM tag) AS tags;" 2>/dev/null
