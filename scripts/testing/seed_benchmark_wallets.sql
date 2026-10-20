-- Additive, repeatable local benchmark fixture: 10,000 users and funded QC wallets.
-- This does not reset or modify existing wallets. Each wallet ID is deterministic
-- and matches benchmark_transfers_steady.py --wallet-pool-size N (indices 1..N).
-- Run only against the intended local QuickWal database:
--   docker exec -i quickwal-db psql -X -v ON_ERROR_STOP=1 -U quickwal -d quickwal < scripts/testing/seed_benchmark_wallets.sql
-- Opening balances are synthetic fixture data and do not create ledger entries.

BEGIN;

WITH benchmark_rows AS (
    SELECT
        n,
        md5('quickwal-benchmark-user-' || n::text)::uuid AS user_id,
        md5('quickwal-benchmark-wallet-' || n::text)::uuid AS wallet_id
    FROM generate_series(1, 10000) AS series(n)
)
INSERT INTO users (id, name, contact_number, email)
SELECT
    user_id,
    'Bench' || lpad(n::text, 5, '0'),
    '+1999' || lpad(n::text, 10, '0'),
    'b' || lpad(n::text, 5, '0') || '@q.test'
FROM benchmark_rows
ON CONFLICT (id) DO NOTHING;

WITH benchmark_rows AS (
    SELECT
        n,
        md5('quickwal-benchmark-user-' || n::text)::uuid AS user_id,
        md5('quickwal-benchmark-wallet-' || n::text)::uuid AS wallet_id
    FROM generate_series(1, 10000) AS series(n)
)
INSERT INTO wallets (id, user_id, balance_minor, currency)
SELECT wallet_id, user_id, 1000000, 'QC'
FROM benchmark_rows
ON CONFLICT (id) DO NOTHING;

WITH benchmark_rows AS (
    SELECT
        md5('quickwal-benchmark-user-' || n::text)::uuid AS user_id,
        md5('quickwal-benchmark-wallet-' || n::text)::uuid AS wallet_id
    FROM generate_series(1, 10000) AS series(n)
)
UPDATE users AS u
SET primary_wallet_id = benchmark_rows.wallet_id
FROM benchmark_rows
WHERE u.id = benchmark_rows.user_id
  AND u.primary_wallet_id IS DISTINCT FROM benchmark_rows.wallet_id;

COMMIT;
