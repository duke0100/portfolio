--liquibase formatted sql

-- Interview topic: docs/interview/rest-api/05-pagination-and-sorting.md#guarding-the-sort
-- One index per sort property the catalog endpoint allows. A sort the database cannot read from
-- an index makes it sort all ~1M product rows again for every single page.

--changeset system:V11-001 runInTransaction:false dbms:postgresql
-- Backs the default sort (createdAt desc, id desc). The id is there to break ties: without it two
-- products sharing a created_at can swap places between requests and show up on two pages.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_products_created_at_id ON products (created_at DESC, id DESC);

--rollback DROP INDEX IF EXISTS idx_products_created_at_id;

--changeset system:V11-002 runInTransaction:false dbms:postgresql
-- Backs ?sort=name,asc.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_products_name_id ON products (name, id);

--rollback DROP INDEX IF EXISTS idx_products_name_id;

--changeset system:V11-003 runInTransaction:false dbms:postgresql
-- Backs ?sort=price,asc. idx_products_brand_price cannot serve this, because a composite index
-- only helps an ORDER BY that starts with its first column.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_products_price_id ON products (price, id);

--rollback DROP INDEX IF EXISTS idx_products_price_id;
