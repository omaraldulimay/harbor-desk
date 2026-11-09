# 1. Pool for small tenants, silo for enterprise

Status: accepted

## Context

Harbor Desk serves many companies. A thousand free tenants with a few tickets each must be cheap. A large
customer may demand its own database for compliance, its own backup schedule or just peace of mind.
One model for everybody is either too expensive or too weak.

## Options considered

1. **Pool only.** All tenants in one database, separated by row level security. Cheapest, simplest to operate,
   but there is no answer for customers who require physical separation.
2. **Silo only.** One database per tenant. Strong separation, but migrations, connections and cost grow with
   every tenant. Free tenants would cost more than they earn.
3. **Pool and silo by plan.** The plan decides. Free and pro share the pool, enterprise gets its own database.

## Decision

Option 3. A registry table (`tenants`, in the pool database) maps each tenant to its database. A routing
`DataSource` picks the pool or the silo pool from the tenant context. Onboarding an enterprise tenant creates the
database and runs the same Flyway migrations. The silo keeps the same schema and the same RLS policies, so
there is one code path for queries.

## Consequences

- One code base and one schema serve both models. Moving a tenant between models later is a data migration, not a rewrite.
- Every silo adds a connection pool. Pools are created lazily and have no idle minimum, but hundreds of silos on one
  server would need a proxy or separate instances.
- New migrations must run on the pool and on every silo. Today the app migrates the pool at startup and a silo at onboarding;
  a rollout step that migrates all silos is an open item.
- The registry is a single point for routing. It is cached in memory and read through the app role with RLS like any other table.
