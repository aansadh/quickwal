-- liquibase formatted sql

-- changeset quickwal:6 splitStatements:false
-- validCheckSum: 9:a58eeaf031cdfabdb5011709750f320d
ALTER TABLE transactions RENAME COLUMN amount TO amount_minor;
ALTER TABLE wallets RENAME COLUMN balance TO balance_minor;
ALTER TABLE ledger_entries RENAME COLUMN amount TO amount_minor;
ALTER TABLE transactions ADD CONSTRAINT transactions_amount_minor_check CHECK (amount_minor > 0);
ALTER TABLE wallets ADD CONSTRAINT wallets_balance_minor_check CHECK (balance_minor >= 0);
ALTER TABLE ledger_entries ADD CONSTRAINT ledger_entries_amount_minor_check CHECK (amount_minor > 0);
ALTER TABLE ledger_entries ADD COLUMN IF NOT EXISTS currency VARCHAR(10);
UPDATE ledger_entries le SET currency = COALESCE(NULLIF(btrim(t.currency), ''), 'QC')
FROM transactions t WHERE t.id = le.transaction_id AND le.currency IS NULL;
ALTER TABLE ledger_entries ALTER COLUMN currency SET NOT NULL;
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ledger_wallet_currency_check') THEN
  ALTER TABLE ledger_entries ADD CONSTRAINT ledger_wallet_currency_check
  CHECK (length(btrim(currency)) > 0);
 END IF;
END $$;
CREATE OR REPLACE FUNCTION enforce_ledger_wallet_currency()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE wallet_currency VARCHAR(10);
BEGIN
 SELECT currency INTO wallet_currency FROM wallets WHERE id = NEW.wallet_id;
 IF wallet_currency IS NULL OR wallet_currency <> NEW.currency THEN
  RAISE EXCEPTION 'Ledger currency must match wallet currency';
 END IF;
 RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS ledger_wallet_currency_consistency ON ledger_entries;
CREATE TRIGGER ledger_wallet_currency_consistency
BEFORE INSERT OR UPDATE OF wallet_id, currency ON ledger_entries
FOR EACH ROW EXECUTE FUNCTION enforce_ledger_wallet_currency();
