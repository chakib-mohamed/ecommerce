-- liquibase formatted sql

-- changeset chakib:002-money-as-numeric
-- The warehouse stores the same amounts as the operational store, so it has to round them the
-- same way. Left as DOUBLE PRECISION, a sum over many rows drifts from the figures the orders
-- service holds, and the dashboard quietly disagrees with the books.
--
-- numeric(12,2) holds each amount exactly at cent scale. The cast rounds existing values once,
-- half-up, matching Money.ROUNDING in the application.
ALTER TABLE fact_sales_line
    ALTER COLUMN unit_price TYPE numeric(12, 2) USING round(unit_price::numeric, 2);

ALTER TABLE fact_sales_line
    ALTER COLUMN line_revenue TYPE numeric(12, 2) USING round(line_revenue::numeric, 2);

-- percentage_off stays DOUBLE PRECISION: it is a rate, not an amount. It is only ever applied
-- to a numeric price, and the result of that is what gets rounded.
