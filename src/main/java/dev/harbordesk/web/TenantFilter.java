package dev.harbordesk.web;

import dev.harbordesk.domain.TenantContext;
import dev.harbordesk.domain.TenantInfo;
import dev.harbordesk.domain.TenantService;
import dev.harbordesk.ratelimit.RateLimiter;
import dev.harbordesk.security.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * For every /api request: verify the JWT, resolve the tenant, apply the tenant's rate limit and bind the tenant
 * to the thread. No valid tenant means 401, the request never reaches a controller.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TenantFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final TenantService tenants;
    private final RateLimiter limiter;

    public TenantFilter(JwtService jwt, TenantService tenants, RateLimiter limiter) {
        this.jwt = jwt;
        this.tenants = tenants;
        this.limiter = limiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : null;
        Optional<UUID> id = jwt.verifyTenant(token);
        Optional<TenantInfo> tenant = id.flatMap(tenants::resolve);
        if (tenant.isEmpty()) {
            res.setHeader("WWW-Authenticate", "Bearer");
            ApiError.write(res, 401, "Unauthorized", "missing or invalid token, or unknown tenant");
            return;
        }
        long retryAfter = limiter.retryAfterSeconds(tenant.get());
        if (retryAfter > 0) {
            res.setHeader("Retry-After", Long.toString(retryAfter));
            ApiError.write(res, 429, "Too Many Requests", "rate limit of the " + tenant.get().plan().id() + " plan exceeded");
            return;
        }
        TenantContext.set(tenant.get());
        try {
            chain.doFilter(req, res);
        } finally {
            TenantContext.clear();
        }
    }
}
