package dev.harbordesk.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
    private final JwtService jwt = new JwtService("a-secret-that-is-at-least-32-chars-long");

    @Test
    void roundTrip() {
        UUID tenant = UUID.randomUUID();
        assertThat(jwt.verifyTenant(jwt.issue(tenant, "ann", Duration.ofMinutes(1)))).contains(tenant);
    }

    @Test
    void rejectsOtherKeyExpiredAndTampered() {
        UUID tenant = UUID.randomUUID();
        String token = jwt.issue(tenant, "ann", Duration.ofMinutes(1));
        assertThat(new JwtService("another-secret-that-is-also-32-chars!!").verifyTenant(token)).isEmpty();
        assertThat(jwt.verifyTenant(jwt.issue(tenant, "ann", Duration.ofSeconds(-1)))).isEmpty();

        String[] p = token.split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"tenant\":\"" + UUID.randomUUID() + "\",\"exp\":9999999999}").getBytes());
        assertThat(jwt.verifyTenant(p[0] + "." + forgedPayload + "." + p[2])).isEmpty();
        assertThat(jwt.verifyTenant(null)).isEmpty();
        assertThat(jwt.verifyTenant("a.b")).isEmpty();
    }

    @Test
    void emptySecretRejectsEverything() {
        JwtService none = new JwtService("");
        assertThat(none.verifyTenant(jwt.issue(UUID.randomUUID(), "x", Duration.ofMinutes(1)))).isEmpty();
    }
}
