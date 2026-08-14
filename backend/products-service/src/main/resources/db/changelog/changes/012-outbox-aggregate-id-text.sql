-- liquibase formatted sql

-- changeset chakib:012-outbox-aggregate-id-text
-- The outbox keys each message by the aggregate it concerns, and that key was typed uuid
-- because every aggregate it had carried so far was a product. Saga replies are keyed by
-- order id so the broker keeps per-order ordering, and an order id is a Mongo ObjectId -
-- 24 hex characters, not a uuid.
--
-- Widened rather than special-cased: the outbox is infrastructure and should not care what
-- shape an aggregate's identity takes. orders-service already stores this as a string.
-- Existing rows cast losslessly, uuid to its canonical text form.
ALTER TABLE outbox
    ALTER COLUMN aggregate_id TYPE VARCHAR(64) USING aggregate_id::text;
