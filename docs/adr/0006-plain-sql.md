# 6. Plain SQL with JdbcClient

Status: accepted

## Context

The interesting part of this project happens in the database: policies, roles, transaction scoped settings,
advisory locks, composite keys. An ORM would hide exactly this.

## Options considered

1. **JPA with Hibernate.** Productive for CRUD. Per tenant behavior needs filters or multi tenancy hooks, and generated SQL
   is harder to review against the RLS model.
2. **jOOQ.** Typed SQL, extra code generation step for a schema of three tables.
3. **Spring `JdbcClient` with explicit SQL and Flyway.** Every statement is visible.

## Decision

Option 3. Repositories hold the SQL. Migrations are plain SQL files, including roles, policies and grants.

## Consequences

- Reviewers can read the exact statements and the exact isolation rules.
- Row mapping is manual. For three tables that is a few lines.
- No second level cache, no lazy loading surprises, no automatic schema generation.
- Growing the model means more boilerplate. If it grew a lot, jOOQ would be the next step, not JPA.
