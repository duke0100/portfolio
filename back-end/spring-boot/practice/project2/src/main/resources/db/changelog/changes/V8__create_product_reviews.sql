--liquibase formatted sql

-- Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#owning-side
-- The FK lives here on the many side, which is the only place a single row can hold it.

--changeset system:V8-001 dbms:postgresql
CREATE TABLE product_reviews (
    id           BIGSERIAL                NOT NULL,
    product_id   BIGINT                   NOT NULL,
    author_name  VARCHAR(150)             NOT NULL,
    rating       INT                      NOT NULL,
    -- Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#enumerated-and-transient
    -- Text rather than a number, so the value still reads correctly if the enum is reordered.
    status       VARCHAR(20)              NOT NULL DEFAULT 'PENDING',
    title        VARCHAR(200),
    comment      TEXT,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_product_reviews         PRIMARY KEY (id),
    CONSTRAINT fk_product_reviews_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT ck_product_reviews_rating  CHECK (rating BETWEEN 1 AND 5)
);

CREATE INDEX idx_product_reviews_product_id ON product_reviews (product_id);

--rollback DROP TABLE product_reviews;
