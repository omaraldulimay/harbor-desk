# 3. The tenant comes from a signed JWT

Status: accepted

## Context

Every request must belong to exactly one tenant, and the client must not be able to pick another one.

## Options considered

1. **Tenant in the URL or a header** (`/t/acme/...`, `X-Tenant`). Simple, but anyone can change it, so every call
   needs a second check.
2. **Tenant from the subdomain.** Nice for users, still unauthenticated on its own.
3. **Tenant as a claim in a signed token.** The identity provider vouches for it. The server only verifies the
   signature, the expiry and that the tenant exists.

## Decision

Option 3. The claim is called `tenant`. A servlet filter verifies the token, loads the tenant from the registry, applies
the rate limit and binds the tenant to the thread. No valid tenant means `401` and the request never reaches a controller.
Code that needs a tenant (`TenantTx`) fails without one.

For the demo the app signs its own HS256 tokens at `/dev/token`. That endpoint exists only when
`harbor.dev-tokens.enabled` is set. ADR 0005 explains what replaces it in production.

## Consequences

- The tenant cannot be spoofed without the signing key.
- A token stays valid until it expires. Removing a tenant takes effect when the registry says so, not when tokens run out.
- The demo token endpoint is a deliberate shortcut and must stay off in production.
- Users and roles inside a tenant are not modelled yet. The token carries `sub`, nothing more is enforced.
