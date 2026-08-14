--liquibase formatted sql

--changeset payment:001-payment
CREATE TABLE payment (
    id             uuid PRIMARY KEY,
    order_id       varchar(64)  NOT NULL,
    step_id        varchar(64)  NOT NULL,
    provider_ref   varchar(255),
    status         varchar(16)  NOT NULL,
    amount         numeric(12,2) NOT NULL,
    currency       varchar(3)   NOT NULL,
    failure_reason varchar(255),
    created_at     timestamptz  NOT NULL
);

-- One row per saga attempt. This is what makes a redelivered capture cheap - and, if two arrive
-- at once, what stops both of them recording a charge.
--changeset payment:001-payment-attempt-unique
ALTER TABLE payment ADD CONSTRAINT uq_payment_attempt UNIQUE (order_id, step_id);

--changeset payment:001-payment-order-idx
CREATE INDEX idx_payment_order ON payment (order_id);
