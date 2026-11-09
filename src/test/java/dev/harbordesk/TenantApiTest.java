package dev.harbordesk;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harbordesk.domain.Plan;
import dev.harbordesk.domain.TenantInfo;
import dev.harbordesk.support.Api;
import dev.harbordesk.support.HarborTestBase;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantApiTest extends HarborTestBase {

    @Test
    void requestsWithoutValidTenantAreRejected() {
        Api api = api();
        assertThat(api.getResp("/api/tickets", null).status()).isEqualTo(401);
        assertThat(api.getResp("/api/tickets", "garbage").status()).isEqualTo(401);

        TenantInfo t = newTenant("auth", Plan.PRO);
        String good = token(t);
        assertThat(api.getResp("/api/tickets", good).status()).isEqualTo(200);

        // tampered signature
        assertThat(api.getResp("/api/tickets", good.substring(0, good.length() - 2) + "xx").status()).isEqualTo(401);
        // valid signature, tenant that does not exist
        String unknown = jwt.issue(UUID.randomUUID(), "x", Duration.ofMinutes(1));
        assertThat(api.getResp("/api/tickets", unknown).status()).isEqualTo(401);
        // expired
        String expired = jwt.issue(t.id(), "x", Duration.ofSeconds(-5));
        assertThat(api.getResp("/api/tickets", expired).status()).isEqualTo(401);
    }

    @Test
    void ticketLifecycleAndCrossTenantAccess() {
        Api api = api();
        TenantInfo a = newTenant("flow-a", Plan.PRO);
        TenantInfo b = newTenant("flow-b", Plan.PRO);
        String ta = token(a);
        String tb = token(b);

        Api.Resp created = api.post("/api/tickets", ta, "{\"title\":\"Printer on fire\",\"body\":\"urgent\"}");
        assertThat(created.status()).isEqualTo(201);
        long id = created.idOf();

        assertThat(api.patch("/api/tickets/" + id + "/status", ta, "{\"status\":\"resolved\"}").status()).isEqualTo(200);
        assertThat(api.patch("/api/tickets/" + id + "/status", ta, "{\"status\":\"nope\"}").status()).isEqualTo(400);
        assertThat(api.post("/api/tickets/" + id + "/comments", ta, "{\"author\":\"ann\",\"body\":\"on it\"}").status()).isEqualTo(201);
        assertThat(api.getResp("/api/tickets/" + id + "/comments", ta).body()).contains("on it");
        assertThat(api.getResp("/api/tickets?status=resolved", ta).body()).contains("Printer on fire");

        // tenant B asks for A's ticket by id: not found, for read, write and comment
        assertThat(api.getResp("/api/tickets/" + id, tb).status()).isEqualTo(404);
        assertThat(api.patch("/api/tickets/" + id + "/status", tb, "{\"status\":\"closed\"}").status()).isEqualTo(404);
        assertThat(api.post("/api/tickets/" + id + "/comments", tb, "{\"author\":\"eve\",\"body\":\"hi\"}").status()).isEqualTo(404);
        assertThat(api.getResp("/api/tickets", tb).body()).doesNotContain("Printer on fire");
        assertThat(api.getResp("/api/tickets/" + id, ta).string("status")).isEqualTo("resolved");
    }

    @Test
    void freeTenantHitsTicketQuota() throws InterruptedException {
        Api api = api();
        TenantInfo t = newTenant("quota", Plan.FREE);
        String token = token(t);
        for (int i = 0; i < Plan.FREE.maxTickets(); i++) {
            assertThat(api.post("/api/tickets", token, "{\"title\":\"t" + i + "\"}").status()).isEqualTo(201);
            Thread.sleep(60);   // stay below the 20 req/s limit of the plan
        }
        assertThat(api.post("/api/tickets", token, "{\"title\":\"one too many\"}").status()).isEqualTo(429);
    }

    @Test
    void rateLimitAnswers429WithRetryAfter() {
        Api api = api();
        TenantInfo t = newTenant("rate", Plan.FREE);
        String token = token(t);
        List<Api.Resp> all = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            all.add(api.getResp("/api/tickets", token));
        }
        List<Api.Resp> limited = all.stream().filter(r -> r.status() == 429).toList();
        assertThat(limited).isNotEmpty();
        assertThat(limited).allSatisfy(r -> assertThat(Integer.parseInt(r.retryAfter())).isGreaterThanOrEqualTo(1));
        assertThat(all.stream().filter(r -> r.status() == 200).count()).isGreaterThanOrEqualTo(20);
        // another tenant is not affected
        TenantInfo other = newTenant("rate-other", Plan.FREE);
        assertThat(api.getResp("/api/tickets", token(other)).status()).isEqualTo(200);
    }

    @Test
    void onboardingNeedsAdminKeyAndRejectsDuplicates() {
        Api api = api();
        String slug = "onb-" + UUID.randomUUID().toString().substring(0, 8);
        String body = "{\"slug\":\"" + slug + "\",\"name\":\"Onboard Co\",\"plan\":\"pro\"}";
        assertThat(api.admin("/admin/tenants", body, null).status()).isEqualTo(401);
        assertThat(api.admin("/admin/tenants", body, "wrong").status()).isEqualTo(401);
        Api.Resp ok = api.admin("/admin/tenants", body, ADMIN_KEY);
        assertThat(ok.status()).isEqualTo(201);
        assertThat(ok.string("database")).isEqualTo("shared pool");
        assertThat(api.admin("/admin/tenants", body, ADMIN_KEY).status()).isEqualTo(409);
        assertThat(api.admin("/admin/tenants", "{\"slug\":\"Bad Slug\",\"name\":\"x\",\"plan\":\"pro\"}", ADMIN_KEY).status()).isEqualTo(400);
        assertThat(api.admin("/admin/tenants", "{\"slug\":\"" + slug + "x\",\"name\":\"x\",\"plan\":\"gold\"}", ADMIN_KEY).status()).isEqualTo(400);

        // the new tenant can get a token and work
        Api.Resp tok = api.getResp("/dev/token?tenant=" + ok.string("id"), null);
        assertThat(tok.status()).isEqualTo(200);
        assertThat(api.getResp("/api/tickets", tok.string("token")).status()).isEqualTo(200);
    }
}
