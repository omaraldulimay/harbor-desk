package dev.harbordesk.domain;

import dev.harbordesk.persistence.TenantDirectory;
import dev.harbordesk.persistence.TenantProvisioner;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Tenant lookup for the request path and onboarding for the admin API. */
@Service
public class TenantService {
    private static final Pattern SLUG = Pattern.compile("[a-z0-9][a-z0-9-]{1,28}[a-z0-9]");

    private final TenantDirectory directory;
    private final TenantProvisioner provisioner;

    public TenantService(TenantDirectory directory, TenantProvisioner provisioner) {
        this.directory = directory;
        this.provisioner = provisioner;
    }

    public Optional<TenantInfo> resolve(UUID id) {
        return directory.find(id);
    }

    /** Creates the tenant. Enterprise tenants also get their own database, created and migrated here. */
    public TenantInfo onboard(String slug, String name, Plan plan) {
        if (slug == null || !SLUG.matcher(slug).matches()) {
            throw new IllegalArgumentException("slug must be 3 to 30 characters of a-z, 0-9 and -");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        return provisioner.provision(slug, name.trim(), plan);
    }
}
