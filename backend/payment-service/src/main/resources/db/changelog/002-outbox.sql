--liquibase formatted sql

-- The same outbox every other producing service has, created here in its settled shape rather
-- than replaying products-service's seven changesets of history. aggregate_id is varchar from
-- the start: these rows are keyed by order id, which is a Mongo ObjectId, not a uuid.
--changeset payment:002-outbox
CREATE TABLE outbox (
    id             uuid PRIMARY KEY,
    aggregate_type varchar(255) NOT NULL,
    aggregate_id   varchar(64)  NOT NULL,
    event_type     varchar(255) NOT NULL,
    topic          varchar(255) NOT NULL,
    payload        jsonb        NOT NULL,
    traceparent    varchar(64),
    created_at     timestamptz  NOT NULL,
    published_at   timestamptz,
    attempts       integer      NOT NULL DEFAULT 0,
    failed_at      timestamptz
);

--changeset payment:002-outbox-unpublished-idx
CREATE INDEX idx_outbox_unpublished ON outbox (created_at)
    WHERE published_at IS NULL AND failed_at IS NULL;
