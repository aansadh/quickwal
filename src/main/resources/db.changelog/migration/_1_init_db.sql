-- liquibase formatted sql

-- changeset quickwal:1

-- 1. Create Lookup Tables (The Enums)
CREATE TABLE IF NOT EXISTS currency_types (
                                              id SERIAL PRIMARY KEY,
                                              code VARCHAR(10) UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS status_types (
                                            id SERIAL PRIMARY KEY,
                                            name VARCHAR(20) UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS acquisition_types (
                                                 id SERIAL PRIMARY KEY,
                                                 name VARCHAR(50) UNIQUE NOT NULL
);

-- 2. Insert Seed Data (Using ON CONFLICT to ensure idempotency)
INSERT INTO currency_types (code) VALUES ('QC') ON CONFLICT (code) DO NOTHING;
INSERT INTO status_types (name) VALUES ('SUCCESSFUL'), ('FAILURE'), ('ABORTED'), ('PROCESSING') ON CONFLICT (name) DO NOTHING;
INSERT INTO acquisition_types (name) VALUES ('peer2peer'), ('qrcode') ON CONFLICT (name) DO NOTHING;

-- 3. Create Core Entities (Upgraded to UUIDs)
CREATE TABLE IF NOT EXISTS users (
                                     id UUID PRIMARY KEY,
                                     name VARCHAR(32) NOT NULL,
                                     contact_number VARCHAR(20) UNIQUE NOT NULL, -- Stored in E.164 format (e.g., +919876543210)
                                     primary_wallet_id UUID,
                                     email VARCHAR(20) UNIQUE NOT NULL,
                                     created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                     updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wallets (
                                       id UUID PRIMARY KEY,
                                       balance BIGINT NOT NULL DEFAULT 0 CHECK (balance >= 0),
                                       user_id UUID NOT NULL REFERENCES users(id),
                                       version INT NOT NULL DEFAULT 0, -- Added for Optimistic Locking
                                       created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                       updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Resolve the circular dependency safely
DO $$
    BEGIN
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.table_constraints
            WHERE constraint_name = 'fk_user_primary_wallet'
        ) THEN
            ALTER TABLE users ADD CONSTRAINT fk_user_primary_wallet
                FOREIGN KEY (primary_wallet_id) REFERENCES wallets(id);
        END IF;
    END $$;

-- Notice: No updated_at. This is a strict append-only log.
CREATE TABLE IF NOT EXISTS transactions (
                                            id UUID PRIMARY KEY,
                                            idempotency_key UUID UNIQUE NOT NULL, -- Upgraded to native UUID for B-Tree performance
                                            sender_wallet_id UUID NOT NULL REFERENCES wallets(id),
                                            receiver_wallet_id UUID NOT NULL REFERENCES wallets(id),
                                            amount BIGINT NOT NULL CHECK (amount > 0),
                                            currency_id INT NOT NULL REFERENCES currency_types(id),
                                            acquisition_id INT NOT NULL REFERENCES acquisition_types(id),
                                            status_id INT NOT NULL REFERENCES status_types(id),
                                            msg VARCHAR(255),
                                            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 4. Indexes for scaling
CREATE INDEX IF NOT EXISTS idx_transactions_sender ON transactions(sender_wallet_id);
CREATE INDEX IF NOT EXISTS idx_transactions_receiver ON transactions(receiver_wallet_id);

-- 5. Ledger & Double-Entry Accounting
CREATE TABLE IF NOT EXISTS entry_types (
                                           id SERIAL PRIMARY KEY,
                                           name VARCHAR(10) UNIQUE NOT NULL
);

INSERT INTO entry_types (name) VALUES ('DEBIT'), ('CREDIT') ON CONFLICT (name) DO NOTHING;

CREATE TABLE IF NOT EXISTS ledger_entries (
                                              id UUID PRIMARY KEY,
                                              transaction_id UUID NOT NULL REFERENCES transactions(id),
                                              wallet_id UUID NOT NULL REFERENCES wallets(id),
                                              entry_type_id INT NOT NULL REFERENCES entry_types(id),
                                              amount BIGINT NOT NULL CHECK (amount > 0),
                                              created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ledger_transaction ON ledger_entries(transaction_id);
CREATE INDEX IF NOT EXISTS idx_ledger_wallet ON ledger_entries(wallet_id);