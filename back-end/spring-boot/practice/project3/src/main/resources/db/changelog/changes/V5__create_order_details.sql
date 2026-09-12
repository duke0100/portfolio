--liquibase formatted sql

--changeset system:V5-001 splitStatements:false
CREATE TABLE IF NOT EXISTS project3_keyspace.order_details (
    order_id        uuid,
    order_detail_id uuid,
    product_id      uuid,
    quantity        int,
    unit_price      decimal,
    discount        decimal,
    total_price     decimal,
    product_name    text,
    product_sku     text,
    notes           text,
    tax_amount      decimal,
    created_at      timestamp,
    updated_at      timestamp,
    PRIMARY KEY (order_id, order_detail_id)
) WITH CLUSTERING ORDER BY (order_detail_id ASC)
AND comment = 'Order line items (order_id partition key for co-location with parent order)';
--rollback DROP TABLE IF EXISTS project3_keyspace.order_details;

--changeset system:V5-002 splitStatements:false
CREATE INDEX IF NOT EXISTS idx_order_details_product ON project3_keyspace.order_details (product_id);
--rollback DROP INDEX IF EXISTS project3_keyspace.idx_order_details_product;
