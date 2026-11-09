#!/usr/bin/env bash
# Throwaway Postgres for local runs and tests (no Docker needed).
# Own cluster on port 55434, never touches the system Postgres on 5432.
set -euo pipefail

PG_BIN=/Library/PostgreSQL/16/bin
PORT=55434
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DATA="$ROOT/.local/pg"

case "${1:-}" in
  start)
    if [ ! -d "$DATA" ]; then
      mkdir -p "$DATA"
      "$PG_BIN/initdb" -D "$DATA" -U harbor -A trust -E UTF8 >/dev/null
    fi
    if ! "$PG_BIN/pg_ctl" -D "$DATA" status >/dev/null 2>&1; then
      "$PG_BIN/pg_ctl" -D "$DATA" -l "$ROOT/.local/pg.log" -w \
        -o "-p $PORT -c listen_addresses=127.0.0.1 -c unix_socket_directories=/tmp -c max_connections=200" start >/dev/null
    fi
    for db in harbor harbor_demo harbor_test; do
      "$PG_BIN/psql" -h 127.0.0.1 -p $PORT -U harbor -d postgres -tAc \
        "SELECT 1 FROM pg_database WHERE datname='$db'" | grep -q 1 \
        || "$PG_BIN/createdb" -h 127.0.0.1 -p $PORT -U harbor "$db"
    done
    echo "postgres ready on 127.0.0.1:$PORT (dbs harbor, harbor_demo and harbor_test, user harbor)"
    ;;
  stop)
    if [ -d "$DATA" ]; then "$PG_BIN/pg_ctl" -D "$DATA" -m fast stop >/dev/null 2>&1 || true; fi
    echo "stopped"
    ;;
  status)
    "$PG_BIN/pg_ctl" -D "$DATA" status 2>&1 | head -1 || true
    ;;
  *)
    echo "usage: $0 start|stop|status" >&2
    exit 1
    ;;
esac
