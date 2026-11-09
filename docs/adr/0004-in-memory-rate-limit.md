# 4. Rate limit per tenant, in memory

Status: accepted

## Context

One tenant must not slow down the others. Limits depend on the plan: free 20 req/s, pro 100, enterprise 500.
Ticket counts are limited per plan as well.

## Options considered

1. **Limit at the gateway** (ALB, WAF, API gateway). Hard to key by tenant when the tenant is inside a JWT, and plan
   limits live in our data.
2. **Shared counter in Redis.** Exact across instances, but one more moving part and a network call on every request.
3. **Token bucket per tenant in each instance.** No dependency, a few nanoseconds per request, rejects before any database work.

## Decision

Option 3. One bucket per tenant, sized by plan, refilled continuously, burst of one second. A rejected request gets
`429` and `Retry-After`. The ticket quota is a separate check inside the create transaction, guarded by a
transaction scoped advisory lock so parallel creates cannot pass the limit together. A quota hit also answers `429`.

## Consequences

- With N instances behind a load balancer a tenant can reach about N times its limit. Acceptable for protection against
  noisy neighbors, wrong for billing or strict quotas.
- Buckets are lost on restart and start full.
- Path to Redis: keep `RateLimiter.retryAfterSeconds(tenant)` as the interface and implement it with a Lua script
  (token bucket in one round trip) or `INCR` with expiry. Fall back to the local bucket if Redis is down, so an outage
  of the limiter does not take the API down.
- Buckets are never evicted. Fine for thousands of tenants, needs an expiry for millions.
