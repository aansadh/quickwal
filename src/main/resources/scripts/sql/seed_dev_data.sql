-- Local development seed data for exercising peer-to-peer transfers.
-- Run from the repository root with:
--   docker compose exec -T postgres psql -U quickwal -d quickwal < src/main/resources/scripts/sql/seed_dev_data.sql
--
-- This script is safe to rerun. Existing seeded wallet balances are preserved.

BEGIN;

INSERT INTO users (id, name, contact_number, email)
VALUES
    ('11111111-1111-4111-8111-111111111111', 'Alice Example', '+919876543210', 'alice@example.test'),
    ('22222222-2222-4222-8222-222222222222', 'Bob Example',   '+919876543211', 'bob@example.test')
ON CONFLICT (id) DO UPDATE
SET name = EXCLUDED.name,
    contact_number = EXCLUDED.contact_number,
    email = EXCLUDED.email;

INSERT INTO wallets (id, user_id, balance_minor, currency)
VALUES
    ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '11111111-1111-4111-8111-111111111111', 100000, 'QC'),
    ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', '22222222-2222-4222-8222-222222222222', 50000, 'QC')
ON CONFLICT (id) DO NOTHING;

UPDATE users
SET primary_wallet_id = CASE id
    WHEN '11111111-1111-4111-8111-111111111111'::UUID THEN 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'::UUID
    WHEN '22222222-2222-4222-8222-222222222222'::UUID THEN 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'::UUID
END
WHERE id IN (
    '11111111-1111-4111-8111-111111111111',
    '22222222-2222-4222-8222-222222222222'
);

COMMIT;
