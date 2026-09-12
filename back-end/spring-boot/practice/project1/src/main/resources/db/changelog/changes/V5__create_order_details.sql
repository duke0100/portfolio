-- liquibase formatted sql

-- changeset system:V5-001
CREATE TABLE order_details (
    id           BIGINT AUTO_INCREMENT NOT NULL,
    order_id     BIGINT                NOT NULL,
    product_id   BIGINT                NOT NULL,
    quantity     INT                   NOT NULL,
    unit_price   DECIMAL(15,2)         NOT NULL,
    discount     DECIMAL(10,2)                  DEFAULT 0,
    total_price  DECIMAL(15,2)         NOT NULL,
    product_name VARCHAR(300),
    product_sku  VARCHAR(100),
    notes        TEXT,
    tax_amount   DECIMAL(10,2)                  DEFAULT 0,
    created_at   TIMESTAMP             NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP,
    CONSTRAINT pk_order_details         PRIMARY KEY (id),
    CONSTRAINT fk_order_details_order   FOREIGN KEY (order_id)   REFERENCES orders   (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_details_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE RESTRICT
);

CREATE INDEX idx_order_details_order_id   ON order_details (order_id);
CREATE INDEX idx_order_details_product_id ON order_details (product_id);

-- rollback DROP TABLE order_details;
