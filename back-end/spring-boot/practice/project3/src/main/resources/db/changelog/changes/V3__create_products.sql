--liquibase formatted sql

--changeset system:V3-001 splitStatements:false
CREATE TABLE IF NOT EXISTS project3_keyspace.products (
    product_id       uuid,
    category_id      uuid,
    category_name    text,
    name             text,
    description      text,
    price            decimal,
    stock_quantity   int,
    sku              text,
    image_url        text,
    weight           decimal,
    status           text,
    brand            text,
    dimensions       text,
    discount_percent decimal,
    created_at       timestamp,
    updated_at       timestamp,
    created_by       text,
    updated_by       text,
    PRIMARY KEY (product_id)
) WITH comment = 'Product catalog (category_name denormalized from categories)';
--rollback DROP TABLE IF EXISTS project3_keyspace.products;

--changeset system:V3-002 splitStatements:false
CREATE INDEX IF NOT EXISTS idx_products_category ON project3_keyspace.products (category_id);
--rollback DROP INDEX IF EXISTS project3_keyspace.idx_products_category;

--changeset system:V3-003 splitStatements:false
CREATE INDEX IF NOT EXISTS idx_products_sku ON project3_keyspace.products (sku);
--rollback DROP INDEX IF EXISTS project3_keyspace.idx_products_sku;

--changeset system:V3-004 splitStatements:false
CREATE INDEX IF NOT EXISTS idx_products_status ON project3_keyspace.products (status);
--rollback DROP INDEX IF EXISTS project3_keyspace.idx_products_status;
