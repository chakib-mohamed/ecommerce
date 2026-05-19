-- liquibase formatted sql

-- changeset chakib:001-init-schema

-- Sequences
DROP SEQUENCE IF EXISTS Category_SEQ;
DROP SEQUENCE IF EXISTS Product_SEQ;
DROP SEQUENCE IF EXISTS Promotion_SEQ;

CREATE SEQUENCE IF NOT EXISTS Category_SEQ START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS Product_SEQ START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS Promotion_SEQ START WITH 1 INCREMENT BY 50 CACHE 50;

-- Tables
DROP TABLE IF EXISTS category CASCADE;
CREATE TABLE category (
    id BIGINT PRIMARY KEY NOT NULL,
    label VARCHAR(255) UNIQUE,
    parent_id BIGINT,
    CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES category(id)
);

DROP TABLE IF EXISTS product CASCADE;
CREATE TABLE product (
    id BIGINT PRIMARY KEY NOT NULL,
    uuid UUID,
    description VARCHAR(255),
    image_key VARCHAR(255),
    price DOUBLE PRECISION,
    title VARCHAR(255)
);
CREATE INDEX idx_product_uuid ON product(uuid);

DROP TABLE IF EXISTS promotion CASCADE;
CREATE TABLE promotion (
    id BIGINT PRIMARY KEY NOT NULL,
    label VARCHAR(255),
    percentage_off DOUBLE PRECISION,
    active_from DATE,
    active_to DATE
);

DROP TABLE IF EXISTS product_category CASCADE;
-- Join Tables
CREATE TABLE product_category (
    product_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    PRIMARY KEY (product_id, category_id),
    CONSTRAINT fk_prod_cat_prod FOREIGN KEY (product_id) REFERENCES product(id),
    CONSTRAINT fk_prod_cat_cat FOREIGN KEY (category_id) REFERENCES category(id)
);

DROP TABLE IF EXISTS product_promotion CASCADE;
CREATE TABLE product_promotion (
    product_id BIGINT NOT NULL,
    promotion_id BIGINT NOT NULL,
    PRIMARY KEY (product_id, promotion_id),
    CONSTRAINT fk_prod_prom_prod FOREIGN KEY (product_id) REFERENCES product(id),
    CONSTRAINT fk_prod_prom_prom FOREIGN KEY (promotion_id) REFERENCES promotion(id)
);
