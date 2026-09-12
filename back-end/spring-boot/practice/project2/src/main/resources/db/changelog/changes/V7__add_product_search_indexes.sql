--liquibase formatted sql

-- Interview topic: docs/interview/spring-data-jpa/02-jpql-criteria-native-queries.md#native-query
-- Indexes for the native full-text search. A GIN index over an expression works only on
-- PostgreSQL, so neither the query nor the index that makes it fast can move to another database.

--changeset system:V7-001 runInTransaction:false dbms:postgresql
-- The expression below must match the WHERE clause in ProductRepository.searchWithFullText
-- exactly, or the database ignores the index and reads all ~1M rows.
-- CONCURRENTLY lets writes continue while the index is built, but PostgreSQL does not allow it
-- inside a transaction - that is why this changeset sets runInTransaction:false.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_products_fulltext
    ON products
    USING GIN (to_tsvector('english', name || ' ' || coalesce(description, '')));

--rollback DROP INDEX IF EXISTS idx_products_fulltext;

--changeset system:V7-002 runInTransaction:false dbms:postgresql
-- Helps the brand and price filters that all three query styles share.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_products_brand_price ON products (brand, price);

--rollback DROP INDEX IF EXISTS idx_products_brand_price;
