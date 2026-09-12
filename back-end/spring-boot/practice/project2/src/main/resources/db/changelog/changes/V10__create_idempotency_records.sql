--liquibase formatted sql

-- Interview topic: docs/interview/rest-api/04-idempotency-in-rest-apis.md#the-store
-- Table behind Idempotency-Key on POST /api/v1/checkout/orders. Without it a retried checkout
-- would place a second order.

--changeset system:V10-001 dbms:postgresql
-- The primary key is the concurrency control: two parallel retries race to INSERT the same key
-- and exactly one of them wins.
CREATE TABLE idempotency_records (
    idempotency_key     VARCHAR(120)             NOT NULL,
    request_target      VARCHAR(200)             NOT NULL,
    request_fingerprint CHAR(64)                 NOT NULL,
    status              VARCHAR(20)              NOT NULL,
    response_status     INTEGER,
    response_body       TEXT,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at        TIMESTAMP WITH TIME ZONE,
    expires_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_idempotency_records PRIMARY KEY (idempotency_key)
);

-- Interview topic: docs/interview/rest-api/04-idempotency-in-rest-apis.md#cleanup
-- The cleanup job deletes by expires_at, so without this index it would scan the whole table
-- every 15 minutes.
CREATE INDEX idx_idempotency_records_expires_at ON idempotency_records (expires_at);

--rollback DROP TABLE idempotency_records;
