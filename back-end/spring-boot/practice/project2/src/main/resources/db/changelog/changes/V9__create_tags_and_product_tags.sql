--liquibase formatted sql

-- Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#jointable-many-to-many
-- A product has many tags and a tag has many products, so the pair needs a table of its own.

--changeset system:V9-001 dbms:postgresql
-- Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#id-and-generatedvalue
-- INCREMENT BY 50 has to match allocationSize on Tag, or the two will hand out the same ids.
CREATE SEQUENCE tags_seq INCREMENT BY 50 START WITH 1;

CREATE TABLE tags (
    id         BIGINT                   NOT NULL,
    name       VARCHAR(100)             NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tags   PRIMARY KEY (id),
    CONSTRAINT uk_tags_name UNIQUE (name)
);

--changeset system:V9-002 dbms:postgresql
-- The composite primary key is what stops the same tag being attached to a product twice.
CREATE TABLE product_tags (
    product_id BIGINT NOT NULL,
    tag_id     BIGINT NOT NULL,
    CONSTRAINT pk_product_tags         PRIMARY KEY (product_id, tag_id),
    CONSTRAINT fk_product_tags_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_product_tags_tag     FOREIGN KEY (tag_id)     REFERENCES tags     (id) ON DELETE CASCADE
);

-- The PK already covers product_id, so this index is for lookups that start from the tag.
CREATE INDEX idx_product_tags_tag_id ON product_tags (tag_id);

--rollback DROP TABLE product_tags;
--rollback DROP TABLE tags;
--rollback DROP SEQUENCE tags_seq;
