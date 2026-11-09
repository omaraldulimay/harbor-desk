package dev.harbordesk.support;

import dev.harbordesk.domain.Plan;
import dev.harbordesk.domain.TenantInfo;
import dev.harbordesk.domain.TenantService;
import dev.harbordesk.security.JwtService;
import java.time.Duration;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Tests run against the Postgres from scripts/local-db.sh (or the CI service). HARBOR_DB_URL overrides the default.
 * The context runs the real server on a random port, so tests exercise filters and routing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class HarborTestBase {
    public static final String ADMIN_KEY = "test-admin";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("harbor.db.url", () -> System.getenv().getOrDefault("HARBOR_DB_URL", "jdbc:postgresql://127.0.0.1:55434/harbor_test"));
        r.add("harbor.jwt.secret", () -> "test-secret-test-secret-test-secret-01");
        r.add("harbor.admin.key", () -> ADMIN_KEY);
        r.add("harbor.dev-tokens.enabled", () -> "true");
    }

    @LocalServerPort
    protected int port;
    @Autowired
    protected TenantService tenants;
    @Autowired
    protected JwtService jwt;
    @Autowired
    @Qualifier("adminDataSource")
    protected DataSource admin;
    @Value("${harbor.db.url}")
    protected String dbUrl;

    protected Api api() {
        return new Api("http://127.0.0.1:" + port);
    }

    protected TenantInfo newTenant(String prefix, Plan plan) {
        String slug = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        return tenants.onboard(slug, prefix + " test tenant", plan);
    }

    protected String token(TenantInfo t) {
        return jwt.issue(t.id(), "tester", Duration.ofMinutes(10));
    }
}
