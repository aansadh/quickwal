-- liquibase formatted sql

-- changeset quickwal:2 splitStatements:false

-- 1. Add direct string columns
ALTER TABLE transactions
    ADD COLUMN currency VARCHAR(10),
    ADD COLUMN acquisition VARCHAR(50),
    ADD COLUMN status VARCHAR(50);

-- 2. Migrate existing data
UPDATE transactions t
SET currency = c.code,
    acquisition = a.name,
    status = s.name
FROM currency_types c, acquisition_types a, status_types s
WHERE t.currency_id = c.id
  AND t.acquisition_id = a.id
  AND t.status_id = s.id;

-- 3. Enforce constraints
ALTER TABLE transactions ALTER COLUMN status SET NOT NULL;

-- 4. Drop old foreign keys and columns
ALTER TABLE transactions
    DROP COLUMN currency_id,
    DROP COLUMN acquisition_id,
    DROP COLUMN status_id;

-- 5. Drop obsolete lookup tables
DROP TABLE currency_types CASCADE;
DROP TABLE acquisition_types CASCADE;
DROP TABLE status_types CASCADE;
