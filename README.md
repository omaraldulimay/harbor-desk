# Harbor Desk

[![ci](https://github.com/omaraldulimay/harbor-desk/actions/workflows/ci.yml/badge.svg)](https://github.com/omaraldulimay/harbor-desk/actions/workflows/ci.yml)

**A multi tenant helpdesk where one tenant can never see another tenant's data, even when a query forgets its `WHERE`.**

Tickets and comments for many companies, on one code base. Java 21, Spring Boot 4, PostgreSQL 16, plain SQL.
The product logic is small on purpose. The project is about isolation, fair use and the cost model of SaaS.

## Key decisions at a glance

| Question | Decision | Trade-off |
|---|---|---|
| How are tenants separated? | Postgres row level security on every table, forced, app role without owner rights ([0002](docs/adr/0002-rls-instead-of-schema-per-tenant.md)) | Small cost per query, strict rules for every connection |
| One database or many? | Pool for free and pro, silo database for enterprise, routed by a registry ([0001](docs/adr/0001-pool-and-silo.md)) | Silos cost connections and need their own migration rollout |
| Where does the tenant come from? | A signed JWT claim, never from the URL ([0003](docs/adr/0003-tenant-from-jwt.md)) | Tokens live until they expire |
| How is the noisy neighbor stopped? | Token bucket per tenant and plan, in memory ([0004](docs/adr/0004-in-memory-rate-limit.md)) | Per instance, not exact across instances |
| Who issues tokens in production? | Keycloak with OIDC ([0005](docs/adr/0005-keycloak-in-production.md)), the demo signs its own | One more service to run |
| ORM or SQL? | Plain SQL with `JdbcClient` ([0006](docs/adr/0006-plain-sql.md)) | More mapping code |
| How do we keep code from skipping the tenant? | `TenantSession` type plus ArchUnit rules ([0007](docs/adr/0007-tenant-session-and-architecture-tests.md)) | Structural checks only |

## Proofs

`make proof` writes `docs/proof/report.md`. The most important proof comes first.

| Proof | Result |
|---|---|
| **Data separation:** 3 tenants, 10,000 random read and write attempts on foreign rows | **0 leaks**, foreign data unchanged |
| Query without `WHERE tenant_id` | tenant A sees its own 20 rows, 0 foreign rows (60 in the table) |
| Silo: enterprise tenant data | 25 of 25 rows in its own database, 0 in the pool |
| Noisy neighbor: tenant A (free) floods at 500 req/s | 95.6 % of A answered 429, tenant B p95 6.9 ms without and 6.3 ms with the flood, 0 rejected |

The noisy neighbor run is noisy itself (client, server and database share one laptop), read the numbers as
"B is not hurt", not as a benchmark. Details in `docs/proof/report.md`.

## Decisions

- [0001 Pool for small tenants, silo for enterprise](docs/adr/0001-pool-and-silo.md)
- [0002 Row level security instead of a schema per tenant](docs/adr/0002-rls-instead-of-schema-per-tenant.md)
- [0003 The tenant comes from a signed JWT](docs/adr/0003-tenant-from-jwt.md)
- [0004 Rate limit per tenant, in memory](docs/adr/0004-in-memory-rate-limit.md)
- [0005 Keycloak and OIDC in production](docs/adr/0005-keycloak-in-production.md)
- [0006 Plain SQL with JdbcClient](docs/adr/0006-plain-sql.md)
- [0007 A tenant session type and architecture tests](docs/adr/0007-tenant-session-and-architecture-tests.md)

Architecture (arc42 with C4 diagrams, request flow, AWS deployment, risks): [docs/architecture.md](docs/architecture.md).
AWS sketch: `infra/terraform` (validated with `terraform fmt` and `terraform validate`, never applied).

## How isolation works

1. Every table has `tenant_id`. `ENABLE` and `FORCE ROW LEVEL SECURITY`, one policy per table.
2. The app connects as `harbor_app`: no superuser, no `BYPASSRLS`, owns nothing, no `DELETE`.
3. Each transaction starts with `set_config('app.tenant_id', <id>, true)`. It ends with the transaction.
4. Read queries have no tenant filter in the code. The database applies it.
5. Plans: free and pro share the pool database, enterprise gets its own database, picked by the `tenants` registry.

## Plans

| Plan | Database | Rate limit | Ticket quota |
|---|---|---|---|
| free | pool | 20 req/s | 50 |
| pro | pool | 100 req/s | 5,000 |
| enterprise | own database | 500 req/s | unlimited |

Over the rate limit: `429` with `Retry-After`. Over the quota: `429` without `Retry-After`, because waiting does not help.

## Run

Needs Java 21. Without Docker, `make` uses a throwaway Postgres 16 on port 55434 (binaries from
`/Library/PostgreSQL/16/bin`, data in `.local/pg`, user `harbor`). The system Postgres on 5432 is never touched.

    make test        # starts the local database, then ./mvnw verify (unit, integration, architecture tests)
    make proof       # runs the three proofs, writes docs/proof/report.md
    make run         # demo profile on http://localhost:8096, database harbor_demo, three demo tenants
    make demo-reset  # recreates the demo database (stop the app first)
    make db-down     # stop the local database

CI starts a Postgres service container and sets `HARBOR_DB_URL`.

## API

| Call | Auth |
|---|---|
| `POST /api/tickets`, `GET /api/tickets?status=&limit=`, `GET /api/tickets/{id}` | `Authorization: Bearer <jwt>` with claim `tenant` |
| `PATCH /api/tickets/{id}/status` with `{"status":"open|in_progress|resolved|closed"}` | same |
| `POST` and `GET /api/tickets/{id}/comments` | same |
| `POST /admin/tenants` with `{"slug","name","plan"}` | header `X-Admin-Key` |
| `GET /dev/token?tenant=<id>` | demo profile only |

Requests without a valid tenant are `401`. A ticket id of another tenant is `404`.

## Demo

`make run` seeds three invented tenants and logs a token for each:

| Tenant | Industry | Plan |
|---|---|---|
| Norddock Freight | logistics | pro (pool) |
| Kestrel Mutual Insurance | insurance | enterprise (own database) |
| Bramble Goods | retail | free (pool) |

    curl -s localhost:8096/api/tickets -H "Authorization: Bearer <token from the log>"

    curl -s -X POST localhost:8096/admin/tenants -H 'X-Admin-Key: demo-admin' -H 'Content-Type: application/json' \
      -d '{"slug":"acme-demo","name":"Acme Demo","plan":"enterprise"}'

## License

MIT
