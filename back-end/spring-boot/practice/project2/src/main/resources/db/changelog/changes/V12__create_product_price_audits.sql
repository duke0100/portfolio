--liquibase formatted sql

-- Interview topic: docs/interview/concurrency/03-async-pitfalls.md#after-commit-listener
-- One row per committed price change, written by an @Async listener after the caller's
-- transaction commits. A row here means the price change is really in the database.

--changeset system:V12-001 dbms:postgresql
CREATE TABLE product_price_audits (
    id             BIGSERIAL                NOT NULL,
    product_id     BIGINT                   NOT NULL,
    old_price      NUMERIC(15, 2)           NOT NULL,
    new_price      NUMERIC(15, 2)           NOT NULL,
    changed_by     VARCHAR(100)             NOT NULL,
    -- Read from MDC on the pool thread, so an empty column here means the TaskDecorator is gone.
    correlation_id VARCHAR(64),
    -- Which thread wrote the row. "http-nio-..." here means @Async silently ran synchronously.
    worker_thread  VARCHAR(100)             NOT NULL,
    recorded_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_product_price_audits PRIMARY KEY (id),
    CONSTRAINT fk_product_price_audits_product FOREIGN KEY (product_id) REFERENCES products (id)
);

-- The history endpoint reads the newest rows for one product, so it needs both columns.
CREATE INDEX idx_product_price_audits_product ON product_price_audits (product_id, recorded_at DESC);

--rollback DROP TABLE product_price_audits;
