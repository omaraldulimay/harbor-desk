# 5. Keycloak and OIDC in production

Status: accepted

## Context

The demo signs tokens itself with a shared secret. That is convenient for a portfolio project and wrong for production:
a shared secret means every service that can verify can also forge, and the app would own passwords, MFA, password
reset and sessions.

## Options considered

1. **Build authentication into the app.** Full control, large security surface that has nothing to do with helpdesk logic.
2. **A managed identity service from the cloud provider.** Little to run, tied to one vendor.
3. **Keycloak (OIDC) as the identity provider.** Open source, standard protocol, one realm per environment,
   tenant stored as a user attribute and mapped into the `tenant` claim.

## Decision

Option 3 for production. Keycloak issues RS256 tokens. The app becomes an OAuth2 resource server that validates
signature (via the JWKS endpoint), issuer, audience and expiry, and reads the `tenant` claim. `JwtService` and
`/dev/token` are removed from that profile. Only the token verification changes, the filter, the tenant context and
the database access stay as they are. Keycloak runs on ECS with its own RDS database.

## Consequences

- No secret is shared with the app, keys rotate through JWKS.
- Keycloak is one more service to run, patch and back up.
- Keycloak is not built in this repository. The seam is small and the tests cover the filter with demo tokens.
- Admin endpoints keep a static key for now. In production they belong behind a separate role or the operator network.
