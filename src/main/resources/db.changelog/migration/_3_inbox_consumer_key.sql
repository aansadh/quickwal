-- liquibase formatted sql

-- changeset quickwal:3 splitStatements:false

-- Each consumer must be able to process the same event independently.
ALTER TABLE inbox_events
    DROP CONSTRAINT IF EXISTS inbox_events_pkey;

ALTER TABLE inbox_events
    ADD CONSTRAINT inbox_events_pkey
    PRIMARY KEY (event_id, consumer_name);
