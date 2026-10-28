# QuickWal

QuickWal is a small peer-to-peer wallet transfer service built with Java 21 and Spring Boot. It accepts transfer requests over HTTP, records them in PostgreSQL, and processes them asynchronously through Kafka. Each transfer moves through a saga so the sender debit, recipient credit, and any required refund can be tracked as separate steps.

> **Project status:** QuickWal is a development project. The seeded wallet data and credentials created from `.env.example` are for local use only; never commit your private `.env` file.

## What it does

- Accepts a transfer between two wallets and returns a transfer ID immediately.
- Uses a caller-supplied UUID in `Idempotency-Key` to make safe retries possible.
- Processes wallet debit and credit commands asynchronously with Kafka.
- Tracks transfer state in PostgreSQL and exposes it through an HTTP endpoint.
- Uses transactional outbox and inbox records to coordinate message publishing and deduplicate processing.
- Refunds the sender when the recipient credit fails after a successful debit.

The current implementation supports the `QC` currency and represents amounts as integer minor units. `QC` has a scale of zero, so `1000` means 1000 QC units.

## Quick start

Use the [QuickWal onboarding skill](.agents/skills/quickwal-onboarding/SKILL.md) to have an agent set up the local environment, start the application, and troubleshoot startup issues.

### Requirements

- Java 21
- Docker with the Compose plugin
- Bash (to use the convenience startup script)

### Start a local instance

On first setup, create a private `.env` file from the template and set a local database password:

```bash
cp .env.example .env
chmod 600 .env  # macOS/Linux/WSL
```

Edit `.env` and set `QUICKWAL_DB_PASSWORD` to a unique local value (a 32-byte hex value from `openssl rand -hex 32` is suitable). The file is git-ignored. `scripts/start-local.sh` loads it for the app, and Docker Compose reads it automatically.

From the repository root, run:

```bash
bash scripts/start-local.sh
```

The script starts PostgreSQL and Kafka, verifies the local database, updates the local database role to the configured password, **resets the local `quickwal` database**, starts the Spring Boot app on port `8081`, applies the Liquibase schema, and seeds two development wallets. It keeps the app running in the foreground; press `Ctrl+C` to stop it. Do not use this script if the local database contains data you need.

The seeded wallets are:

| Example user | Wallet ID | Starting balance |
| --- | --- | ---: |
| Alice Example (sender) | `aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa` | 100000 QC |
| Bob Example (recipient) | `bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb` | 50000 QC |

The local services use these ports:

| Service | Address |
| --- | --- |
| QuickWal API | `http://localhost:8081` |
| Swagger UI | [`http://localhost:8081/swagger-ui/index.html`](http://localhost:8081/swagger-ui/index.html) |
| PostgreSQL | `localhost:5433` |
| Kafka | `localhost:9092` |
| Kafka UI | `http://localhost:8080` |
| Redis (available in Compose) | `localhost:6379` |

### Make a transfer with Swagger UI

Open [Swagger UI](http://localhost:8081/swagger-ui/index.html) in your browser and use the seeded Alice and Bob wallets to try the API:

1. Expand `POST /api/v1/transfers` and select **Try it out**.
2. Set `Idempotency-Key` to a UUID, for example `3d6f0a1c-8f2b-4c1e-9a7d-2b5e6f0a1c3d`.
3. Enter this request body:

   ```json
   {
     "sourceWalletId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
     "targetWalletId": "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
     "amountMinor": 1000,
     "currency": "QC"
   }
   ```

4. Select **Execute**. A successful request returns `202 Accepted` with a `transferId` and a `Location` header.
5. Expand `GET /api/v1/transfers/{transferId}`, select **Try it out**, enter the returned transfer ID, and select **Execute** to check the latest state.

Transfer processing is asynchronous, so the first status may be `PROCESSING`. A transfer can pass through `PROCESSING`, `SENDER_DEBITED`, and `REFUNDING`, and ends in `SUCCESSFUL` or `FAILURE`. The response includes the wallet IDs, amount, currency, current status, and message.

`Idempotency-Key` must be a UUID string. Generate one UUID per logical transfer and reuse it unchanged when retrying that same request. Reusing the UUID with different request details returns `409 Conflict`.

### Run services manually

If you prefer to start the services yourself, first load the same password into your shell so both Compose and the Java app use it, then launch the Compose stack:

```bash
set -a
source .env
set +a
docker compose up -d postgres kafka
```

For a database volume created before this environment-based password setup, changing `.env` alone does not change PostgreSQL's existing role password. Before starting the app, update the local role interactively (using the password from `.env`):

```bash
docker compose exec postgres psql -U quickwal -d quickwal
```

At the `psql` prompt, run `\password quickwal` and enter the `.env` value when prompted, then run `\q`. If local authentication fails, use the existing authorized database access to rotate the role; preserve the volume rather than deleting it as a workaround.

In PowerShell, set `$env:QUICKWAL_DB_PASSWORD` to the value in `.env` before running Compose and Maven. Start the application on port `8081` (port `8080` is used by Kafka UI in the Compose stack):

```bash
./mvnw -Dspring-boot.run.arguments=--server.port=8081 spring-boot:run
```

To create the example users and wallets in the local database, seed them with:

```bash
docker compose exec -T postgres psql -U quickwal -d quickwal \
  < src/main/resources/scripts/sql/seed_dev_data.sql
```

Stop the services when finished:

```bash
docker compose down
```

To also delete the local PostgreSQL volume and all its data, run `docker compose down -v`.

## API

| Method | Endpoint | Description |
| --- | --- | --- |
| `POST` | `/api/v1/transfers` | Start an asynchronous wallet transfer. Requires a UUID string in the `Idempotency-Key` header. |
| `GET` | `/api/v1/transfers/{transferId}` | Read the latest persisted transfer state. |

`POST /api/v1/transfers` accepts JSON with `sourceWalletId`, `targetWalletId`, `amountMinor`, and `currency`. The `Idempotency-Key` request header must contain a UUID string. Amounts must be positive, both wallets must use the requested currency, and the source and target must differ. The endpoint returns `202 Accepted` and a transfer ID when the request has been accepted for processing.

## Swagger UI

While QuickWal is running locally, open **[Swagger UI](http://localhost:8081/swagger-ui/index.html)** to explore the API and try requests in your browser. The raw OpenAPI document is available at `http://localhost:8081/v3/api-docs`.

## Development

For agent onboarding and local startup troubleshooting, see the [QuickWal onboarding skill](.agents/skills/quickwal-onboarding/SKILL.md).

Load the database password into your shell before running Maven commands; in Bash, use `set -a; source .env; set +a`. In PowerShell, set `$env:QUICKWAL_DB_PASSWORD` to the value stored in `.env`.

Build the project:

```bash
./mvnw package
```

Run the tests (the Spring application context test needs PostgreSQL and Kafka running):

```bash
./mvnw test
```

Database schema changes are managed by Liquibase and are applied by the application during startup. The main application configuration is in `src/main/resources/application.properties`; Kafka settings are in `src/main/resources/application.kafka.properties`.

## Requirements and design decisions

The [QuickWal requirements and design board](https://excalidraw.com/#json=48Xoo1x_DTWzX5pjstTyF,qpr-Vs0440mCoCohvuUycQ) is the companion reference for this README. It contains the requirements diagrams, decision discussions, and other project essentials. Use it to understand the intent behind the product behavior and architecture described here.

## Architecture notes

The transfer API stores the initial transfer and debit command together. A scheduled relay publishes pending outbox events to Kafka. Wallet workers process commands and emit outcome events; the transfer worker advances the saga and records any next command, such as a recipient credit or sender refund. Inbox records make event handling idempotent.

For the current implementation, see [the transfer flow diagram](docs/transfer_flow.md) for the detailed request, database, and messaging sequence. The Excalidraw board above provides the broader requirements and decision context. Performance notes and sample benchmark data are available in the [`docs/`](docs/) directory.
