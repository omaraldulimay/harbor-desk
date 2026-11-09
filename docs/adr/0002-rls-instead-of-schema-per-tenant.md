# 2. Row level security instead of a schema per tenant

Status: accepted

## Context

In the pool, tenants share tables. Isolation must hold even when a developer forgets a filter, so it cannot live
only in application code.

## Options considered

1. **Filter in the application.** Every query adds `WHERE tenant_id = ?`. Cheap, but one forgotten clause is a data leak.
2. **Schema per tenant.** Natural separation, but migrations run once per tenant, the catalog grows large and connection
   pooling gets awkward with `search_path`.
3. **Row level security.** A policy on each table compares `tenant_id` with a setting the app sets per transaction.
   The database enforces it.

## Decision

Option 3. Every table has `tenant_id`, `ENABLE` and `FORCE ROW LEVEL SECURITY`. The app connects as `harbor_app`, which
is no superuser, has no `BYPASSRLS` and owns no table. Each transaction starts with
`set_config('app.tenant_id', ..., true)`, so the value cannot outlive the transaction on a pooled connection.
An unset value matches no row. A composite foreign key (`tenant_id`, `ticket_id`) also stops a comment from pointing
at another tenant's ticket.

Read queries deliberately carry no tenant filter, so the policy is what the tests prove.

## Consequences

- A forgotten `WHERE` returns only own rows. The isolation proof runs 10,000 foreign attempts with 0 leaks.
- The policy adds a small cost per query and every index should start with `tenant_id`.
- A superuser or owner connection bypasses RLS (owner is subject to it only because of `FORCE`; superusers never).
  Those credentials are used for migrations and onboarding only.
- Reporting across tenants needs a separate privileged path. There is none today, on purpose.
- Pooled connections must never run queries outside `TenantTx`. See ADR 0007.
