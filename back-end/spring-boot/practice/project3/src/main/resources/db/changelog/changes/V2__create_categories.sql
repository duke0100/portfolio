--liquibase formatted sql

--changeset system:V2-001 splitStatements:false
CREATE TABLE IF NOT EXISTS project3_keyspace.categories (
    category_id        uuid,
    parent_category_id uuid,
    name               text,
    description        text,
    image_url          text,
    slug               text,
    status             text,
    display_order      int,
    level              int,
    is_featured        boolean,
    meta_title         text,
    meta_description   text,
    created_at         timestamp,
    updated_at         timestamp,
    created_by         text,
    updated_by         text,
    PRIMARY KEY (category_id)
) WITH comment = 'Product category hierarchy (denormalized - no FK in Cassandra)';
--rollback DROP TABLE IF EXISTS project3_keyspace.categories;

--changeset system:V2-002 splitStatements:false
CREATE INDEX IF NOT EXISTS idx_categories_status ON project3_keyspace.categories (status);
--rollback DROP INDEX IF EXISTS project3_keyspace.idx_categories_status;

--changeset system:V2-003 splitStatements:false
CREATE INDEX IF NOT EXISTS idx_categories_parent ON project3_keyspace.categories (parent_category_id);
--rollback DROP INDEX IF EXISTS project3_keyspace.idx_categories_parent;
