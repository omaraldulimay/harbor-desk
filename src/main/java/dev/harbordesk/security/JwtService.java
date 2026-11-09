package dev.harbordesk.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Minimal HS256 JWT for the demo profile. Claims: tenant (uuid), sub, exp.
 * In production an OIDC provider issues RS256 tokens and this class is replaced by a resource server, see ADR 0003 and 0005.
 */
@Component
public class JwtService {
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final String HEADER = B64.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
    private static final Pattern TENANT = Pattern.compile("\"tenant\"\\s*:\\s*\"([0-9a-fA-F-]{36})\"");
    private static final Pattern EXP = Pattern.compile("\"exp\"\\s*:\\s*(\\d+)");

    private final byte[] secret;

    public JwtService(@Value("${harbor.jwt.secret:}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String issue(UUID tenant, String subject, Duration ttl) {
        if (secret.length < 32) {
            throw new IllegalStateException("harbor.jwt.secret must have at least 32 characters");
        }
        String sub = subject.replaceAll("[^A-Za-z0-9@._-]", "");
        long exp = System.currentTimeMillis() / 1000 + ttl.toSeconds();
        String payload = "{\"tenant\":\"" + tenant + "\",\"sub\":\"" + sub + "\",\"exp\":" + exp + "}";
        String signingInput = HEADER + "." + B64.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return signingInput + "." + B64.encodeToString(hmac(signingInput));
    }

    /** Returns the tenant id of a valid, unexpired token, or empty. */
    public Optional<UUID> verifyTenant(String token) {
        if (secret.length < 32 || token == null) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3 || !HEADER.equals(parts[0])) {
            return Optional.empty();
        }
        try {
            byte[] expected = hmac(parts[0] + "." + parts[1]);
            byte[] given = Base64.getUrlDecoder().decode(parts[2]);
            if (!MessageDigest.isEqual(expected, given)) {
                return Optional.empty();
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            Matcher exp = EXP.matcher(payload);
            Matcher tenant = TENANT.matcher(payload);
            if (!exp.find() || !tenant.find() || Long.parseLong(exp.group(1)) < System.currentTimeMillis() / 1000) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(tenant.group(1)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private byte[] hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
