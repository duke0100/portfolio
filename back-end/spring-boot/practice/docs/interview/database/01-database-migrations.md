# How do you handle database migrations in a Spring Boot project?

Applied in [project2](../../../project2) (Postgres + Liquibase, the module's schema is already
Liquibase-managed, so the topic extends real changesets instead of adding a second tool).
Every snippet below is real code — the link above each block opens the file it came from, and each
of those files carries an `Interview topic:` back-link to the section here.

| Section | Code in project2 |
|---|---|
| [Answer](#answer) | [`pom.xml`](../../../project2/pom.xml) |
| [Config](#config) | [`application.properties`](../../../project2/src/main/resources/application.properties) |
| [Versioned migration](#versioned-migration) | [`V6__add_low_stock_threshold_to_products.sql`](../../../project2/src/main/resources/db/changelog/changes/V6__add_low_stock_threshold_to_products.sql) |
| [Master changelog](#versioned-migration) | [`db.changelog-master.xml`](../../../project2/src/main/resources/db/changelog/db.changelog-master.xml) |
| [Entity mapping](#versioned-migration) | [`Product.java`](../../../project2/src/main/java/com/example/project2/entity/Product.java) |
| [Rollback strategy](#rollback-strategy) | [`pom.xml`](../../../project2/pom.xml) |

## Answer

I keep schema changes as versioned, checked-in changesets — Liquibase here, Flyway on other
projects — never `ddl-auto=update`. Each changeset runs once and its checksum lands in
`DATABASECHANGELOG`; a hand-edited old changeset fails the next startup instead of silently
re-running. For a live table I split a change into expand, migrate, contract steps, and I gate the
contract step out of the normal deploy — a rolling restart runs old and new instances against the
same schema, so dropping a column or adding `NOT NULL` too early breaks the old ones. In
production I reach for a forward-fix changeset over `liquibase:rollback` almost every time; see
[Rollback strategy](#rollback-strategy) for both of those in detail.

[`project2/pom.xml`](../../../project2/pom.xml):

```xml
<!-- Liquibase - see docs/interview/database/01-database-migrations.md#answer -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-liquibase</artifactId>
    <scope>compile</scope>
</dependency>
```

## Config

[`application.properties`](../../../project2/src/main/resources/application.properties):

```properties
spring.liquibase.change-log=classpath:/db/changelog/db.changelog-master.xml
spring.liquibase.enabled=true

spring.liquibase.contexts=!contract
```

| Choice | vs the alternative |
|---|---|
| `spring.liquibase.enabled=true` | vs `spring.jpa.hibernate.ddl-auto=update`: Hibernate's auto-DDL has no version history, can't be reviewed in a PR, and silently drops columns it no longer sees on an entity. project2 keeps `ddl-auto=none` (see the JPA block in the same file) for exactly this reason. |
| Migrations run on app startup | vs a separate migration step in CI/CD before deploy: startup-run is simpler for one instance, but two app instances starting together race for the changelog lock — `DATABASECHANGELOGLOCK` serializes them, the second one just waits. |
| Per-module changelog (`project1`, `project2`, `project3` each own theirs) | vs one shared changelog: each module owns a different schema/database, so a shared file would mix unrelated tables and force every module to skip changesets meant for another one. |
| `spring.liquibase.contexts=!contract` | vs no context filter: without it, every instance's startup run would apply *every* changeset, including a contract step that isn't safe yet — see [Rollback strategy](#rollback-strategy). |

## Versioned migration

[`V6__add_low_stock_threshold_to_products.sql`](../../../project2/src/main/resources/db/changelog/changes/V6__add_low_stock_threshold_to_products.sql):

```sql
--changeset system:V6-001 dbms:postgresql
ALTER TABLE products ADD COLUMN low_stock_threshold INT;

--rollback ALTER TABLE products DROP COLUMN low_stock_threshold;

--changeset system:V6-002 dbms:postgresql
UPDATE products SET low_stock_threshold = 5 WHERE low_stock_threshold IS NULL;

--rollback UPDATE products SET low_stock_threshold = NULL WHERE low_stock_threshold = 5;

--changeset system:V6-003 dbms:postgresql context:contract
ALTER TABLE products ALTER COLUMN low_stock_threshold SET DEFAULT 5;
ALTER TABLE products ALTER COLUMN low_stock_threshold SET NOT NULL;

--rollback ALTER TABLE products ALTER COLUMN low_stock_threshold DROP NOT NULL;
```

- Three changesets, not one: `products` has roughly a million rows in project2's seed data, so
  adding a `NOT NULL` column directly would need a full-table rewrite under lock. Splitting it
  into add-nullable, backfill, then constrain keeps every individual statement fast and every step
  independently revertible. `V6-003` also carries `context:contract`, so it never runs on a normal
  startup deploy — why is in [Rollback strategy](#rollback-strategy).
- The filename follows Flyway's `V<n>__<description>.sql` convention, but the mechanism is
  different. Liquibase doesn't discover files by name — the master changelog has to `<include>`
  each one explicitly (see below) — and every changeset gets its own row and checksum in
  `DATABASECHANGELOG`, not just the file as a whole.

[`db.changelog-master.xml`](../../../project2/src/main/resources/db/changelog/db.changelog-master.xml):

```xml
<include file="db/changelog/changes/V5__create_order_details.sql"/>
<include file="db/changelog/changes/V6__add_low_stock_threshold_to_products.sql"/>
```

[`Product.java`](../../../project2/src/main/java/com/example/project2/entity/Product.java)
— the entity field only reflects the schema shape after all three changesets have run:

```java
@Column(name = "low_stock_threshold", nullable = false)
@Builder.Default
private Integer lowStockThreshold = 5;
```

- Deploy order matters here. This field has to ship after `V6-002` backfills the column and
  before `V6-003` makes it `NOT NULL` — too early and the app writes into a column that doesn't
  exist yet, too late and `V6-003` fails on rows the new app already inserted without it.

## Rollback strategy

Two things that actually matter in production, before the mechanical `--rollback` syntax below.

**1. The contract step ships as its own deploy, not bundled with expand.** A rolling restart runs
old and new `project2` instances against the *same* schema at the same time — that's the whole
point of a rolling restart, there's no instant where every instance is on the new version at once.
If `V6-003` (`SET NOT NULL`) ran on the very first instance's startup, an old instance still
inserting rows without `low_stock_threshold` would start failing that constraint mid-rollout — a
schema-version mismatch between instances that never agreed to move together.

[`V6__add_low_stock_threshold_to_products.sql`](../../../project2/src/main/resources/db/changelog/changes/V6__add_low_stock_threshold_to_products.sql)
gates the contract changeset behind a Liquibase context so the normal startup run skips it:

```sql
--changeset system:V6-003 dbms:postgresql context:contract labels:contract-v6-low-stock-threshold
```

[`application.properties`](../../../project2/src/main/resources/application.properties):

```properties
spring.liquibase.contexts=!contract
```

| Step | Context | Runs on normal `spring-boot:run` startup? |
|---|---|---|
| `V6-001`, `V6-002` (expand, migrate) | none | Yes — untagged changesets always run, `!contract` doesn't filter them |
| `V6-003` (contract) | `contract` | No — excluded by `!contract` on every instance's startup |

Applying the contract step is then a deliberate, separate action, run once every instance is
confirmed on the version that writes the column — not part of the app's own deploy pipeline:

```bash
mvn -pl project2 liquibase:update "-Dliquibase.contexts=contract"
```

The `-D` assignment must be quoted as one token — in PowerShell on Windows, an unquoted
`-Dliquibase.contexts=contract` gets split at the dot into `-Dliquibase` and `.contexts=contract`,
and Maven then reads the second half as an unknown lifecycle phase instead of a property value.

**Deployment sequence.** This is the actual procedure the context gate exists to support — the
expand/migrate/contract split only stays safe if the four steps happen in this order.

| # | Step | Detail |
|---|---|---|
| 1 | Deploy the non-contract changesets | `V6-001` and `V6-002` run on every instance's normal startup — no `context:contract` tag, so `!contract` never touches them. `V6-003` stays pending. |
| 2 | Roll the new app version out to all instances | Old and new instances serve traffic against the same schema during the restart. Safe here only because the schema is still backward-compatible — the column exists and is nullable, nothing requires the new shape yet. |
| 3 | Bake | Once the rollout finishes, watch instance health for an observation window. This is the real precondition for step 4 — not a timer, confirmation that every instance is on the new version and stable. |
| 4 | Apply the contract step manually | Only once step 3 gives confidence: `mvn -pl project2 liquibase:update "-Dliquibase.contexts=contract" "-Dliquibase.labels=contract-v6-low-stock-threshold"`. Point of no return — `NOT NULL` now rejects any writer still on the old shape. |

**Targeting one contract changeset, not every pending one.** Once several features each have a
contract step waiting, `context:contract` alone can't tell them apart — it only says "this is a
contract step", not "this is *the* one I mean to run today". Liquibase requires a changeset to
match *both* the context expression and the label filter when both are given, so each contract
changeset also gets its own unique label tied to its feature, like `V6-003`'s
`labels:contract-v6-low-stock-threshold` above — never a shared `contract` bucket label, which
would be as unspecific as the context alone.

| Filter | Job | Who sets it |
|---|---|---|
| `context:contract` / `contexts=!contract` | Blanket switch: never auto-run *any* contract step in the normal deploy. One rule, no matter how many contract changesets pile up. | The deploy pipeline, always |
| `labels:contract-<feature>` | Picks out one specific contract changeset by name. | A human, running the manual contract step |

```bash
# Target only the low-stock-threshold contract step, ignore every other pending contract changeset
mvn -pl project2 liquibase:update "-Dliquibase.contexts=contract" "-Dliquibase.labels=contract-v6-low-stock-threshold"

# Normal deploy pipeline: exclude ALL contract steps regardless of how many labels exist -
# no -Dliquibase.labels at all, an unset label filter matches everything, so only context gates
mvn -pl project2 liquibase:update "-Dliquibase.contexts=!contract"
```

**2. `--rollback` is not how a real production rollback usually happens.** Two reasons. First, data
may already be built on top of the change — mechanically reversing a changeset can silently drop
rows nobody meant to lose (see `V6-002`'s rollback: it blindly resets `low_stock_threshold` back to
`NULL`, which also wipes out any value an operator hand-edited in the meantime). Second, once app
code has deployed against the new schema, reverting the schema out from under an already-running
app version breaks it the same way skipping the contract step would. The realistic fix in both
cases is a forward-fix changeset — `V7__restore_low_stock_threshold.sql` re-adding the column, or
a corrective `UPDATE` — not reversing history. In production, migrations move forward only.

[`pom.xml`](../../../project2/pom.xml) — CLI-only plugin, separate from Spring Boot's own
startup-run migration:

```xml
<plugin>
    <groupId>org.liquibase</groupId>
    <artifactId>liquibase-maven-plugin</artifactId>
    <version>4.29.2</version>
    <configuration>
        <changeLogFile>db/changelog/db.changelog-master.xml</changeLogFile>
        <url>jdbc:postgresql://localhost:5434/project2_db</url>
        <username>project2_user</username>
        <password>project2_pass</password>
        <driver>org.postgresql.Driver</driver>
    </configuration>
</plugin>
```

| Command | What it does | Real-world use |
|---|---|---|
| `mvn -pl project2 liquibase:status` | Lists changesets not yet applied, without running anything. | Safe anytime. |
| `mvn -pl project2 liquibase:updateSQL` | Prints the SQL a real run would execute. | The review step before touching a shared environment. |
| `mvn -pl project2 liquibase:update "-Dliquibase.contexts=contract"` | Applies only `context:contract` changesets. | The deliberate contract-step run, once the rollout is complete. |
| `mvn -pl project2 liquibase:rollback "-Dliquibase.rollbackCount=1"` | Runs the `--rollback` block of the most recent changeset. | Local dev, CI, or a deploy that failed before any traffic touched the new shape — not a lever to pull after go-live. |

## Comparison

| Aspect | Liquibase | Flyway |
|---|---|---|
| Change format | XML/YAML/JSON, or SQL with `--liquibase formatted sql` (used here) | Plain `.sql` files, naming (`V1__x.sql`) *is* the ordering mechanism |
| Discovery | Explicit `<include>` list in a master changelog | Auto-discovered by filename pattern in `classpath:db/migration` |
| Rollback | Built-in `--rollback` block per changeset, `liquibase rollback` | Community edition: none — write a new forward migration; Teams edition adds `undo` scripts |
| Preconditions | `preConditions` tags can skip/fail a changeset based on DB state | Not supported — a migration either applies or the whole run fails |
| Tracking table | `DATABASECHANGELOG` (+ `DATABASECHANGELOGLOCK`) | `flyway_schema_history` |
| Boot starter | `spring-boot-starter-liquibase` | `spring-boot-starter-flyway` (auto-configured if on the classpath) |
| Use when | You need preconditions, XML/YAML diffing tooling, or built-in rollback scripts | You want the simplest possible mental model: one SQL file per version, nothing else to learn |

Both are equally "correct" for versioned migrations; the difference that actually matters day to
day is Liquibase's built-in rollback vs Flyway's forward-only-by-default model.

## Pitfalls

| Pitfall | Why it hurts |
|---|---|
| Editing an already-applied changeset | Liquibase checksums each changeset; a stale checksum fails startup on every other environment that already ran the original. Add a new changeset instead. |
| `ddl-auto=update` alongside Liquibase | Hibernate and Liquibase both try to own the schema; whichever runs second can undo or conflict with the other's DDL. project2 sets `ddl-auto=none` for this reason. |
| One giant changeset for `ADD COLUMN NOT NULL` on a large table | Locks/rewrites the whole table in one shot; split into expand/migrate/contract like `V6` does. |
| Contract step ungated, ships in the same deploy as expand | During a rolling restart, an old instance still on the previous app version hits the new constraint mid-rollout and starts failing writes — gate it behind a context like `V6-003` does. |
| Rolling back a changeset after app code already depends on it | The rollback SQL succeeds, but the running app instances now write into a table shape they no longer match — write a forward-fix instead. |
| No `liquibase:updateSQL` review before running against shared environments | The first time anyone sees the generated SQL is when it already ran. |

## Follow-up questions

**Why not just use `ddl-auto=update`?** No history, no code review, no rollback, and Hibernate
will drop a column it no longer sees mapped on an entity — that's data loss with no changeset to
point to afterward.

**How do two instances starting at once avoid running the same migration twice?**
`DATABASECHANGELOGLOCK` is a single-row lock table; the second instance to start blocks until the
first releases it, then sees the changesets already applied and skips them.

**When would you pick Flyway over Liquibase for a new project?** When the team wants the simplest
possible model — one `.sql` file per version, ordering by filename, no XML to learn — and doesn't
need built-in rollback scripts or preconditions.

**How do you test a migration before it hits a shared database?** `mvn liquibase:updateSQL`
prints the exact SQL without running it; `src/test/resources/application.properties` disables
Liquibase entirely for the JPA test slices (`spring.liquibase.enabled=false`) and lets H2's
`ddl-auto=create-drop` build the schema straight from the entities instead.

**Why can't the contract step just run automatically once, on the next deploy?** Nothing on the
DB side tells Liquibase "every instance is now on the new app version" — that's a fact about your
fleet, not about the schema. A context flag makes that call a deliberate, human-triggered step
instead of a race between whichever instance restarts first.

## Try it

```bash
cd back-end/spring-boot/practice
docker-compose up -d postgresql          # local Postgres on localhost:5434

mvn -pl project2 liquibase:status        # pending changesets, nothing applied yet
mvn -pl project2 liquibase:updateSQL     # review the exact SQL first
mvn -pl project2 spring-boot:run         # startup applies V1..V6-002 only - contexts=!contract skips V6-003

mvn -pl project2 liquibase:status                                       # confirm V6-003 (contract) still pending
mvn -pl project2 liquibase:update "-Dliquibase.contexts=contract"       # deliberate contract step
mvn -pl project2 liquibase:rollback "-Dliquibase.rollbackCount=1" "-Dliquibase.contexts=contract"   # undoes V6-003 only
```

## References

- [Liquibase formatted SQL changelogs](https://docs.liquibase.com/concepts/changelogs/sql-format.html)
- [Liquibase Maven plugin](https://docs.liquibase.com/tools-integrations/maven/home.html)
- [Flyway documentation](https://documentation.red-gate.com/fd)
- [Spring Boot database initialization](https://docs.spring.io/spring-boot/reference/data/sql.html#data.sql.migration-tool)
