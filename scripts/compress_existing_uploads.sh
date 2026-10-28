#!/usr/bin/env bash
set -euo pipefail

MODE="${1:---dry-run}"
UPLOAD_DIR="${2:-${UGC_UPLOAD_DIR:-./uploads}}"

if [[ "$MODE" != "--dry-run" ]]; then
  echo "Only --dry-run is enabled in this release. Historical compression must be reviewed before execution." >&2
  exit 2
fi

if [[ ! -d "$UPLOAD_DIR" ]]; then
  echo "Upload directory does not exist: $UPLOAD_DIR" >&2
  exit 1
fi

echo "# historical uploads compression dry-run"
echo "upload_dir=$UPLOAD_DIR"
echo

stat_size() {
  if stat -c '%s' "$1" >/dev/null 2>&1; then
    stat -c '%s' "$1"
  else
    stat -f '%z' "$1"
  fi
}

print_sized_files() {
  while IFS= read -r file; do
    size=$(stat_size "$file" 2>/dev/null || echo 0)
    printf '%s %s\n' "$size" "$file"
  done
}

print_mime_files() {
  while IFS= read -r -d '' file; do
    file --mime-type "$file"
  done
}

echo "## total"
du -sh "$UPLOAD_DIR"
find "$UPLOAD_DIR" -type f | wc -l | awk '{print "files_total=" $1}'
echo

echo "## image candidates over 500KB"
find "$UPLOAD_DIR" -type f -print0 \
  | print_mime_files \
  | awk -F: '$2 ~ /image\/(jpeg|png)/ {print $1}' \
  | print_sized_files \
  | awk '$1 > 512000 {count++; bytes += $1; print} END {print "candidate_count=" count+0; print "candidate_bytes=" bytes+0}' \
  | sort -nr || true
echo

echo "## max files top20"
find "$UPLOAD_DIR" -type f | print_sized_files | sort -nr | head -20
echo

echo "## video summary only"
find "$UPLOAD_DIR" -type f -print0 \
  | print_mime_files \
  | awk -F: '$2 ~ /video\// {print $1}' \
  | print_sized_files \
  | awk '{count++; bytes += $1; print} END {print "video_count=" count+0; print "video_bytes=" bytes+0}' \
  | sort -nr || true
echo

if command -v ffmpeg >/dev/null 2>&1; then
  echo "ffmpeg=installed"
else
  echo "ffmpeg=missing"
fi
