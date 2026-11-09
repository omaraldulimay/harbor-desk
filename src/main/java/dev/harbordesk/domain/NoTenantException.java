package dev.harbordesk.domain;

public class NoTenantException extends RuntimeException {
    public NoTenantException() {
        super("no tenant in context");
    }
}
