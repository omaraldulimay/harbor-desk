package dev.harbordesk.web;

import dev.harbordesk.security.JwtService;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Demo only: the app signs its own tokens. In production the identity provider does this, see ADR 0003 and 0005. */
@RestController
@ConditionalOnProperty("harbor.dev-tokens.enabled")
public class DevTokenController {
    private final JwtService jwt;

    public DevTokenController(JwtService jwt) {
        this.jwt = jwt;
    }

    @GetMapping("/dev/token")
    Map<String, String> token(@RequestParam UUID tenant, @RequestParam(defaultValue = "demo-user") String sub) {
        return Map.of("token", jwt.issue(tenant, sub, Duration.ofHours(1)));
    }
}
