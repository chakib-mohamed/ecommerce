-- liquibase formatted sql

-- changeset chakib:005-add-outbox
CREATE TABLE outbox (
    id             uuid PRIMARY KEY,
    aggregate_type varchar(255) NOT NULL,
    aggregate_id   uuid         NOT NULL,
    event_type     varchar(255) NOT NULL,
    topic          varchar(255) NOT NULL,
    payload        jsonb        NOT NULL,
    created_at     timestamptz  NOT NULL,
    published_at   timestamptz
);

CREATE INDEX idx_outbox_unpublished ON outbox (created_at) WHERE published_at IS NULL;
