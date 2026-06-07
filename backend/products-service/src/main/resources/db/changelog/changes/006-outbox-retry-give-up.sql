-- liquibase formatted sql

-- changeset chakib:006-outbox-retry-give-up
ALTER TABLE outbox ADD COLUMN attempts  integer NOT NULL DEFAULT 0;
ALTER TABLE outbox ADD COLUMN failed_at timestamptz;

DROP INDEX idx_outbox_unpublished;
CREATE INDEX idx_outbox_unpublished ON outbox (created_at)
    WHERE published_at IS NULL AND failed_at IS NULL;
