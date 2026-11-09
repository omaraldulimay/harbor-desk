package dev.harbordesk.web;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Problem+json for errors raised in filters, where the controller advice does not apply. */
final class ApiError {
    private ApiError() {
    }

    static void write(HttpServletResponse res, int status, String title, String detail) throws IOException {
        res.setStatus(status);
        res.setContentType("application/problem+json");
        res.getWriter().write("{\"status\":" + status + ",\"title\":\"" + title + "\",\"detail\":\"" + detail + "\"}");
    }
}
