-- liquibase formatted sql

-- changeset system:V3-001
CREATE TABLE products (
    id               BIGINT AUTO_INCREMENT NOT NULL,
    category_id      BIGINT,
    name             VARCHAR(300)          NOT NULL,
    description      TEXT,
    price            DECIMAL(15,2)         NOT NULL,
    stock_quantity   INT                            DEFAULT 0,
    sku              VARCHAR(100),
    image_url        VARCHAR(500),
    weight           DECIMAL(10,3),
    status           VARCHAR(20)           NOT NULL DEFAULT 'ACTIVE',
    brand            VARCHAR(100),
    dimensions       VARCHAR(100),
    discount_percent DECIMAL(5,2)                   DEFAULT 0,
    created_at       TIMESTAMP             NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP,
    created_by       VARCHAR(100),
    updated_by       VARCHAR(100),
    CONSTRAINT pk_products         PRIMARY KEY (id),
    CONSTRAINT uq_products_sku     UNIQUE (sku),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE SET NULL
);

CREATE INDEX idx_products_category_id ON products (category_id);
CREATE INDEX idx_products_sku         ON products (sku);
CREATE INDEX idx_products_status      ON products (status);

-- rollback DROP TABLE products;
