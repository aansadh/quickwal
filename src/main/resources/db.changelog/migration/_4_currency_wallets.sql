-- liquibase formatted sql

-- changeset quickwal:4 splitStatements:false
-- validCheckSum: 9:a7423f24c9f82bc8ce082e7af58169a5
ALTER TABLE wallets ADD COLUMN IF NOT EXISTS currency VARCHAR(10);
UPDATE wallets SET currency = 'QC' WHERE currency IS NULL;
ALTER TABLE wallets ALTER COLUMN currency SET NOT NULL;
ALTER TABLE wallets ALTER COLUMN currency DROP DEFAULT;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'wallets_currency_check') THEN
        ALTER TABLE wallets ADD CONSTRAINT wallets_currency_check CHECK (length(btrim(currency)) > 0);
    END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_wallets_currency ON wallets(currency);
