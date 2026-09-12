-- liquibase formatted sql

-- changeset system:V1-001
CREATE TABLE users (
    id             BIGINT AUTO_INCREMENT NOT NULL,
    username       VARCHAR(100)          NOT NULL,
    email          VARCHAR(255)          NOT NULL,
    password_hash  VARCHAR(255)          NOT NULL,
    first_name     VARCHAR(100),
    last_name      VARCHAR(100),
    phone_number   VARCHAR(20),
    address        TEXT,
    date_of_birth  DATE,
    gender         VARCHAR(10),
    status         VARCHAR(20)           NOT NULL DEFAULT 'ACTIVE',
    avatar_url     VARCHAR(500),
    created_at     TIMESTAMP             NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP,
    created_by     VARCHAR(100),
    updated_by     VARCHAR(100),
    CONSTRAINT pk_users          PRIMARY KEY (id),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email    UNIQUE (email)
);

CREATE INDEX idx_users_email  ON users (email);
CREATE INDEX idx_users_status ON users (status);

-- rollback DROP TABLE users;
