-- liquibase formatted sql

-- changeset quickwal:7 splitStatements:false
-- The previous backfill could not prove that a historical transfer had actually
-- posted. Remove only its deterministic synthetic rows; real ledger postings
-- are left untouched. New transfers are posted by the application.
DELETE FROM ledger_entries le
WHERE le.id = md5(le.transaction_id::text || ':debit')::uuid
   OR le.id = md5(le.transaction_id::text || ':credit')::uuid;
