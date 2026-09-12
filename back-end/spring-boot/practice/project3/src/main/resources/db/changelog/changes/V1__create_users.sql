--liquibase formatted sql

--changeset system:V1-001 splitStatements:false
CREATE TABLE IF NOT EXISTS project3_keyspace.users (
    user_id       uuid,
    username      text,
    email         text,
    password_hash text,
    first_name    text,
    last_name     text,
    phone_number  text,
    address       text,
    date_of_birth date,
    gender        text,
    status        text,
    avatar_url    text,
    created_at    timestamp,
    updated_at    timestamp,
    created_by    text,
    updated_by    text,
    PRIMARY KEY (user_id)
) WITH comment = 'Stores user account information';
--rollback DROP TABLE IF EXISTS project3_keyspace.users;

--changeset system:V1-002 splitStatements:false
CREATE INDEX IF NOT EXISTS idx_users_email ON project3_keyspace.users (email);
--rollback DROP INDEX IF EXISTS project3_keyspace.idx_users_email;

--changeset system:V1-003 splitStatements:false
CREATE INDEX IF NOT EXISTS idx_users_status ON project3_keyspace.users (status);
--rollback DROP INDEX IF EXISTS project3_keyspace.idx_users_status;
