--liquibase formatted sql

--changeset system:V2-001 dbms:postgresql
CREATE TABLE categories (
    id               BIGSERIAL                NOT NULL,
    name             VARCHAR(200)             NOT NULL,
    description      TEXT,
    parent_id        BIGINT,
    image_url        VARCHAR(500),
    slug             VARCHAR(200),
    status           VARCHAR(20)              NOT NULL DEFAULT 'ACTIVE',
    display_order    INT                               DEFAULT 0,
    level            INT                               DEFAULT 0,
    is_featured      BOOLEAN                           DEFAULT FALSE,
    meta_title       VARCHAR(200),
    meta_description TEXT,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP WITH TIME ZONE,
    created_by       VARCHAR(100),
    updated_by       VARCHAR(100),
    CONSTRAINT pk_categories        PRIMARY KEY (id),
    CONSTRAINT uq_categories_slug   UNIQUE (slug),
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories (id) ON DELETE SET NULL
);

CREATE INDEX idx_categories_parent_id ON categories (parent_id);
CREATE INDEX idx_categories_status    ON categories (status);

--rollback DROP TABLE categories;
