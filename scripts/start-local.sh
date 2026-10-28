#!/usr/bin/env bash

# Resets the local QuickWal database, starts the application so Liquibase can
# recreate/migrate the schema, and seeds development users and wallets.
# WARNING: Every run deletes all existing data in the local quickwal database.

## quick commands to run this script: bash scripts/start-local.sh

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${PROJECT_ROOT}/docker-compose.yaml"
RESET_SQL="${PROJECT_ROOT}/src/main/resources/scripts/sql/reset_db.sql"
SEED_SQL="${PROJECT_ROOT}/src/main/resources/scripts/sql/seed_dev_data.sql"
STARTUP_LOG="$(mktemp "${TMPDIR:-/tmp}/quickwal-start-local.XXXXXX.log")"
APP_PID=""
LOG_TAIL_PID=""

cleanup() {
    if [[ -n "${LOG_TAIL_PID}" ]]; then
        kill "${LOG_TAIL_PID}" 2>/dev/null || true
    fi
    if [[ -n "${APP_PID}" ]] && kill -0 "${APP_PID}" 2>/dev/null; then
        kill "${APP_PID}" 2>/dev/null || true
        wait "${APP_PID}" 2>/dev/null || true
    fi
    rm -f -- "${STARTUP_LOG}"
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

fail_with_startup_log() {
    local message="$1"
    printf '%s\n' "${message}" >&2
    cat "${STARTUP_LOG}" >&2
    exit 1
}

cd "${PROJECT_ROOT}"

# Compose reads .env automatically, but the host JVM also needs the same value.
# Respect a value already exported by the caller; otherwise load the local .env.
if [[ -z "${QUICKWAL_DB_PASSWORD:-}" && -f "${PROJECT_ROOT}/.env" ]]; then
    set -a
    # shellcheck disable=SC1091
    source "${PROJECT_ROOT}/.env"
    set +a
fi

if [[ -z "${QUICKWAL_DB_PASSWORD:-}" ]]; then
    printf '%s\n' "QUICKWAL_DB_PASSWORD is required. Copy .env.example to .env and set a local password." >&2
    exit 1
fi
export QUICKWAL_DB_PASSWORD

if ! command -v docker >/dev/null 2>&1; then
    printf '%s\n' "Docker is required to run this local startup script." >&2
    exit 1
fi

if [[ -n "${DOCKER_HOST:-}" ]]; then
    printf '%s\n' "Refusing to reset the database while DOCKER_HOST is set; use a local Docker context." >&2
    exit 1
fi

DOCKER_CONTEXT="$(docker context show)"
DOCKER_ENDPOINT="$(docker context inspect "${DOCKER_CONTEXT}" --format '{{(index .Endpoints "docker").Host}}')"
case "${DOCKER_ENDPOINT}" in
    unix://*|npipe://*) ;;
    *)
        printf 'Refusing destructive reset with non-local Docker endpoint: %s\n' "${DOCKER_ENDPOINT}" >&2
        exit 1
        ;;
esac

printf '%s\n' "Starting local PostgreSQL and Kafka services..."
docker compose -f "${COMPOSE_FILE}" up -d postgres kafka

printf '%s\n' "Waiting for the local quickwal database..."
DATABASE_READY=false
for attempt in {1..60}; do
    if docker compose -f "${COMPOSE_FILE}" exec -T postgres \
        pg_isready -U quickwal -d quickwal >/dev/null 2>&1; then
        DATABASE_READY=true
        break
    fi
    sleep 1
done

if [[ "${DATABASE_READY}" != true ]]; then
    printf '%s\n' "PostgreSQL did not become ready within 60 seconds." >&2
    exit 1
fi

DATABASE_NAME="$(docker compose -f "${COMPOSE_FILE}" exec -T postgres \
    psql -XAt -U quickwal -d quickwal -c 'SELECT current_database();')"
if [[ "${DATABASE_NAME}" != "quickwal" ]]; then
    printf 'Refusing to reset unexpected database: %s\n' "${DATABASE_NAME}" >&2
    exit 1
fi

printf '%s\n' "Applying the configured password to the local quickwal database role..."
# Use the local database socket after the database name check. Escape SQL string
# quotes and send the statement over stdin so the password is not in argv/logs.
ESCAPED_DB_PASSWORD="${QUICKWAL_DB_PASSWORD//\'/\'\'}"
printf "ALTER ROLE quickwal WITH PASSWORD '%s';\n" "${ESCAPED_DB_PASSWORD}" | \
    docker compose -f "${COMPOSE_FILE}" exec -T postgres \
        psql -X -v ON_ERROR_STOP=1 -U quickwal -d quickwal

printf '%s\n' "Resetting the local quickwal database..."
docker compose -f "${COMPOSE_FILE}" exec -T postgres \
    psql -X -v ON_ERROR_STOP=1 -U quickwal -d quickwal < "${RESET_SQL}"

printf '%s\n' "Starting QuickWal; waiting for Spring Boot and Liquibase..."
# Kafka UI already occupies host port 8080 in docker-compose.yaml.
"${PROJECT_ROOT}/mvnw" -f "${PROJECT_ROOT}/pom.xml" \
    -Dspring-boot.run.arguments=--server.port=8081 spring-boot:run >"${STARTUP_LOG}" 2>&1 &
APP_PID="$!"

APP_STARTED=false
for attempt in {1..180}; do
    if grep -q 'Started QuickWalApplication' "${STARTUP_LOG}"; then
        APP_STARTED=true
        break
    fi
    if ! kill -0 "${APP_PID}" 2>/dev/null; then
        wait "${APP_PID}" || true
        fail_with_startup_log "QuickWal exited before completing startup."
    fi
    sleep 1
done

if [[ "${APP_STARTED}" != true ]]; then
    fail_with_startup_log "QuickWal did not report successful startup within 180 seconds."
fi

cat "${STARTUP_LOG}"

printf '%s\n' "Seeding development users and wallets..."
docker compose -f "${COMPOSE_FILE}" exec -T postgres \
    psql -X -v ON_ERROR_STOP=1 -U quickwal -d quickwal < "${SEED_SQL}"

printf '%s\n' "QuickWal is running with local development seed data. Press Ctrl+C to stop it."
tail -n 0 -F "${STARTUP_LOG}" &
LOG_TAIL_PID="$!"

set +e
wait "${APP_PID}"
APP_EXIT_CODE="$?"
set -e
exit "${APP_EXIT_CODE}"
