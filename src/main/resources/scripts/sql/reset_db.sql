-- use with caution. this deletes the entire database to drop all tables.

-- WARNING: DESTRUCTIVE.
-- Deletes all QuickWal tables and their data.
-- LOCAL DEVELOPMENT ONLY. NEVER RUN AGAINST PRODUCTION.

DROP TABLE IF EXISTS
    databasechangeloglock,
    databasechangelog,
    inbox_events,
    outbox_events,
    ledger_entries,
    transactions,
    wallets,
    users,
    entry_types,
    acquisition_types,
    status_types,
    currency_types
    CASCADE;
