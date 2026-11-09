package dev.harbordesk.persistence;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("harbor.db")
public record DbProperties(String url, String adminUser, String adminPassword,
                           String appUser, String appPassword, int poolSize) {

    /** Same server, other database. Assumes a URL without query parameters. */
    public String urlFor(String dbName) {
        return url.substring(0, url.lastIndexOf('/') + 1) + dbName;
    }
}
