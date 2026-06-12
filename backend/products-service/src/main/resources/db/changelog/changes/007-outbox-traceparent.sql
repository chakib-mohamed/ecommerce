-- liquibase formatted sql

-- changeset chakib:007-outbox-traceparent
ALTER TABLE outbox ADD COLUMN traceparent varchar(64);
