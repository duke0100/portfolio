--liquibase formatted sql

--changeset system:V0-001 splitStatements:false
CREATE KEYSPACE IF NOT EXISTS project3_keyspace
WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}
AND durable_writes = true;
--rollback DROP KEYSPACE IF EXISTS project3_keyspace;
