package dev.harbordesk.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tiny HTTP helper on the JDK client. */
public final class Api {
    public record Resp(int status, String body, String retryAfter) {
        public long idOf() {
            Matcher m = Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(body);
            if (!m.find()) {
                throw new IllegalStateException("no id in " + body);
            }
            return Long.parseLong(m.group(1));
        }

        public String string(String field) {
            Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"").matcher(body);
            if (!m.find()) {
                throw new IllegalStateException("no " + field + " in " + body);
            }
            return m.group(1);
        }
    }

    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final String base;

    public Api(String base) {
        this.base = base;
    }

    public HttpRequest.Builder request(String path, String token) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path));
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        return b;
    }

    public HttpRequest get(String path, String token) {
        return request(path, token).GET().build();
    }

    public HttpRequest json(String method, String path, String token, String body) {
        return request(path, token).header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
    }

    public Resp send(HttpRequest req) {
        try {
            return wrap(client.send(req, HttpResponse.BodyHandlers.ofString()));
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    public java.util.concurrent.CompletableFuture<Resp> sendAsync(HttpRequest req) {
        return client.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(Api::wrap);
    }

    public Resp getResp(String path, String token) {
        return send(get(path, token));
    }

    public Resp post(String path, String token, String body) {
        return send(json("POST", path, token, body));
    }

    public Resp patch(String path, String token, String body) {
        return send(json("PATCH", path, token, body));
    }

    public Resp admin(String path, String body, String key) {
        HttpRequest.Builder b = request(path, null).header("Content-Type", "application/json");
        if (key != null) {
            b.header("X-Admin-Key", key);
        }
        return send(b.POST(HttpRequest.BodyPublishers.ofString(body)).build());
    }

    private static Resp wrap(HttpResponse<String> r) {
        return new Resp(r.statusCode(), r.body(), r.headers().firstValue("Retry-After").orElse(null));
    }
}
