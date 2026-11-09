package dev.harbordesk.domain;

import java.time.Instant;

public record Comment(long id, long ticketId, String author, String body, Instant createdAt) {
}
