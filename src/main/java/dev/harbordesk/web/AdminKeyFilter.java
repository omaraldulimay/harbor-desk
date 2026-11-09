package dev.harbordesk.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** /admin is for the platform operator: a static key in X-Admin-Key. An empty configured key rejects everything. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AdminKeyFilter extends OncePerRequestFilter {
    private final byte[] key;

    public AdminKeyFilter(@Value("${harbor.admin.key:}") String key) {
        this.key = key.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/admin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String given = req.getHeader("X-Admin-Key");
        if (key.length == 0 || given == null || !MessageDigest.isEqual(key, given.getBytes(StandardCharsets.UTF_8))) {
            ApiError.write(res, 401, "Unauthorized", "admin key required");
            return;
        }
        chain.doFilter(req, res);
    }
}
