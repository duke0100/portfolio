--liquibase formatted sql

-- Interview topic: docs/interview/database/01-database-migrations.md#versioned-migration
-- Three changesets = the expand/migrate/contract pattern: each step alone is safe to run against
-- the live ~1M-row products table (see project2 seed data). V6-003 (contract) is additionally
-- gated behind a context so it never ships in the same deploy as V6-001/V6-002 - see V6-003
-- below and docs/interview/database/01-database-migrations.md#rollback-strategy.

--changeset system:V6-001 dbms:postgresql
-- Expand: nullable column add. No table rewrite on Postgres 11+, no lock that blocks readers.
ALTER TABLE products ADD COLUMN low_stock_threshold INT;

--rollback ALTER TABLE products DROP COLUMN low_stock_threshold;

--changeset system:V6-002 dbms:postgresql
-- Migrate: backfill before any deployed code path depends on the column being set.
UPDATE products SET low_stock_threshold = 5 WHERE low_stock_threshold IS NULL;

--rollback UPDATE products SET low_stock_threshold = NULL WHERE low_stock_threshold = 5;

--changeset system:V6-003 dbms:postgresql context:contract labels:contract-v6-low-stock-threshold
-- Contract: gated behind the "contract" context (see application.properties'
-- spring.liquibase.contexts=!contract) so it does NOT run on the normal startup deploy. During a
-- rolling restart, old and new project2 instances serve traffic against the same schema at the
-- same time; if this ran as soon as the first new instance started, an old instance still
-- inserting rows without low_stock_threshold would start failing NOT NULL. Apply this deliberately,
-- as its own step, once every instance is confirmed on the version that writes the column.
-- The label is unique per changeset (not a shared "contract" bucket label) so a manual run can
-- target this one alone: -Dliquibase.contexts=contract -Dliquibase.labels=contract-v6-low-stock-threshold
-- docs/interview/database/01-database-migrations.md#rollback-strategy
ALTER TABLE products ALTER COLUMN low_stock_threshold SET DEFAULT 5;
ALTER TABLE products ALTER COLUMN low_stock_threshold SET NOT NULL;

--rollback ALTER TABLE products ALTER COLUMN low_stock_threshold DROP NOT NULL;
