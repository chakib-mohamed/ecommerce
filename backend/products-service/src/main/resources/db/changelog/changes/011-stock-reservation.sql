-- liquibase formatted sql

-- changeset chakib:011-stock-reservation
-- Stock held against an order. Nothing read product.stock before this, so the catalog would
-- sell the same unit to every buyer who asked.
--
-- The row doubles as the idempotency record: saga commands arrive at-least-once, and the
-- presence of rows for an order is what tells a redelivered reserve that its work is done.
-- A rejected reservation is recorded too, so redelivery repeats the original refusal rather
-- than re-testing stock that has since moved.
CREATE TABLE stock_reservation (
    id          BIGSERIAL PRIMARY KEY,
    order_id    VARCHAR(255) NOT NULL,
    product_id  VARCHAR(255) NOT NULL,
    quantity    INTEGER      NOT NULL,
    status      VARCHAR(32)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL
);

-- Reservation is keyed by order, and every lookup is by order id.
CREATE INDEX idx_stock_reservation_order ON stock_reservation (order_id);

-- One row per order line. Also stops a concurrent redelivery from inserting a second hold
-- for the same line if both pass the emptiness check at once.
CREATE UNIQUE INDEX uq_stock_reservation_order_product
    ON stock_reservation (order_id, product_id);
