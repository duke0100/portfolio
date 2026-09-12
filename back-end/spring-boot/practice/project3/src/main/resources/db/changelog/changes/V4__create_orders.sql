--liquibase formatted sql

--changeset system:V4-001 splitStatements:false
CREATE TABLE IF NOT EXISTS project3_keyspace.orders (
    order_id                uuid,
    user_id                 uuid,
    user_email              text,
    order_number            text,
    status                  text,
    total_amount            decimal,
    shipping_address        text,
    billing_address         text,
    payment_method          text,
    payment_status          text,
    notes                   text,
    shipping_fee            decimal,
    discount_amount         decimal,
    estimated_delivery_date date,
    actual_delivery_date    date,
    created_at              timestamp,
    updated_at              timestamp,
    created_by              text,
    updated_by              text,
    PRIMARY KEY (order_id)
) WITH comment = 'Customer orders (user_email denormalized from users)';
--rollback DROP TABLE IF EXISTS project3_keyspace.orders;

--changeset system:V4-002 splitStatements:false
CREATE INDEX IF NOT EXISTS idx_orders_user_id ON project3_keyspace.orders (user_id);
--rollback DROP INDEX IF EXISTS project3_keyspace.idx_orders_user_id;

--changeset system:V4-003 splitStatements:false
CREATE INDEX IF NOT EXISTS idx_orders_status ON project3_keyspace.orders (status);
--rollback DROP INDEX IF EXISTS project3_keyspace.idx_orders_status;

--changeset system:V4-004 splitStatements:false
CREATE INDEX IF NOT EXISTS idx_orders_payment_status ON project3_keyspace.orders (payment_status);
--rollback DROP INDEX IF EXISTS project3_keyspace.idx_orders_payment_status;
