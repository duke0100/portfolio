-- liquibase formatted sql

-- changeset system:V4-001
CREATE TABLE orders (
    id                      BIGINT AUTO_INCREMENT NOT NULL,
    user_id                 BIGINT                NOT NULL,
    order_number            VARCHAR(50)           NOT NULL,
    status                  VARCHAR(30)           NOT NULL DEFAULT 'PENDING',
    total_amount            DECIMAL(15,2)         NOT NULL,
    shipping_address        TEXT,
    billing_address         TEXT,
    payment_method          VARCHAR(50),
    payment_status          VARCHAR(30)                    DEFAULT 'UNPAID',
    notes                   TEXT,
    shipping_fee            DECIMAL(10,2)                  DEFAULT 0,
    discount_amount         DECIMAL(10,2)                  DEFAULT 0,
    estimated_delivery_date DATE,
    actual_delivery_date    DATE,
    created_at              TIMESTAMP             NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP,
    created_by              VARCHAR(100),
    updated_by              VARCHAR(100),
    CONSTRAINT pk_orders        PRIMARY KEY (id),
    CONSTRAINT uq_orders_number UNIQUE (order_number),
    CONSTRAINT fk_orders_user   FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX idx_orders_user_id       ON orders (user_id);
CREATE INDEX idx_orders_status        ON orders (status);
CREATE INDEX idx_orders_payment_status ON orders (payment_status);

-- rollback DROP TABLE orders;
