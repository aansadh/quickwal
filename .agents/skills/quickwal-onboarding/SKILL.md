---
name: quickwal-onboarding
description: Set up, start, and troubleshoot the QuickWal application for local development. Use when someone is onboarding to this repository or asks an agent to run QuickWal locally; this is not a production deployment guide.
---

# QuickWal Onboarding

Set up and run QuickWal in the local development environment, and help diagnose startup failures. Work from the repository root. Before acting, reread the current `README.md`, `docker-compose.yaml`, startup script, and application properties; these are the sources of truth if this skill becomes stale.

## When asked to set up or start QuickWal

Carry out the setup in the available workspace instead of only listing commands. Confirm the repository root and prerequisites, make sure Docker targets a local development daemon, start PostgreSQL and Kafka, wait for both to respond, start the Spring Boot app, confirm startup in its logs, and ensure demo wallets are present when the user wants to try the API. Keep the app running and report its URLs and any remaining issue.

Use the manual workflow below by default. It preserves the named PostgreSQL volume. Do not run `scripts/start-local.sh`, `docker compose down -v`, or the SQL reset script unless the user explicitly authorizes deleting the local database data. Never point these operations at a remote or unclear Docker context.

Local setup requires `QUICKWAL_DB_PASSWORD` for both PostgreSQL and the host JVM. The committed `.env.example` is a blank template; create a private, ignored `.env` and set this variable there. Compose reads `.env` automatically, and `scripts/start-local.sh` exports it for Spring Boot. For manual Maven startup, load the same `.env` into the shell. Never commit or print the private value. Spring Boot can also receive command-line overrides; if settings differ, inspect only relevant variable names and redact their values instead of dumping the full environment.

## Requirements and local topology

- Java 21 is required. The Maven wrapper is included; a separate Maven installation is not required. The wrapper may need network access to download Maven and project dependencies.
- Docker Engine/Desktop with the Compose plugin must be installed and running. The startup script requires Bash; manual setup also works in Windows PowerShell with Docker Desktop and `mvnw.cmd`.
- QuickWal needs PostgreSQL and Kafka. Compose publishes PostgreSQL at `localhost:5433` and Kafka at `localhost:9092`. The default app datasource and Kafka broker addresses are hard-coded in `src/main/resources/application.properties` and `application.kafka.properties`.
- Kafka UI (`localhost:8080`) and Redis (`localhost:6379`) are optional Compose services; the current application does not require Redis. The app is launched on `localhost:8081`, and Swagger UI is at <http://localhost:8081/swagger-ui/index.html>.
- PostgreSQL uses a named `postgres_data` volume and fixed container name `quickwal-db`; Kafka and optional services also have fixed container names. These can conflict with another local Compose project using the same names.
- The Compose file includes CPU and memory limits for PostgreSQL and Kafka. Docker Desktop must have enough available memory, CPU, and disk to run both images.

## Manual setup and startup

From the repository root:

1. Confirm the current directory contains `pom.xml` and `docker-compose.yaml`. If `.env` does not exist, create it from `.env.example`, set a cryptographically random `QUICKWAL_DB_PASSWORD` (for example, generate 32 random bytes with `openssl rand -hex 32`), and create the file with restrictive permissions (`umask 077` on Unix-like systems). Preserve an existing `.env`; do not echo its value. If no secure generator is available, ask the user for a value rather than inventing a weak one. Check versions and the Docker endpoint:

   ```bash
   java -version
   ./mvnw -version
   docker --version
   docker compose version
   docker context show
   ```

   Confirm both Java commands use Java 21. Inspect `docker context inspect` when needed; only continue with a local Docker endpoint (`unix://` or `npipe://`). If `DOCKER_HOST` is set or the context is remote/unclear, stop and resolve the intended environment before touching containers or data. Ensure `.env` contains a non-empty password; the Compose file deliberately fails fast when it is absent.

2. Validate the Compose file and inspect any existing QuickWal services before starting a second copy:

   ```bash
   docker compose config --quiet
   docker compose ps -a
   ```

   Before launching, open the Swagger URL below or probe port 8081; if QuickWal already responds, use that instance instead of starting a duplicate process. If containers with QuickWal's fixed names belong to another project, identify the owner before stopping or removing anything.

3. Start only the required infrastructure:

   ```bash
   docker compose up -d postgres kafka
   docker compose ps -a
   ```

4. Wait for PostgreSQL to report readiness:

   ```bash
   docker compose exec -T postgres pg_isready -U quickwal -d quickwal
   ```

   It should say the server accepts connections. Also verify Kafka responds, not only that its container exists:

   ```bash
   docker compose exec -T kafka kafka-broker-api-versions --bootstrap-server localhost:9092
   ```

   If the Kafka utility is unavailable in the image, use `docker compose logs --tail=100 kafka` and its container state to diagnose startup. Kafka has no Compose health check, so give it time to initialize before starting the app.

   For a PostgreSQL volume created before this environment-based password setup, the new `.env` value does not automatically change the stored role password. Before the app starts, rotate the local role interactively with `docker compose exec postgres psql -U quickwal -d quickwal`, then run `\password quickwal` in `psql` and enter the `.env` value. If local authentication fails, use authorized existing database access; preserve the volume.

5. Load `.env` into the host process and start QuickWal in a terminal; keep that terminal/session open:

   ```bash
   set -a
   source .env
   set +a
   ./mvnw -Dspring-boot.run.arguments=--server.port=8081 spring-boot:run
   ```

   In PowerShell, set `$env:QUICKWAL_DB_PASSWORD` to the value in `.env`, then invoke `.\mvnw.cmd -Dspring-boot.run.arguments=--server.port=8081 spring-boot:run`. In Command Prompt, set the same environment variable with `set QUICKWAL_DB_PASSWORD=...` before running `mvnw.cmd`. Wait for `Started QuickWalApplication` in the Spring Boot output. Startup must complete Liquibase migrations; a running Maven process alone is not success.

6. In the local development database only, seed the example wallets if they are absent or the user wants the demo data:

   ```bash
   docker compose exec -T postgres psql -U quickwal -d quickwal \
     < src/main/resources/scripts/sql/seed_dev_data.sql
   ```

   The seed preserves balances for existing demo wallet IDs, but it updates the two demo users' names/contact/email and primary wallet IDs. Do not apply it to shared or non-development data without checking first. If the SQL reports missing tables, the app migrations did not complete; return to the application logs before retrying the seed.

7. Verify the running app by opening <http://localhost:8081/swagger-ui/index.html>. Use Swagger UI for a transfer only if the user wants an end-to-end example; follow the README's “Make a transfer with Swagger UI” steps and use a UUID `Idempotency-Key`.

Report the local API and Swagger UI URLs and leave the app process running. To stop, press `Ctrl+C` in the app terminal, then run `docker compose down` if the user also wants the Compose services stopped. `docker compose down` preserves `postgres_data`; `docker compose down -v` deletes it.

### Windows notes

Use Docker Desktop with the intended local context. The Bash convenience script needs WSL or Git Bash. In PowerShell, set `$env:QUICKWAL_DB_PASSWORD` to the value in `.env`, then invoke the wrapper in the current directory (for example, `.\mvnw.cmd -Dspring-boot.run.arguments=--server.port=8081 spring-boot:run`). If Docker Desktop integration is unavailable inside WSL, resolve that before continuing rather than switching to an unknown remote daemon.

## Destructive convenience startup

`scripts/start-local.sh` is only for a disposable local database. It starts PostgreSQL and Kafka, checks for a local Docker endpoint, refuses when `DOCKER_HOST` is set, drops all QuickWal tables, starts the app on port 8081, waits for the `Started QuickWalApplication` log line, seeds the demo wallets, then keeps the app in the foreground. Every run deletes existing QuickWal database data. Only use it after the user has explicitly authorized that reset. If it fails, the script prints its startup log; inspect the first underlying error rather than rerunning it blindly.

## Troubleshooting

Start with the failing command's complete output, then collect only relevant diagnostics:

```bash
docker context show
docker compose ps -a
docker compose logs --tail=100 postgres kafka
java -version
./mvnw -version
```

For application errors, inspect the Spring Boot output from the first exception through the root cause. Do not print secrets or the full environment. Avoid destructive cleanup as a generic fix.

- **Docker command missing, daemon unavailable, or permission denied:** install/start Docker Desktop or the local Docker service; check `docker info` and `docker compose version`. On Linux, resolve Docker socket permissions for the user instead of running the app as root without need.
- **Wrong or remote Docker context / `DOCKER_HOST` set:** stop before changing containers or data. Inspect the context endpoint and environment, and switch only when the intended local daemon is clear. The reset helper intentionally refuses non-local contexts and any `DOCKER_HOST` setting.
- **Compose parse error or missing service:** confirm the working directory is the repository root, inspect `docker compose config`, and compare the current Compose file with this skill. Do not substitute guessed service names or credentials.
- **Image pull fails, disk is full, or container exits/restarts:** check Docker Desktop/local daemon connectivity, available disk and memory, `docker compose ps -a`, and relevant service logs. Resolve the reported image/network/resource cause; do not prune volumes or remove containers as a first step.
- **Port is already allocated:** QuickWal uses host ports 5433 (PostgreSQL), 9092 (Kafka), and 8081 (app); optional Kafka UI and Redis use 8080 and 6379. Identify the listener with the platform's port-inspection tool (`ss -ltnp` on Linux, `lsof -nP -iTCP -sTCP:LISTEN` on macOS, `netstat -ano` on Windows), then stop only the known conflicting service or adjust the local mapping/config and keep all connection URLs consistent. Do not delete the database volume to fix a port conflict.
- **Container name conflict (`quickwal-db`, `quickwal-kafka`, etc.):** fixed names may belong to another checkout or Compose project. Identify that stack and stop/remove only with its owner's intent; do not force-remove containers by name.
- **Missing `QUICKWAL_DB_PASSWORD` / Compose interpolation or Spring placeholder error:** create `.env` from `.env.example`, set a non-empty local value, and ensure it is loaded into the Maven shell (`set -a; source .env; set +a`) as well as available to Compose. Never commit `.env` or print the value.
- **PostgreSQL not ready / connection refused / password authentication failed:** ensure the container is running, inspect PostgreSQL logs, and retry `pg_isready`. The app expects `localhost:5433`, database/user `quickwal`, and the same `QUICKWAL_DB_PASSWORD` in Compose and the Spring process. PostgreSQL image initialization variables apply when the data directory is first created; changing the password in `.env` does not rewrite credentials in an existing named volume. If the volume predates this change, the convenience helper updates the local role password after verifying the database name and before its reset. For the manual path, rotate it interactively with `docker compose exec postgres psql -U quickwal -d quickwal` followed by `\password quickwal`, entering the `.env` value. If local authentication fails, use authorized existing database access; never reset the volume as a guess.
- **Kafka not ready / connection refused / consumer startup errors:** inspect Kafka container state and logs, then repeat the broker API check. The app runs on the host and uses `localhost:9092`; Kafka UI, if started, connects internally to `kafka:29092`. Wait for KRaft broker initialization before restarting the app.
- **Wrong Java version or Maven wrapper failure:** verify both `java -version` and `./mvnw -version` show Java 21. Correct `JAVA_HOME`/`PATH` if they select different JDKs. If Maven or project dependencies cannot download, check network/proxy/repository access; use the wrapper rather than assuming system Maven is configured. Don't erase the local Maven cache unless logs identify a corrupt artifact.
- **Spring Boot exits before `Started QuickWalApplication`:** use the first application exception as the root cause. Check database connectivity, Kafka readiness, port 8081, Java version, dependency downloads, and any Spring command-line/environment overrides. Rerun the Maven command in the foreground to retain full logs. Do not infer success from an open port or a still-running Maven launcher.
- **Liquibase migration fails or the database schema is inconsistent:** preserve the database. Capture the first Liquibase error and inspect the current local changelog/schema state; do not run the reset SQL or `down -v` unless the user explicitly authorizes data deletion.
- **Seed SQL fails:** if tables are absent, wait until application startup/migrations succeed. If a unique constraint or existing demo ID conflicts, inspect the local rows before retrying; the seed updates fixed demo-user IDs and is intended for the local development database.
- **App responds but Swagger UI does not open:** verify the app's completed startup log and use the selected app port at `/swagger-ui/index.html`. Kafka UI is a separate optional page on port 8080; it is not QuickWal's API docs. The current app does not need Redis to serve the API.
- **Still failing / unlisted host-specific issue:** do not loop through speculative cleanup. Share the exact failed command, OS/shell, Java and Maven wrapper versions, Docker context type (not secrets), container status, relevant redacted logs, and what has already been tried. Then choose a fix based on that evidence or ask the user for the missing environment detail.

## Keep guidance current

Treat `.env.example`, `.gitignore`, `README.md`, `scripts/start-local.sh`, both SQL scripts under `src/main/resources/scripts/sql/`, `docker-compose.yaml`, `pom.xml`, and the two Spring properties files as the sources of truth. Update this skill when ports, services, startup behavior, dependencies, or seed effects change. The environment template and sample data are for local development, not production instructions.
