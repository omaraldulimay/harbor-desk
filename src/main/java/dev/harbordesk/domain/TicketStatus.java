package dev.harbordesk.domain;

import java.util.List;

public final class TicketStatus {
    public static final List<String> ALL = List.of("open", "in_progress", "resolved", "closed");

    private TicketStatus() {
    }

    public static String require(String status) {
        if (status == null || !ALL.contains(status)) {
            throw new IllegalArgumentException("status must be one of " + ALL);
        }
        return status;
    }
}
