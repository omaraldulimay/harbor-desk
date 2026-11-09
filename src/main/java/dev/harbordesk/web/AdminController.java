package dev.harbordesk.web;

import dev.harbordesk.domain.Plan;
import dev.harbordesk.domain.TenantInfo;
import dev.harbordesk.domain.TenantService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/tenants")
public class AdminController {
    record CreateTenant(@NotBlank String slug, @NotBlank String name, @NotBlank String plan) {
    }

    record TenantResponse(UUID id, String slug, String plan, String database) {
    }

    private final TenantService tenants;

    public AdminController(TenantService tenants) {
        this.tenants = tenants;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TenantResponse create(@Valid @RequestBody CreateTenant req) {
        TenantInfo t = tenants.onboard(req.slug(), req.name(), Plan.parse(req.plan()));
        return new TenantResponse(t.id(), t.slug(), t.plan().id(), t.dbName() == null ? "shared pool" : t.dbName());
    }
}
