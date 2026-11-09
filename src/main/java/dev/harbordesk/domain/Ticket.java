package dev.harbordesk.domain;

import java.time.Instant;
import java.util.UUID;

public record Ticket(long id, UUID tenantId, String title, String body, String status, Instant createdAt) {
}
