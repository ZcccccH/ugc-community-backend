#!/bin/bash
# Start a local MySQL server (portable install). Skips if it is already running.
set -e
. "$(dirname "$0")/_db-env.sh"

PID="$MYSQL_TMP_DIR/mysql.pid"
LOG="$MYSQL_TMP_DIR/mysql.log"
mkdir -p "$MYSQL_TMP_DIR"

if "${MYSQL_BIN}mysqladmin" --socket="$SOCK" -u "$DB_USERNAME" ping >/dev/null 2>&1; then
  echo "MySQL is already running (socket: $SOCK)"
  exit 0
fi

echo "Starting MySQL ..."
nohup "${MYSQL_BIN}mysqld" \
  ${MYSQL_HOME:+--basedir="$MYSQL_HOME"} \
  --datadir="$MYSQL_DATA_DIR" \
  --port="$DB_PORT" \
  --socket="$SOCK" \
  --pid-file="$PID" \
  >"$LOG" 2>&1 &

for i in $(seq 1 30); do
  if "${MYSQL_BIN}mysqladmin" --socket="$SOCK" -u "$DB_USERNAME" ping >/dev/null 2>&1; then
    echo "MySQL started (port $DB_PORT, database $DB_NAME)"
    exit 0
  fi
  sleep 1
done

echo "MySQL failed to start, see log: $LOG"
tail -20 "$LOG" || true
exit 1
