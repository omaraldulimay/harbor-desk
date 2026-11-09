# 7. A tenant session type and architecture tests

Status: accepted

## Context

RLS only works when each transaction sets the tenant. If one code path forgets, queries on a pooled connection run
without a tenant, or worse, with the previous one if the setting were session scoped.

## Options considered

1. **Convention and code review.** Cheap, fails silently.
2. **A Spring AOP aspect around services.** Hidden control flow, easy to bypass with a direct call.
3. **A type that proves the context.** Repository methods take a `TenantSession`. Only `TenantTx` can create one, and it
   does so after opening the transaction and setting the tenant. ArchUnit tests keep the structure honest.

## Decision

Option 3. `TenantTx.run(session -> ...)` is the only entry to tenant data. The setting is transaction local
(`set_config(..., true)`). ArchUnit rules fail the build if:

- web, domain, security or rate limit code uses JDBC, Flyway or transaction classes,
- web code depends on persistence,
- a public repository method has no `TenantSession` parameter,
- anything but `TenantTx` constructs a `TenantSession`,
- repositories are used outside the domain,
- `JdbcClient` is used outside the persistence package.

## Consequences

- The compiler and the tests stop a repository call without a tenant. RLS stays the second line of defense.
- Services must wrap their work in one `tx.run`, which also makes the transaction boundary obvious.
- The rules are structural. They cannot see a developer who opens a second connection inside the persistence package.
