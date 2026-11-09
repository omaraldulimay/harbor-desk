package dev.harbordesk.domain;

public class QuotaExceededException extends RuntimeException {
    public QuotaExceededException(Plan plan) {
        super("ticket quota of the " + plan.id() + " plan is used up (" + plan.maxTickets() + ")");
    }
}
