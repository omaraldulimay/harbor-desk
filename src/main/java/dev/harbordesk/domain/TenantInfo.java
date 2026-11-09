package dev.harbordesk.domain;

import java.util.UUID;

/** What the request path needs to know about a tenant. dbName is null for pool tenants. */
public record TenantInfo(UUID id, String slug, Plan plan, String dbName) {
}
