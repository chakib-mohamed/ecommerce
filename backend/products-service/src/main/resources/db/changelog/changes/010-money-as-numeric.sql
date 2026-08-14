-- liquibase formatted sql

-- changeset chakib:010-money-as-numeric
-- Money moves off DOUBLE PRECISION. Binary floating point cannot represent most decimal
-- fractions, so a catalog price stored as a double is already not the number that was
-- entered, and the error compounds through every total computed from it.
--
-- numeric(12,2) holds the amount exactly at cent scale. The cast rounds each existing
-- value once, half-up, matching Money.ROUNDING in the application.
ALTER TABLE product
    ALTER COLUMN price TYPE numeric(12, 2) USING round(price::numeric, 2);

-- percentage_off stays a floating-point percentage: it is a rate, not an amount, and is
-- never summed into money without being applied to a numeric price first.
