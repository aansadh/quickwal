-- liquibase formatted sql

-- changeset quickwal:5 splitStatements:false
-- Amount and balance are existing long minor-unit columns; retain names for
-- compatibility with the JDBC adapters rather than performing a breaking rename.
UPDATE transactions SET currency = 'QC'
WHERE currency IS NULL OR length(btrim(currency)) = 0;
ALTER TABLE transactions ALTER COLUMN currency SET NOT NULL;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'transactions_currency_check') THEN
        ALTER TABLE transactions ADD CONSTRAINT transactions_currency_check CHECK (length(btrim(currency)) > 0);
    END IF;
END $$;

ALTER TABLE ledger_entries ADD COLUMN IF NOT EXISTS currency VARCHAR(10);
UPDATE ledger_entries SET currency = COALESCE(
    (SELECT t.currency FROM transactions t WHERE t.id = ledger_entries.transaction_id), 'QC'
) WHERE currency IS NULL;
ALTER TABLE ledger_entries ALTER COLUMN currency SET NOT NULL;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ledger_entries_currency_check') THEN
        ALTER TABLE ledger_entries ADD CONSTRAINT ledger_entries_currency_check CHECK (length(btrim(currency)) > 0);
    END IF;
END $$;

INSERT INTO ledger_entries (id, transaction_id, wallet_id, entry_type_id, amount, currency)
SELECT md5(t.id::text || ':debit')::uuid, t.id, t.sender_wallet_id, et.id, t.amount,
       COALESCE(NULLIF(btrim(t.currency), ''), 'QC')
FROM transactions t JOIN entry_types et ON et.name = 'DEBIT'
WHERE NOT EXISTS (
    SELECT 1 FROM ledger_entries le
    WHERE le.transaction_id = t.id AND le.wallet_id = t.sender_wallet_id AND le.entry_type_id = et.id
);
INSERT INTO ledger_entries (id, transaction_id, wallet_id, entry_type_id, amount, currency)
SELECT md5(t.id::text || ':credit')::uuid, t.id, t.receiver_wallet_id, et.id, t.amount,
       COALESCE(NULLIF(btrim(t.currency), ''), 'QC')
FROM transactions t JOIN entry_types et ON et.name = 'CREDIT'
WHERE NOT EXISTS (
    SELECT 1 FROM ledger_entries le
    WHERE le.transaction_id = t.id AND le.wallet_id = t.receiver_wallet_id AND le.entry_type_id = et.id
);
