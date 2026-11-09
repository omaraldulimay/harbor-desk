package dev.harbordesk.domain;

/** Plans decide isolation (pool or silo), the request rate and the ticket quota. */
public enum Plan {
    FREE(20, 50),
    PRO(100, 5_000),
    ENTERPRISE(500, -1);

    private final int requestsPerSecond;
    private final int maxTickets;

    Plan(int requestsPerSecond, int maxTickets) {
        this.requestsPerSecond = requestsPerSecond;
        this.maxTickets = maxTickets;
    }

    public int requestsPerSecond() {
        return requestsPerSecond;
    }

    /** Negative means unlimited. */
    public int maxTickets() {
        return maxTickets;
    }

    public boolean hasSilo() {
        return this == ENTERPRISE;
    }

    public String id() {
        return name().toLowerCase();
    }

    public static Plan parse(String value) {
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("plan must be free, pro or enterprise");
        }
    }
}
